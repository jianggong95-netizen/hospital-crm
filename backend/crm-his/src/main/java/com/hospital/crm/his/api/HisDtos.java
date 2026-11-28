package com.hospital.crm.his.api;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HisDtos {

    private HisDtos() {
    }

    public record PatientCandidate(
            String externalPatientId,
            String mrn,
            String nameMasked,
            String gender,
            LocalDate birthDate,
            String mobileMasked,
            String matchType,
            BigDecimal matchConfidence,
            boolean requiresManualVerification,
            Instant fetchedAt
    ) {
    }

    public record PatientSearchResult(
            List<PatientCandidate> candidates,
            boolean requiresManualVerification,
            String sourceSystem,
            Instant fetchedAt,
            int limit
    ) {
    }

    public record VisitItem(
            String visitId,
            Instant visitTime,
            String department,
            String doctor,
            String diagnosis
    ) {
    }

    public record AdmissionItem(
            String admissionId,
            Instant admittedAt,
            Instant dischargedAt,
            String ward,
            String diagnosis
    ) {
    }

    public record Patient360(
            String externalPatientId,
            String mrn,
            String nameMasked,
            String gender,
            LocalDate birthDate,
            String mobileMasked,
            List<VisitItem> visits,
            List<AdmissionItem> admissions,
            String sourceSystem,
            Instant fetchedAt,
            Instant validUntil,
            boolean stale,
            String dataStatus,
            BigDecimal matchConfidence,
            boolean requiresManualVerification
    ) {
    }

    public record LinkRequest(
            @NotNull(message = "客户ID不能为空")
            UUID customerId,

            @NotNull(message = "请确认已完成人工核验")
            Boolean verified
    ) {
    }

    public record LinkResponse(
            UUID identityId,
            UUID customerId,
            String externalPatientId,
            String sourceSystem,
            String identityType,
            boolean verified,
            Instant linkedAt
    ) {
    }

    public record SyncLogItem(
            UUID id,
            String sourceSystem,
            String operation,
            String traceId,
            String externalPatientId,
            String status,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs
    ) {
    }

    /**
     * HIS 患者全量同步结果：total 本次处理的 HIS 患者数；created 新建客户数；
     * linked 匹配到现有客户并绑定主索引数；updated 补齐性别/生日数；skipped 已绑定跳过数。
     */
    public record SyncResult(
            int total,
            int created,
            int linked,
            int updated,
            int skipped
    ) {
    }

    /**
     * HIS 患者全量分页拉取的固定契约：items 为本页脱敏候选，
     * hasMore 表示仍有后续分页，total 为视图内患者总数。
     */
    public record PatientPage(
            List<PatientCandidate> items,
            boolean hasMore,
            int total
    ) {
    }
}
