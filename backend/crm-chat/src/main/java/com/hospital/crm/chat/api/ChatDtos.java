package com.hospital.crm.chat.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 在线客服与人工工作台接口契约。
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record SessionView(
            UUID id,
            String sessionNo,
            UUID customerId,
            String customerName,
            String phoneMasked,
            String channel,
            String status,
            String currentHandlerType,
            String priority,
            UUID assignedStaffId,
            String assignedStaffName,
            int handoffRequestCount,
            Instant queueEnteredAt,
            Instant claimedAt,
            Instant lastMessageAt,
            String lastMessagePreview,
            String aiSummary,
            String authorizationScope,
            Instant startedAt,
            Instant endedAt,
            UUID closedBy,
            String closedByName,
            long waitingSeconds
    ) {
    }

    public record MessageView(
            UUID id,
            String senderType,
            UUID senderId,
            String senderName,
            String content,
            String contentType,
            BigDecimal aiConfidence,
            List<String> knowledgeRefs,
            Instant createdAt
    ) {
    }

    public record HandoffView(
            UUID id,
            int requestCount,
            String reason,
            String mode,
            String status,
            UUID fromStaffId,
            String fromStaffName,
            UUID toStaffId,
            String toStaffName,
            String note,
            Instant requestedAt,
            Instant acceptedAt
    ) {
    }

    public record EventView(
            UUID id,
            String eventType,
            String fromStatus,
            String toStatus,
            UUID operatorId,
            String operatorName,
            String note,
            Instant createdAt
    ) {
    }

    public record SessionDetail(
            SessionView session,
            List<MessageView> messages,
            List<HandoffView> handoffs,
            List<EventView> events
    ) {
    }

    public record QueueItemView(
            UUID sessionId,
            String sessionNo,
            String customerName,
            String phoneMasked,
            String channel,
            String priority,
            long waitingSeconds,
            int queuePosition,
            String lastMessage,
            String aiSummary,
            String reason
    ) {
    }

    public record HandoffResult(
            boolean needsConfirmation,
            String message,
            Integer queuePosition,
            SessionView session
    ) {
    }

    public record CreateSessionRequest(
            @NotNull UUID customerId,
            @NotBlank @Pattern(regexp = "^(web|h5|mini_program|app|phone|wechat)$", message = "不支持的会话渠道")
            String channel,
            @Size(max = 200) String authorizationScope
    ) {
    }

    public record MessageCreateRequest(
            @NotBlank @Pattern(regexp = "^(CUSTOMER|AI|STAFF|SYSTEM)$", message = "不支持的消息发送方")
            String senderType,
            @NotBlank @Size(max = 4000) String content,
            @Pattern(regexp = "^(text|image|file|system)$", message = "不支持的消息类型") String contentType,
            @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") BigDecimal aiConfidence,
            List<String> knowledgeRefs,
            @Size(max = 64) String clientMessageId
    ) {
    }

    public record HandoffRequest(
            @NotBlank @Pattern(regexp = "^(explicit|complaint|urgent|sensitive|low_confidence)$",
                    message = "不支持的转人工原因") String reason,
            @Size(max = 500) String note
    ) {
    }

    public record TransferRequest(
            @NotNull UUID toStaffId,
            @Size(max = 500) String note
    ) {
    }

    public record CloseRequest(@Size(max = 500) String reason) {
    }
}
