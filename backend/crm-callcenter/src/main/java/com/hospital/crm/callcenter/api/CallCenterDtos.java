package com.hospital.crm.callcenter.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 呼叫中心与短信接口 DTO。所有视图只输出脱敏号码，不含任何敏感明文。
 */
public final class CallCenterDtos {

    private CallCenterDtos() {
    }

    // ---------- 来电弹屏 ----------

    public record PopupCustomer(
            UUID id,
            String customerNo,
            String name,
            String gender,
            LocalDate birthDate,
            String phoneMasked,
            String status
    ) {
    }

    public record PopupView(
            boolean matched,
            String phoneMasked,
            PopupCustomer customer,
            long callHistoryCount,
            Instant lastCallAt
    ) {
    }

    // ---------- 通话记录 ----------

    public record CallRecordView(
            UUID id,
            String callNo,
            String vendorCallId,
            String direction,
            String callerMasked,
            String calleeMasked,
            UUID customerId,
            String customerName,
            String linkType,
            UUID linkId,
            String linkNo,
            String queueName,
            UUID agentUserId,
            String agentUserName,
            String status,
            Instant startedAt,
            Instant endedAt,
            Integer durationSeconds,
            boolean hasRecording,
            UUID recordingId,
            String source,
            Instant createdAt
    ) {
    }

    public record RecordingView(
            UUID id,
            String recordingKey,
            String storageType,
            Integer durationSeconds,
            Long fileSize,
            Integer accessCount,
            Instant createdAt
    ) {
    }

    public record CallRecordDetailView(
            CallRecordView call,
            RecordingView recording
    ) {
    }

    public record CreateCallRequest(
            @Size(max = 120, message = "厂商通话号长度不能超过 120")
            String vendorCallId,

            @NotBlank(message = "通话方向不能为空")
            @Pattern(regexp = "inbound|outbound", message = "通话方向取值不合法")
            String direction,

            @Size(max = 32, message = "主叫号码长度不合法")
            String callerNumber,

            @Size(max = 32, message = "被叫号码长度不合法")
            String calleeNumber,

            UUID customerId,

            @Pattern(regexp = "customer|complaint|session|appointment|none", message = "关联类型取值不合法")
            String linkType,

            UUID linkId,

            @Size(max = 80, message = "队列名称长度不能超过 80")
            String queueName,

            UUID agentUserId,

            @NotNull(message = "通话开始时间不能为空")
            Instant startedAt,

            Instant endedAt,

            Integer durationSeconds,

            @NotBlank(message = "通话状态不能为空")
            @Pattern(regexp = "connected|missed|transferred", message = "通话状态取值不合法")
            String status,

            @Size(max = 255, message = "录音键长度不能超过 255")
            String recordingKey,

            @Pattern(regexp = "vendor|oss|local", message = "录音存储类型取值不合法")
            String storageType
    ) {
    }

    public record LinkRequest(
            @NotBlank(message = "关联目标类型不能为空")
            @Pattern(regexp = "complaint|session|appointment|customer", message = "关联目标类型取值不合法")
            String targetType,

            @NotNull(message = "关联目标 ID 不能为空")
            UUID targetId
    ) {
    }

    public record RecordingAccessView(
            UUID recordingId,
            String callNo,
            String storageType,
            String accessUrl,
            String note
    ) {
    }

    // ---------- 短信模板 ----------

    public record SmsTemplateView(
            UUID id,
            String code,
            String name,
            String channel,
            String content,
            List<String> variables,
            String status,
            String remark,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TemplateCreateRequest(
            @NotBlank(message = "模板编码不能为空")
            @Size(max = 64, message = "模板编码长度不能超过 64")
            String code,

            @NotBlank(message = "模板名称不能为空")
            @Size(max = 120, message = "模板名称长度不能超过 120")
            String name,

            @NotBlank(message = "模板内容不能为空")
            String content,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record TemplateUpdateRequest(
            @Size(max = 120, message = "模板名称长度不能超过 120")
            String name,

            String content,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record TemplateStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "状态取值不合法")
            String status
    ) {
    }

    // ---------- 消息队列 ----------

    public record MessageSendRequest(
            @NotBlank(message = "发送渠道不能为空")
            @Pattern(regexp = "sms|wechat|voice", message = "发送渠道取值不合法")
            String channel,

            @Size(max = 64, message = "业务场景长度不能超过 64")
            String scene,

            @Size(max = 64, message = "模板编码长度不能超过 64")
            String templateCode,

            Map<String, String> variables,

            String content,

            @NotBlank(message = "接收号码不能为空")
            @Size(max = 32, message = "接收号码长度不合法")
            String recipientPhone,

            UUID customerId,

            @Size(max = 120, message = "业务键长度不能超过 120")
            String bizKey
    ) {
    }

    public record MessageView(
            UUID id,
            String msgNo,
            String bizKey,
            String templateCode,
            String templateName,
            String channel,
            String scene,
            String recipientMasked,
            UUID customerId,
            String customerName,
            String content,
            String provider,
            String status,
            Integer retryCount,
            Integer maxRetries,
            Instant nextRetryAt,
            String lastError,
            Instant sentAt,
            Instant createdAt
    ) {
    }

    public record DispatchResult(
            int scanned,
            int sent,
            int failed,
            Instant at
    ) {
    }
}
