package com.hospital.crm.escort.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class EscortDtos {

    private EscortDtos() {
    }

    // ------------------------------------------------------------------
    // 陪诊师档案与资质
    // ------------------------------------------------------------------

    public record EscortCreateRequest(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 100, message = "姓名长度不能超过 100")
            String name,
            @Pattern(regexp = "unknown|male|female", message = "性别取值不合法")
            String gender,
            @Size(max = 32, message = "手机号长度不合法")
            String phone,
            @Size(max = 20, message = "服务区域数量不能超过 20")
            List<@Size(max = 50, message = "单个服务区域编码长度不能超过 50") String> serviceAreas,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record EscortUpdateRequest(
            @Size(max = 100, message = "姓名长度不能超过 100")
            String name,
            @Pattern(regexp = "unknown|male|female", message = "性别取值不合法")
            String gender,
            @Size(max = 32, message = "手机号长度不合法")
            String phone,
            @Size(max = 20, message = "服务区域数量不能超过 20")
            List<@Size(max = 50, message = "单个服务区域编码长度不能超过 50") String> serviceAreas,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record EscortStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    public record QualificationCreateRequest(
            @NotBlank(message = "资质类型不能为空")
            @Pattern(regexp = "nurse|caregiver|health_manager|other", message = "资质类型取值不合法")
            String qualType,
            @NotBlank(message = "证书编号不能为空")
            @Size(max = 64, message = "证书编号长度不能超过 64")
            String certNo,
            @NotNull(message = "发证日期不能为空")
            LocalDate issuedAt,
            LocalDate expiresAt
    ) {
    }

    public record EscortSummary(
            UUID id,
            String escortNo,
            String name,
            String gender,
            String phoneMasked,
            List<String> serviceAreas,
            String status,
            Double avgRating,
            long reviewCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record EscortQualificationView(
            UUID id,
            String qualType,
            String certNo,
            LocalDate issuedAt,
            LocalDate expiresAt,
            String status,
            boolean expired
    ) {
    }

    public record EscortDetail(
            EscortSummary summary,
            String remark,
            List<EscortQualificationView> qualifications
    ) {
    }

    // ------------------------------------------------------------------
    // 排班
    // ------------------------------------------------------------------

    public record ScheduleCreateRequest(
            @NotNull(message = "陪诊师不能为空")
            UUID escortId,
            @NotNull(message = "排班日期不能为空")
            LocalDate workDate,
            @NotNull(message = "开始时间不能为空")
            LocalTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalTime endTime
    ) {
    }

    public record ScheduleUpdateRequest(
            @NotNull(message = "排班日期不能为空")
            LocalDate workDate,
            @NotNull(message = "开始时间不能为空")
            LocalTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalTime endTime
    ) {
    }

    public record ScheduleView(
            UUID id,
            UUID escortId,
            String escortName,
            LocalDate workDate,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }

    // ------------------------------------------------------------------
    // 陪诊订单
    // ------------------------------------------------------------------

    public record OrderCreateRequest(
            @NotNull(message = "客户不能为空")
            UUID customerId,
            UUID appointmentId,
            @NotNull(message = "服务日期不能为空")
            LocalDate serviceDate,
            @NotNull(message = "开始时间不能为空")
            LocalTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalTime endTime,
            @NotBlank(message = "服务区域不能为空")
            @Size(max = 100, message = "服务区域长度不能超过 100")
            String area,
            @Size(max = 32, message = "联系电话长度不合法")
            String contactPhone,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record DispatchRequest(
            @NotNull(message = "陪诊师不能为空")
            UUID escortId
    ) {
    }

    public record ReasonRequest(
            @NotBlank(message = "原因不能为空")
            @Size(max = 500, message = "原因长度不能超过 500")
            String reason
    ) {
    }

    public record FinishRequest(
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record ReviewRequest(
            @NotNull(message = "评分不能为空")
            @Min(value = 1, message = "评分不得低于 1")
            @Max(value = 5, message = "评分不得高于 5")
            Integer rating,
            @Size(max = 1000, message = "评价内容长度不能超过 1000")
            String content
    ) {
    }

    public record OrderSummary(
            UUID id,
            String orderNo,
            UUID customerId,
            String customerName,
            UUID appointmentId,
            UUID escortId,
            String escortName,
            LocalDate serviceDate,
            LocalTime startTime,
            LocalTime endTime,
            String area,
            String status,
            Instant actualStartAt,
            Instant actualEndAt,
            Integer durationMinutes,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record OrderReviewView(
            UUID id,
            Integer rating,
            String content,
            String createdBy,
            Instant createdAt
    ) {
    }

    public record OrderDetail(
            OrderSummary summary,
            String contactPhoneMasked,
            String remark,
            String rejectReason,
            String cancelReason,
            Instant assignedAt,
            Instant acceptedAt,
            OrderReviewView review
    ) {
    }

    public record ReviewView(
            UUID id,
            String orderNo,
            UUID escortId,
            String escortName,
            Integer rating,
            String content,
            String createdBy,
            Instant createdAt
    ) {
    }
}
