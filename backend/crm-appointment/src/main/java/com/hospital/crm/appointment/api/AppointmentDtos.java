package com.hospital.crm.appointment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    // ------------------------------------------------------------------
    // 预约类型
    // ------------------------------------------------------------------

    public record TypeCreateRequest(
            @NotBlank(message = "类型编码不能为空")
            @Pattern(regexp = "[A-Z0-9_]{2,64}", message = "类型编码仅允许大写字母、数字与下划线")
            String typeCode,
            @NotBlank(message = "类型名称不能为空")
            @Size(max = 100, message = "类型名称长度不能超过 100")
            String typeName,
            Integer durationMinutes,
            Integer cancelCutoffHours,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record TypeUpdateRequest(
            @NotBlank(message = "类型名称不能为空")
            @Size(max = 100, message = "类型名称长度不能超过 100")
            String typeName,
            Integer durationMinutes,
            Integer cancelCutoffHours,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record TypeStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    public record TypeView(
            UUID id,
            String typeCode,
            String typeName,
            int durationMinutes,
            int cancelCutoffHours,
            String status,
            String remark,
            Instant createdAt
    ) {
    }

    // ------------------------------------------------------------------
    // 服务时段
    // ------------------------------------------------------------------

    public record SlotCreateRequest(
            @NotBlank(message = "预约类型不能为空")
            @Size(max = 64, message = "预约类型编码长度不合法")
            String typeCode,
            @NotNull(message = "服务日期不能为空")
            LocalDate slotDate,
            @NotNull(message = "开始时间不能为空")
            LocalTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalTime endTime,
            @NotNull(message = "容量不能为空")
            Integer capacity,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record SlotUpdateRequest(
            @NotNull(message = "服务日期不能为空")
            LocalDate slotDate,
            @NotNull(message = "开始时间不能为空")
            LocalTime startTime,
            @NotNull(message = "结束时间不能为空")
            LocalTime endTime,
            @NotNull(message = "容量不能为空")
            Integer capacity,
            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record SlotStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "OPEN|CLOSED", message = "状态取值不合法")
            String status
    ) {
    }

    public record SlotView(
            UUID id,
            String typeCode,
            String typeName,
            LocalDate slotDate,
            LocalTime startTime,
            LocalTime endTime,
            int capacity,
            int bookedCount,
            int remaining,
            String status,
            String remark,
            Instant updatedAt
    ) {
    }

    // ------------------------------------------------------------------
    // 预约
    // ------------------------------------------------------------------

    public record AppointmentCreateRequest(
            @NotNull(message = "客户不能为空")
            UUID customerId,
            @NotBlank(message = "预约类型不能为空")
            @Size(max = 64, message = "预约类型编码长度不合法")
            String typeCode,
            @NotNull(message = "服务时段不能为空")
            UUID slotId,
            @Size(max = 32, message = "联系电话长度不合法")
            String contactPhone,
            @Size(max = 1000, message = "备注长度不能超过 1000")
            String remark
    ) {
    }

    public record RescheduleRequest(
            @NotNull(message = "目标时段不能为空")
            UUID slotId,
            @Size(max = 500, message = "改期说明长度不能超过 500")
            String remark
    ) {
    }

    public record CancelRequest(
            @Size(max = 500, message = "取消原因长度不能超过 500")
            String reason
    ) {
    }

    public record StatusChangeRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "CONFIRMED|COMPLETED|NO_SHOW", message = "状态取值不合法")
            String status,
            @Size(max = 500, message = "说明长度不能超过 500")
            String reason
    ) {
    }

    public record AppointmentSummary(
            UUID id,
            String appointmentNo,
            UUID customerId,
            String customerNo,
            String customerName,
            String typeCode,
            String typeName,
            UUID slotId,
            LocalDate slotDate,
            LocalTime startTime,
            LocalTime endTime,
            String status,
            String contactPhoneMasked,
            Instant createdAt
    ) {
    }

    public record AppointmentDetail(
            AppointmentSummary summary,
            String remark,
            Instant cancelledAt,
            String cancelReason,
            Instant completedAt,
            Instant noShowAt,
            long noShowCount,
            List<ReminderView> reminders
    ) {
    }

    // ------------------------------------------------------------------
    // 提醒
    // ------------------------------------------------------------------

    public record ReminderView(
            UUID id,
            UUID appointmentId,
            String appointmentNo,
            String channel,
            Instant plannedAt,
            String status,
            int retryCount,
            int maxRetries,
            Instant nextRetryAt,
            String lastError,
            Instant sentAt
    ) {
    }

    public record DispatchResult(
            int scanned,
            int sent,
            int failed,
            Instant dispatchedAt
    ) {
    }

    // ------------------------------------------------------------------
    // 爽约记录
    // ------------------------------------------------------------------

    public record NoShowView(
            UUID id,
            UUID appointmentId,
            String appointmentNo,
            UUID customerId,
            String customerNo,
            String customerName,
            Instant appointmentAt,
            String reason,
            String recordedByName,
            Instant createdAt
    ) {
    }
}
