package com.hospital.crm.his.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerIdentityService;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.application.HisPatientSyncPort;
import com.hospital.crm.his.api.HisDtos;
import com.hospital.crm.his.support.HisPatientQuery;
import com.hospital.crm.his.support.HisProperties;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HIS 患者全量同步：让 CRM 客户集合覆盖 HIS 患者集合。
 * 分页调用 HisPatientQuery.fetchPatientRows（真实模式查同步视图，模拟模式返回内置患者）；
 * 匹配顺序：已绑定主索引 → 手机号 → 证件号 → 姓名+出生日期 → 新建建档；
 * 匹配或新建后均绑定 his_patient_id 主索引、补齐缺失的性别/生日并记录 his_synced_at。
 * 幂等：同一 HIS 患者重复执行只会命中"已绑定"分支被跳过，不产生重复客户。
 */
@Service
public class HisPatientSyncService implements HisPatientSyncPort {

    private static final String RESOURCE_TYPE = "HIS_PATIENT";
    private static final String IDENTITY_TYPE = "his_patient_id";
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 单次同步最多处理的行数上限，防止真实视图异常膨胀时拖垮主库事务。 */
    private static final int MAX_TOTAL_ROWS = 20000;

    private final HisProperties properties;
    private final HisPatientQuery patientQuery;
    private final HisSyncLogService syncLogService;
    private final CustomerService customerService;
    private final CustomerIdentityService customerIdentityService;
    private final AuditService auditService;

    public HisPatientSyncService(HisProperties properties,
                                 HisPatientQuery patientQuery,
                                 HisSyncLogService syncLogService,
                                 CustomerService customerService,
                                 CustomerIdentityService customerIdentityService,
                                 AuditService auditService) {
        this.properties = properties;
        this.patientQuery = patientQuery;
        this.syncLogService = syncLogService;
        this.customerService = customerService;
        this.customerIdentityService = customerIdentityService;
        this.auditService = auditService;
    }

    /**
     * HIS 端点入口（/api/v1/his/patients/sync），响应结构保持不变。
     */
    @Transactional
    public HisDtos.SyncResult syncAll() {
        SyncCounters counters = doSync();
        return new HisDtos.SyncResult(counters.total, counters.created,
                counters.linked, counters.updated, counters.skipped);
    }

    /**
     * 客户中心端点入口（/api/v1/customers/sync-his），经 HisPatientSyncPort 调用。
     */
    @Transactional
    @Override
    public CustomerDtos.HisSyncResult syncFromHis() {
        SyncCounters counters = doSync();
        return new CustomerDtos.HisSyncResult(counters.total, counters.created,
                counters.linked, counters.updated, counters.skipped);
    }

    private SyncCounters doSync() {
        if (!properties.getSync().isEnabled()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "HIS 患者同步开关未启用（crm.his.sync.enabled=false）");
        }
        Instant startedAt = Instant.now();
        boolean live = properties.configured();
        int pageSize = Math.min(Math.max(properties.getSync().getPageSize(), 1), 1000);

        SyncCounters counters = new SyncCounters();
        int offset = 0;
        while (offset < MAX_TOTAL_ROWS) {
            HisPatientQuery.PatientRowPage page = patientQuery.fetchPatientRows(offset, pageSize);
            page.items().forEach(patient -> syncOne(patient, counters));
            counters.total += page.items().size();
            offset += pageSize;
            if (!page.hasMore() || page.items().isEmpty()) {
                break;
            }
        }

        Instant finishedAt = Instant.now();
        syncLogService.record("PATIENT_SYNC", null, STATUS_SUCCESS, null, null, startedAt, finishedAt);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mode", live ? "LIVE" : "DEMO");
        detail.put("total", counters.total);
        detail.put("created", counters.created);
        detail.put("linked", counters.linked);
        detail.put("updated", counters.updated);
        detail.put("skipped", counters.skipped);
        auditService.record("HIS_PATIENT_SYNC", RESOURCE_TYPE, null, detail);
        return counters;
    }

    private void syncOne(HisPatientQuery.PatientRow patient, SyncCounters counters) {
        String patientId = patient.externalPatientId();
        if (patientId == null || patientId.isBlank()
                || patient.name() == null || patient.name().isBlank()) {
            counters.skipped++;
            return;
        }
        // a. 已绑定主索引 → 视为已同步，仅刷新同步时间
        Optional<UUID> bound = customerIdentityService.findCustomerId(IDENTITY_TYPE, patientId);
        if (bound.isPresent()) {
            customerService.markHisSynced(bound.get());
            counters.skipped++;
            return;
        }
        String gender = normalizeGender(patient.gender());
        // b. 手机号完全匹配（走检索哈希，不接触密文存储）
        Optional<CustomerDtos.CustomerBrief> match = customerService.matchByPhone(patient.mobile());
        // c. 证件号匹配
        if (match.isEmpty()) {
            match = customerService.matchByIdNo(patient.idNo());
        }
        // d. 姓名 + 出生日期匹配
        if (match.isEmpty()) {
            match = customerService.matchByNameAndBirthDate(patient.name(), patient.birthDate());
        }

        UUID customerId;
        if (match.isPresent()) {
            customerId = match.get().id();
            counters.linked++;
            if (customerService.fillMissingProfile(customerId, gender, patient.birthDate())) {
                counters.updated++;
            }
        } else {
            // e. 无匹配 → 新建建档（手机号/生日与 HIS 保持一致，强制创建避免与停用档案冲突）
            CustomerDtos.CustomerSummary summary = customerService.create(new CustomerDtos.CreateRequest(
                    patient.name().trim(),
                    gender,
                    patient.birthDate(),
                    patient.mobile(),
                    null,
                    "HIS",
                    "HIS 同步自动建档",
                    null,
                    true));
            customerId = summary.id();
            counters.created++;
        }

        customerIdentityService.link(customerId, properties.getSourceSystem(),
                IDENTITY_TYPE, patientId, true);
        customerService.markHisSynced(customerId);
    }

    private String normalizeGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "m", "male", "1", "男" -> "male";
            case "f", "female", "2", "女" -> "female";
            default -> "unknown";
        };
    }

    private static final class SyncCounters {
        private int total;
        private int created;
        private int linked;
        private int updated;
        private int skipped;
    }
}
