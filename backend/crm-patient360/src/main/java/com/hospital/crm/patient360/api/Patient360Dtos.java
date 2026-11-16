package com.hospital.crm.patient360.api;

import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.family.api.FamilyDtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 患者360聚合视图。每个分区均携带数据来源、状态与更新时间；
 * HIS 不可用时只返回 UNAVAILABLE 或 STALE 标记，不伪造诊疗数据。
 */
public final class Patient360Dtos {

    private Patient360Dtos() {
    }

    public record Patient360View(
            CustomerDtos.CustomerDetail customer,
            boolean sensitiveVisible,
            FamilySection family,
            HisSection his,
            List<DataSource> dataSources,
            List<String> unavailableSections
    ) {
    }

    public record FamilySection(
            String dataStatus,
            String message,
            FamilyDtos.FamilyMembership family
    ) {
    }

    public record HisSection(
            String dataStatus,
            String message,
            String sourceSystem,
            String externalPatientIdMasked,
            String patientNameMasked,
            String mrn,
            String gender,
            LocalDate birthDate,
            String mobileMasked,
            Instant fetchedAt,
            Instant validUntil,
            boolean stale,
            boolean cached,
            BigDecimal matchConfidence,
            boolean requiresManualVerification,
            PageResponse<VisitItem> visits,
            PageResponse<AdmissionItem> admissions,
            String errorCode
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

    /**
     * 数据来源与更新时间。section 取值 profile / family / his；
     * dataStatus 取值 LIVE / STALE / UNAVAILABLE / NOT_LINKED / RESTRICTED / NOT_FOUND / NOT_REQUESTED。
     */
    public record DataSource(
            String section,
            String sourceSystem,
            String dataStatus,
            Instant fetchedAt,
            Instant validUntil,
            Boolean cached,
            String message
    ) {
    }
}
