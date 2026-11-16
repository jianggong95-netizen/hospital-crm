package com.hospital.crm.his.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.api.CustomerIdentityDtos;
import com.hospital.crm.customer.application.CustomerIdentityService;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.his.api.HisDtos;
import com.hospital.crm.his.domain.HisPatientSnapshot;
import com.hospital.crm.his.domain.HisPatientSnapshotRepository;
import com.hospital.crm.his.support.HisMasking;
import com.hospital.crm.his.support.HisPatientQuery;
import com.hospital.crm.his.support.HisProperties;
import com.hospital.crm.system.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HIS 患者服务：候选查询、360 摘要、人工核验后绑定与降级快照。
 * 任何响应都不包含证件号明文；检索关键字不写入日志与审计。
 */
@Service
public class HisPatientService {

    private static final Logger log = LoggerFactory.getLogger(HisPatientService.class);
    private static final String RESOURCE_TYPE = "HIS_PATIENT";
    private static final String IDENTITY_TYPE = "his_patient_id";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String DATA_LIVE = "LIVE";
    private static final String DATA_STALE = "STALE";
    private static final BigDecimal EXACT_CONFIDENCE = new BigDecimal("0.9900");
    private static final BigDecimal FUZZY_CONFIDENCE = new BigDecimal("0.6000");

    private final HisProperties properties;
    private final HisPatientQuery patientQuery;
    private final HisSyncLogService syncLogService;
    private final HisPatientSnapshotRepository snapshotRepository;
    private final CustomerService customerService;
    private final CustomerIdentityService customerIdentityService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public HisPatientService(HisProperties properties,
                             HisPatientQuery patientQuery,
                             HisSyncLogService syncLogService,
                             HisPatientSnapshotRepository snapshotRepository,
                             CustomerService customerService,
                             CustomerIdentityService customerIdentityService,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.patientQuery = patientQuery;
        this.syncLogService = syncLogService;
        this.snapshotRepository = snapshotRepository;
        this.customerService = customerService;
        this.customerIdentityService = customerIdentityService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public HisDtos.PatientSearchResult search(String keyword, String identityType) {
        String trimmedKeyword = requireKeyword(keyword);
        Instant startedAt = Instant.now();
        try {
            HisPatientQuery.SearchResult result = patientQuery.search(identityType, trimmedKeyword);
            Instant fetchedAt = Instant.now();
            BigDecimal confidence = "any".equals(result.matchType()) ? FUZZY_CONFIDENCE : EXACT_CONFIDENCE;
            List<HisDtos.PatientCandidate> candidates = result.patients().stream()
                    .map(patient -> new HisDtos.PatientCandidate(
                            patient.externalPatientId(),
                            patient.mrn(),
                            HisMasking.maskName(patient.name()),
                            patient.gender(),
                            patient.birthDate(),
                            HisMasking.maskPhone(patient.mobile()),
                            result.matchType(),
                            confidence,
                            true,
                            fetchedAt))
                    .toList();

            syncLogService.record("PATIENT_SEARCH", null, STATUS_SUCCESS, null, null, startedAt, fetchedAt);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("matchType", result.matchType());
            detail.put("candidateCount", candidates.size());
            auditService.record("HIS_PATIENT_SEARCH", RESOURCE_TYPE, null, detail);

            return new HisDtos.PatientSearchResult(
                    candidates, true, properties.getSourceSystem(), fetchedAt,
                    properties.getSearchLimit());
        } catch (RuntimeException ex) {
            syncLogService.record("PATIENT_SEARCH", null, STATUS_FAILED,
                    errorCode(ex), errorMessage(ex), startedAt, Instant.now());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("identityType", identityType);
            detail.put("errorCode", errorCode(ex));
            auditService.recordNow("HIS_PATIENT_SEARCH_FAILED", RESOURCE_TYPE, null, detail);
            throw ex;
        }
    }

    public HisDtos.Patient360 patient360(String externalPatientId) {
        String patientId = requireExternalId(externalPatientId);
        Instant startedAt = Instant.now();
        try {
            HisPatientQuery.Patient360Raw raw = patientQuery.load360(patientId);
            Instant fetchedAt = Instant.now();
            Instant validUntil = fetchedAt.plus(properties.getSnapshotValidMinutes(), ChronoUnit.MINUTES);
            HisDtos.Patient360 detail = toPatient360(raw, fetchedAt, validUntil, DATA_LIVE);
            saveSnapshot(detail);

            syncLogService.record("PATIENT_360", patientId, STATUS_SUCCESS, null, null, startedAt, fetchedAt);
            auditService.record("HIS_PATIENT_360_VIEW", RESOURCE_TYPE, patientId, viewDetail(detail));
            return detail;
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException businessException
                    && businessException.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                syncLogService.record("PATIENT_360", patientId, STATUS_FAILED,
                        errorCode(ex), errorMessage(ex), startedAt, Instant.now());
                auditService.recordNow("HIS_PATIENT_360_FAILED", RESOURCE_TYPE, patientId,
                        failureDetail(errorCode(ex)));
                throw ex;
            }

            Optional<HisPatientSnapshot> cached = snapshotRepository
                    .findBySourceSystemAndExternalPatientId(properties.getSourceSystem(), patientId);
            if (cached.isPresent() && isUsable(cached.get())) {
                HisDtos.Patient360 stale = fromSnapshot(cached.get());
                syncLogService.record("PATIENT_360", patientId, STATUS_DEGRADED,
                        errorCode(ex), errorMessage(ex), startedAt, Instant.now());
                Map<String, Object> detail = viewDetail(stale);
                detail.put("degradedReason", errorCode(ex));
                auditService.recordNow("HIS_PATIENT_360_STALE", RESOURCE_TYPE, patientId, detail);
                return stale;
            }

            syncLogService.record("PATIENT_360", patientId, STATUS_FAILED,
                    errorCode(ex), errorMessage(ex), startedAt, Instant.now());
            auditService.recordNow("HIS_PATIENT_360_FAILED", RESOURCE_TYPE, patientId,
                    failureDetail(errorCode(ex)));
            throw ex;
        }
    }

    public HisDtos.LinkResponse link(String externalPatientId, HisDtos.LinkRequest request) {
        String patientId = requireExternalId(externalPatientId);
        if (!Boolean.TRUE.equals(request.verified())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "HIS 患者必须完成人工核验后才能绑定到 CRM 客户");
        }
        CustomerDtos.CustomerBrief customer = customerService.brief(request.customerId());
        if (!"active".equals(customer.status())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "客户档案已停用，不能绑定 HIS 患者");
        }

        Instant startedAt = Instant.now();
        try {
            patientQuery.findPatient(patientId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "HIS 中未找到该患者主索引记录"));
            CustomerIdentityDtos.LinkedIdentity linked = customerIdentityService.link(
                    customer.id(), properties.getSourceSystem(), IDENTITY_TYPE, patientId, true);

            Instant finishedAt = Instant.now();
            syncLogService.record("PATIENT_LINK", patientId, STATUS_SUCCESS, null, null, startedAt, finishedAt);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("customerNo", customer.customerNo());
            detail.put("identityId", linked.identityId().toString());
            auditService.record("HIS_PATIENT_LINK", RESOURCE_TYPE, patientId, detail);

            return new HisDtos.LinkResponse(
                    linked.identityId(),
                    linked.customerId(),
                    patientId,
                    linked.sourceSystem(),
                    linked.identityType(),
                    linked.verified(),
                    linked.createdAt());
        } catch (RuntimeException ex) {
            syncLogService.record("PATIENT_LINK", patientId, STATUS_FAILED,
                    errorCode(ex), errorMessage(ex), startedAt, Instant.now());
            auditService.recordNow("HIS_PATIENT_LINK_FAILED", RESOURCE_TYPE, patientId,
                    failureDetail(errorCode(ex)));
            throw ex;
        }
    }

    private HisDtos.Patient360 toPatient360(HisPatientQuery.Patient360Raw raw,
                                            Instant fetchedAt,
                                            Instant validUntil,
                                            String dataStatus) {
        HisPatientQuery.PatientRow patient = raw.patient();
        List<HisDtos.VisitItem> visits = raw.visits().stream()
                .map(visit -> new HisDtos.VisitItem(
                        visit.visitId(), visit.visitTime(), visit.department(), visit.doctor(), visit.diagnosis()))
                .toList();
        List<HisDtos.AdmissionItem> admissions = raw.admissions().stream()
                .map(admission -> new HisDtos.AdmissionItem(
                        admission.admissionId(), admission.admittedAt(), admission.dischargedAt(),
                        admission.ward(), admission.diagnosis()))
                .toList();
        return new HisDtos.Patient360(
                patient.externalPatientId(),
                patient.mrn(),
                HisMasking.maskName(patient.name()),
                patient.gender(),
                patient.birthDate(),
                HisMasking.maskPhone(patient.mobile()),
                visits,
                admissions,
                properties.getSourceSystem(),
                fetchedAt,
                validUntil,
                false,
                dataStatus,
                EXACT_CONFIDENCE,
                true);
    }

    private void saveSnapshot(HisDtos.Patient360 detail) {
        try {
            HisPatientSnapshot entity = snapshotRepository
                    .findBySourceSystemAndExternalPatientId(
                            properties.getSourceSystem(), detail.externalPatientId())
                    .orElseGet(HisPatientSnapshot::new);
            entity.setSourceSystem(properties.getSourceSystem());
            entity.setExternalPatientId(detail.externalPatientId());
            entity.setSnapshot(objectMapper.convertValue(detail, new TypeReference<Map<String, Object>>() {
            }));
            entity.setFetchedAt(detail.fetchedAt());
            entity.setValidUntil(detail.validUntil());
            entity.setMatchConfidence(detail.matchConfidence());
            customerIdentityService.find(properties.getSourceSystem(), IDENTITY_TYPE, detail.externalPatientId())
                    .ifPresent(identity -> entity.setCustomerId(identity.customerId()));
            snapshotRepository.save(entity);
        } catch (RuntimeException ex) {
            log.warn("HIS 患者快照保存失败，不影响实时查询结果：{}", ex.getMessage());
        }
    }

    private boolean isUsable(HisPatientSnapshot snapshot) {
        if (snapshot.getFetchedAt() == null) {
            return false;
        }
        Instant threshold = Instant.now().minus(properties.getSnapshotMaxStaleMinutes(), ChronoUnit.MINUTES);
        return snapshot.getFetchedAt().isAfter(threshold);
    }

    private HisDtos.Patient360 fromSnapshot(HisPatientSnapshot snapshot) {
        HisDtos.Patient360 stored = objectMapper.convertValue(snapshot.getSnapshot(), HisDtos.Patient360.class);
        return new HisDtos.Patient360(
                stored.externalPatientId(),
                stored.mrn(),
                stored.nameMasked(),
                stored.gender(),
                stored.birthDate(),
                stored.mobileMasked(),
                stored.visits(),
                stored.admissions(),
                properties.getSourceSystem(),
                snapshot.getFetchedAt(),
                snapshot.getValidUntil(),
                true,
                DATA_STALE,
                stored.matchConfidence(),
                true);
    }

    private Map<String, Object> viewDetail(HisDtos.Patient360 detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dataStatus", detail.dataStatus());
        map.put("visitCount", detail.visits() == null ? 0 : detail.visits().size());
        map.put("admissionCount", detail.admissions() == null ? 0 : detail.admissions().size());
        map.put("fetchedAt", detail.fetchedAt() == null ? null : detail.fetchedAt().toString());
        return map;
    }

    private Map<String, Object> failureDetail(String errorCode) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("errorCode", errorCode);
        return detail;
    }

    private String requireKeyword(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "查询关键字长度需在 2 到 100 个字符之间");
        }
        return trimmed;
    }

    private String requireExternalId(String externalPatientId) {
        String trimmed = externalPatientId == null ? "" : externalPatientId.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "HIS 患者标识不能为空且长度不能超过 100");
        }
        return trimmed;
    }

    private String errorCode(RuntimeException ex) {
        return ex instanceof BusinessException businessException
                ? businessException.getErrorCode().code()
                : ErrorCode.INTERNAL_ERROR.code();
    }

    private String errorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
