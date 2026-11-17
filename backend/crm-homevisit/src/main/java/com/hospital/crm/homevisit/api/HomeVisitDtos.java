package com.hospital.crm.homevisit.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class HomeVisitDtos {

    private HomeVisitDtos() {
    }

    // ------------------------------------------------------------------
    // 上门服务目录与审核
    // ------------------------------------------------------------------

    public record HomeServiceCreateRequest(
            @NotBlank(message = "服务编码不能为空")
            @Size(max = 32, message = "服务编码长度不能超过 32")
            String serviceCode,
            @NotBlank(message = "服务名称不能为空")
            @Size(max = 100, message = "服务名称长度不能超过 100")
            String serviceName,
            @NotBlank(message = "服务类别不能为空")
            @Pattern(regexp = "medical|non_medical", message = "服务类别取值不合法")
            String category,
            @Pattern(regexp = "nurse|caregiver|health_manager|doctor|other", message = "资质要求取值不合法")
            String requiredQualType,
            @NotNull(message = "服务时长不能为空")
            @Min(value = 1, message = "服务时长必须大于 0")
            Integer durationMinutes,
            @NotNull(message = "价格不能为空")
            @DecimalMin(value = "0.00", message = "价格不能为负数")
            BigDecimal price,
            @Size(max = 1000, message = "服务说明长度不能超过 1000")
            String description
    ) {
    }

    public record HomeServiceUpdateRequest(
            @Size(max = 100, message = "服务名称长度不能超过 100")
            String serviceName,
            @Pattern(regexp = "medical|non_medical", message = "服务类别取值不合法")
            String category,
            @Pattern(regexp = "nurse|caregiver|health_manager|doctor|other", message = "资质要求取值不合法")
            String requiredQualType,
            @Min(value = 1, message = "服务时长必须大于 0")
            Integer durationMinutes,
            @DecimalMin(value = "0.00", message = "价格不能为负数")
            BigDecimal price,
            @Size(max = 1000, message = "服务说明长度不能超过 1000")
            String description
    ) {
    }

    public record HomeServiceReviewRequest(
            @NotNull(message = "审核结论不能为空")
            Boolean approved,
            @Size(max = 500, message = "审核意见长度不能超过 500")
            String reason
    ) {
    }

    public record HomeServiceStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "published|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    public record HomeServiceView(
            UUID id,
            String serviceCode,
            String serviceName,
            String category,
            String requiredQualType,
            Integer durationMinutes,
            String price,
            String status,
            String description,
            String reviewReason,
            String reviewedBy,
            Instant reviewedAt
    ) {
    }

    // ------------------------------------------------------------------
    // 服务地址
    // ------------------------------------------------------------------

    public record HomeAddressCreateRequest(
            @NotNull(message = "客户不能为空")
            UUID customerId,
            @NotBlank(message = "服务地址不能为空")
            @Size(max = 300, message = "服务地址长度不能超过 300")
            String address,
            @NotBlank(message = "联系人不能为空")
            @Size(max = 100, message = "联系人长度不能超过 100")
            String contactName,
            @Size(max = 32, message = "联系电话长度不合法")
            String contactPhone,
            Boolean isDefault,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record HomeAddressUpdateRequest(
            @Size(max = 300, message = "服务地址长度不能超过 300")
            String address,
            @Size(max = 100, message = "联系人长度不能超过 100")
            String contactName,
            @Size(max = 32, message = "联系电话长度不合法")
            String contactPhone,
            Boolean isDefault,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record HomeAddressStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    public record HomeAddressView(
            UUID id,
            UUID customerId,
            String address,
            String contactName,
            String contactPhoneMasked,
            boolean isDefault,
            String status,
            String remark
    ) {
    }

    // ------------------------------------------------------------------
    // 人员资质
    // ------------------------------------------------------------------

    public record StaffQualificationCreateRequest(
            @NotNull(message = "人员不能为空")
            UUID userId,
            @NotBlank(message = "资质类型不能为空")
            @Pattern(regexp = "nurse|caregiver|health_manager|doctor|other", message = "资质类型取值不合法")
            String qualType,
            @NotBlank(message = "证书编号不能为空")
            @Size(max = 64, message = "证书编号长度不能超过 64")
            String certNo,
            @NotNull(message = "发证日期不能为空")
            LocalDate issuedAt,
            LocalDate expiresAt
    ) {
    }

    public record StaffQualificationView(
            UUID id,
            UUID userId,
            String username,
            String displayName,
            String qualType,
            String certNo,
            LocalDate issuedAt,
            LocalDate expiresAt,
            String status,
            boolean expired
    ) {
    }

    // ------------------------------------------------------------------
    // 上门服务订单
    // ------------------------------------------------------------------

    public record OrderCreateRequest(
            @NotBlank(message = "服务编码不能为空")
            @Size(max = 32, message = "服务编码长度不能超过 32")
            String serviceCode,
            @NotNull(message = "客户不能为空")
            UUID customerId,
            @NotNull(message = "服务地址不能为空")
            UUID addressId,
            @NotNull(message = "预约日期不能为空")
            LocalDate scheduledDate,
            @NotNull(message = "开始时间不能为空")
            LocalTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalTime endTime,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record OrderReviewRequest(
            @NotNull(message = "审核结论不能为空")
            Boolean approved,
            @Size(max = 500, message = "审核意见长度不能超过 500")
            String reason
    ) {
    }

    public record DispatchRequest(
            @NotNull(message = "服务人员不能为空")
            UUID staffUserId
    ) {
    }

    public record ReasonRequest(
            @NotBlank(message = "原因不能为空")
            @Size(max = 500, message = "原因长度不能超过 500")
            String reason
    ) {
    }

    public record ExecutionCreateRequest(
            @NotBlank(message = "执行内容不能为空")
            @Size(max = 1000, message = "执行内容长度不能超过 1000")
            String content,
            Instant visitAt
    ) {
    }

    public record ExceptionCreateRequest(
            @NotBlank(message = "异常级别不能为空")
            @Pattern(regexp = "low|medium|high|critical", message = "异常级别取值不合法")
            String level,
            @NotBlank(message = "异常类别不能为空")
            @Pattern(regexp = "vital_sign|equipment|patient_condition|staff_safety|other",
                    message = "异常类别取值不合法")
            String category,
            @NotBlank(message = "异常描述不能为空")
            @Size(max = 1000, message = "异常描述长度不能超过 1000")
            String description
    ) {
    }

    public record ResolveRequest(
            @NotBlank(message = "处置结果不能为空")
            @Size(max = 1000, message = "处置结果长度不能超过 1000")
            String resolution
    ) {
    }

    public record FinishRequest(
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record OrderSummary(
            UUID id,
            String orderNo,
            String serviceCode,
            String serviceName,
            UUID customerId,
            String customerName,
            UUID addressId,
            LocalDate scheduledDate,
            LocalTime startTime,
            LocalTime endTime,
            String status,
            UUID staffUserId,
            String staffName,
            Instant actualStartAt,
            Instant actualEndAt,
            Integer durationMinutes,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AddressSnapshot(
            String address,
            String contactName,
            String contactPhoneMasked
    ) {
    }

    public record ExecutionView(
            UUID id,
            Instant visitAt,
            String content,
            String recordedBy
    ) {
    }

    public record ExceptionView(
            UUID id,
            UUID orderId,
            String orderNo,
            String level,
            String category,
            String description,
            String status,
            String reportedBy,
            Instant escalatedAt,
            Instant resolvedAt
    ) {
    }

    public record OrderDetail(
            OrderSummary summary,
            String remark,
            String reviewReason,
            String cancelReason,
            Instant assignedAt,
            Instant acceptedAt,
            AddressSnapshot address,
            List<ExecutionView> executions,
            List<ExceptionView> exceptions,
            String price,
            String serviceName,
            String requiredQualType
    ) {
    }
}
