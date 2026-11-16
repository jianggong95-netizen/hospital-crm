package com.hospital.crm.patient360.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.api.CustomerIdentityDtos;
import com.hospital.crm.customer.application.CustomerIdentityService;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.family.api.FamilyDtos;
import com.hospital.crm.family.application.FamilyService;
import com.hospital.crm.his.api.HisDtos;
import com.hospital.crm.his.application.HisPatientService;
import com.hospital.crm.his.support.HisProperties;
import com.hospital.crm.patient360.api.Patient360Dtos;
import com.hospital.crm.patient360.support.Patient360Cache;
import com.hospital.crm.patient360.support.Patient360Properties;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 患者360聚合服务：以 CRM 客户为主键，聚合标签、家庭归属与 HIS 诊疗摘要。
 *
 * <p>权限边界：分组视图基础权限为 crm:patient360:read；
 * 敏感字段继续由 crm:customer:sensitive:read 控制，家庭分区需 crm:family:read，
 * HIS 诊疗分区需 his:patient:read。无权限分区返回 RESTRICTED，不返回任何业务数据。</p>
 *
 * <p>降级边界：HIS 不可用时返回 UNAVAILABLE 或 CRM-005 已标记的 STALE 快照，
 * 并在 dataSources 中明确来源、获取时间与有效期，不补齐、不推测、不伪造诊疗数据。</p>
 */
@Service
public class Patient360Service {

    private static final String RESOURCE_TYPE = "CUSTOMER";
    private static final String SOURCE_CRM = "CRM";
    private static final String IDENTITY_TYPE = "his_patient_id";
    private static final String PERMISSION_FAMILY = "crm:family:read";
    private static final String PERMISSION_HIS = "his:patient:read";

    private static final String STATUS_LIVE = "LIVE";
    private static final String STATUS_STALE = "STALE";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String STATUS_NOT_LINKED = "NOT_LINKED";
    private static final String STATUS_RESTRICTED = "RESTRICTED";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";
    private static final String STATUS_NOT_REQUESTED = "NOT_REQUESTED";

    private static final Set<String> CONTRACT_SECTIONS = Set.of(
            "visits", "admissions", "appointments", "serviceOrders", "followups", "memberships", "complaints");
    private static final Set<String> DELIVERED_SECTIONS = Set.of("visits", "admissions");

    private final CustomerService customerService;
    private final FamilyService familyService;
    private final CustomerIdentityService customerIdentityService;
    private final HisPatientService hisPatientService;
    private final HisProperties hisProperties;
    private final Patient360Cache cache;
    private final Patient360Properties properties;
    private final AuditService auditService;

    public Patient360Service(CustomerService customerService,
                             FamilyService familyService,
                             CustomerIdentityService customerIdentityService,
                             HisPatientService hisPatientService,
                             HisProperties hisProperties,
                             Patient360Cache cache,
                             Patient360Properties properties,
                             AuditService auditService) {
        this.customerService = customerService;
        this.familyService = familyService;
        this.customerIdentityService = customerIdentityService;
        this.hisPatientService = hisPatientService;
        this.hisProperties = hisProperties;
        this.cache = cache;
        this.properties = properties;
        this.auditService = auditService;
    }

    public Patient360Dtos.Patient360View view(UUID customerId,
                                             String include,
                                             int visitPage,
                                             int visitPageSize,
                                             int admissionPage,
                                             int admissionPageSize,
                                             boolean refresh) {
        CustomerDtos.CustomerDetail customer = customerService.detail(customerId);
        SectionSelection selection = SectionSelection.parse(include);

        List<Patient360Dtos.DataSource> dataSources = new ArrayList<>();
        Instant assembledAt = Instant.now();
        dataSources.add(new Patient360Dtos.DataSource(
                "profile", SOURCE_CRM, STATUS_LIVE, assembledAt, null, null, "CRM 客户主数据与标签"));

        Patient360Dtos.FamilySection family = familySection(customerId, dataSources, assembledAt);
        Patient360Dtos.HisSection his = hisSection(customerId, selection,
                visitPage, visitPageSize, admissionPage, admissionPageSize, refresh, dataSources);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("customerNo", customer.customerNo());
        detail.put("sensitiveVisible", customer.sensitiveVisible());
        detail.put("familyStatus", family.dataStatus());
        detail.put("hisStatus", his.dataStatus());
        detail.put("hisCached", his.cached());
        if (!selection.unavailable().isEmpty()) {
            detail.put("unavailableSections", selection.unavailable());
        }
        auditService.record("PATIENT360_VIEW", RESOURCE_TYPE, customerId.toString(), detail);

        return new Patient360Dtos.Patient360View(
                customer,
                customer.sensitiveVisible(),
                family,
                his,
                List.copyOf(dataSources),
                selection.unavailable());
    }

    private Patient360Dtos.FamilySection familySection(UUID customerId,
                                                       List<Patient360Dtos.DataSource> dataSources,
                                                       Instant assembledAt) {
        if (!CurrentUser.hasPermission(PERMISSION_FAMILY)) {
            String message = "当前角色无家庭账户查看权限";
            dataSources.add(new Patient360Dtos.DataSource(
                    "family", SOURCE_CRM, STATUS_RESTRICTED, assembledAt, null, null, message));
            return new Patient360Dtos.FamilySection(STATUS_RESTRICTED, message, null);
        }

        Optional<FamilyDtos.FamilyMembership> membership = familyService.membershipOf(customerId);
        if (membership.isEmpty()) {
            String message = "该客户当前未加入任何家庭账户";
            dataSources.add(new Patient360Dtos.DataSource(
                    "family", SOURCE_CRM, STATUS_NOT_FOUND, assembledAt, null, null, message));
            return new Patient360Dtos.FamilySection(STATUS_NOT_FOUND, message, null);
        }

        dataSources.add(new Patient360Dtos.DataSource(
                "family", SOURCE_CRM, STATUS_LIVE, assembledAt, null, null, "CRM 家庭账户与成员关系"));
        return new Patient360Dtos.FamilySection(STATUS_LIVE, null, membership.get());
    }

    private Patient360Dtos.HisSection hisSection(UUID customerId,
                                                 SectionSelection selection,
                                                 int visitPage,
                                                 int visitPageSize,
                                                 int admissionPage,
                                                 int admissionPageSize,
                                                 boolean refresh,
                                                 List<Patient360Dtos.DataSource> dataSources) {
        String sourceSystem = hisProperties.getSourceSystem();
        Instant assembledAt = Instant.now();

        if (!selection.visits() && !selection.admissions()) {
            return emptyHisSection(sourceSystem, STATUS_NOT_REQUESTED, "本次请求未包含 HIS 诊疗分区");
        }
        if (!CurrentUser.hasPermission(PERMISSION_HIS)) {
            String message = "当前角色无 HIS 患者数据查看权限";
            dataSources.add(new Patient360Dtos.DataSource(
                    "his", sourceSystem, STATUS_RESTRICTED, assembledAt, null, null, message));
            return emptyHisSection(sourceSystem, STATUS_RESTRICTED, message);
        }

        Optional<CustomerIdentityDtos.ResolvedIdentity> identity = customerIdentityService
                .resolve(customerId, sourceSystem, IDENTITY_TYPE);
        if (identity.isEmpty()) {
            String message = "该客户尚未绑定 HIS 患者主索引，暂无可展示的诊疗数据";
            dataSources.add(new Patient360Dtos.DataSource(
                    "his", sourceSystem, STATUS_NOT_LINKED, assembledAt, null, null, message));
            return emptyHisSection(sourceSystem, STATUS_NOT_LINKED, message);
        }

        String externalPatientId = identity.get().identityValue();
        String maskedId = maskIdentifier(externalPatientId);
        String cacheKey = sourceSystem + "|" + externalPatientId;

        HisDtos.Patient360 data = null;
        boolean cached = false;
        if (!refresh) {
            Optional<HisDtos.Patient360> hit = cache.get(cacheKey);
            if (hit.isPresent()) {
                data = hit.get();
                cached = true;
            }
        }
        if (data == null) {
            try {
                data = hisPatientService.patient360(externalPatientId);
                cache.put(cacheKey, data);
            } catch (BusinessException ex) {
                String message = safeMessage(ex, "HIS 数据暂不可用");
                dataSources.add(new Patient360Dtos.DataSource(
                        "his", sourceSystem, STATUS_UNAVAILABLE, assembledAt, null, null, message));
                return new Patient360Dtos.HisSection(
                        STATUS_UNAVAILABLE, message, sourceSystem, maskedId,
                        null, null, null, null, null, null, null, false, false, null, false,
                        null, null, ex.getErrorCode().code());
            } catch (RuntimeException ex) {
                String message = "HIS 数据暂不可用，未展示任何推测数据";
                dataSources.add(new Patient360Dtos.DataSource(
                        "his", sourceSystem, STATUS_UNAVAILABLE, assembledAt, null, null, message));
                return new Patient360Dtos.HisSection(
                        STATUS_UNAVAILABLE, message, sourceSystem, maskedId,
                        null, null, null, null, null, null, null, false, false, null, false,
                        null, null, ErrorCode.INTERNAL_ERROR.code());
            }
        }

        String message = STATUS_STALE.equals(data.dataStatus())
                ? "HIS 暂不可用，正在展示有效期内缓存快照（数据已标记过期）"
                : "HIS 实时只读数据";
        dataSources.add(new Patient360Dtos.DataSource(
                "his", sourceSystem, data.dataStatus(), data.fetchedAt(), data.validUntil(), cached, message));

        return new Patient360Dtos.HisSection(
                data.dataStatus(),
                message,
                sourceSystem,
                maskedId,
                data.nameMasked(),
                data.mrn(),
                data.gender(),
                data.birthDate(),
                data.mobileMasked(),
                data.fetchedAt(),
                data.validUntil(),
                data.stale(),
                cached,
                data.matchConfidence(),
                data.requiresManualVerification(),
                selection.visits() ? page(visitItems(data.visits()), visitPage, visitPageSize) : null,
                selection.admissions() ? page(admissionItems(data.admissions()), admissionPage, admissionPageSize) : null,
                null);
    }

    private List<Patient360Dtos.VisitItem> visitItems(List<HisDtos.VisitItem> visits) {
        if (visits == null) {
            return List.of();
        }
        return visits.stream()
                .map(visit -> new Patient360Dtos.VisitItem(
                        visit.visitId(), visit.visitTime(), visit.department(), visit.doctor(), visit.diagnosis()))
                .toList();
    }

    private List<Patient360Dtos.AdmissionItem> admissionItems(List<HisDtos.AdmissionItem> admissions) {
        if (admissions == null) {
            return List.of();
        }
        return admissions.stream()
                .map(admission -> new Patient360Dtos.AdmissionItem(
                        admission.admissionId(), admission.admittedAt(), admission.dischargedAt(),
                        admission.ward(), admission.diagnosis()))
                .toList();
    }

    private Patient360Dtos.HisSection emptyHisSection(String sourceSystem, String status, String message) {
        return new Patient360Dtos.HisSection(
                status, message, sourceSystem, null,
                null, null, null, null, null, null, null, false, false, null, false,
                null, null, null);
    }

    private <T> PageResponse<T> page(List<T> items, int page, int pageSize) {
        List<T> source = items == null ? List.of() : items;
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? Math.max(properties.getDefaultPageSize(), 1)
                : Math.min(pageSize, Math.max(properties.getMaxPageSize(), 1));
        int from = Math.min((safePage - 1) * safePageSize, source.size());
        int to = Math.min(from + safePageSize, source.size());
        return PageResponse.of(List.copyOf(source.subList(from, to)), source.size(), safePage, safePageSize);
    }

    private String maskIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private String safeMessage(RuntimeException ex, String fallback) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }

    private record SectionSelection(boolean visits, boolean admissions, List<String> unavailable) {

        static SectionSelection parse(String include) {
            if (include == null || include.isBlank()) {
                return new SectionSelection(true, true, List.of());
            }
            Set<String> requested = new LinkedHashSet<>();
            Arrays.stream(include.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(requested::add);
            if (requested.isEmpty()) {
                return new SectionSelection(true, true, List.of());
            }
            List<String> invalid = requested.stream()
                    .filter(section -> !CONTRACT_SECTIONS.contains(section))
                    .sorted()
                    .toList();
            if (!invalid.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "include 包含不支持的聚合分区：" + String.join(",", invalid));
            }
            List<String> unavailable = requested.stream()
                    .filter(section -> !DELIVERED_SECTIONS.contains(section))
                    .sorted()
                    .toList();
            return new SectionSelection(
                    requested.contains("visits"),
                    requested.contains("admissions"),
                    unavailable);
        }
    }
}
