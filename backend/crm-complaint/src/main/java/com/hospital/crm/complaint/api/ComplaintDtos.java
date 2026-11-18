package com.hospital.crm.complaint.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 投诉表扬中心接口契约。
 */
public final class ComplaintDtos {

    private ComplaintDtos() {
    }

    // ------------------------------------------------------------------
    // 查询视图
    // ------------------------------------------------------------------
    public record TicketView(
            UUID id,
            String ticketNo,
            UUID customerId,
            String customerName,
            String phoneMasked,
            String ticketType,
            String severity,
            String subject,
            String description,
            String status,
            String source,
            UUID responsibleOrgId,
            String responsibleOrgName,
            UUID assigneeId,
            String assigneeName,
            Instant dueAt,
            boolean overdue,
            boolean escalated,
            int escalationLevel,
            Instant escalatedAt,
            String escalationReason,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt,
            UUID closedBy,
            String closedByName
    ) {
    }

    public record ActionView(
            UUID id,
            String actionType,
            String fromStatus,
            String toStatus,
            String content,
            UUID operatorId,
            String operatorName,
            Instant createdAt
    ) {
    }

    public record RectificationView(
            UUID id,
            String content,
            UUID responsibleOrgId,
            Instant dueAt,
            String status,
            Instant submittedAt,
            Instant verifiedAt,
            UUID verifiedBy,
            Instant createdAt
    ) {
    }

    public record FeedbackView(
            UUID id,
            int rating,
            boolean satisfied,
            String comment,
            String channel,
            Instant createdAt
    ) {
    }

    public record ReviewView(
            UUID id,
            UUID reviewerId,
            String reviewerName,
            String decision,
            String comment,
            Instant createdAt
    ) {
    }

    public record TicketDetail(
            TicketView ticket,
            List<ActionView> actions,
            List<RectificationView> rectifications,
            List<FeedbackView> feedbacks,
            List<ReviewView> reviews
    ) {
    }

    public record ScanResult(int escalated) {
    }

    // ------------------------------------------------------------------
    // 请求体
    // ------------------------------------------------------------------
    public record CreateRequest(
            @NotNull UUID customerId,
            @NotBlank @Pattern(regexp = "^(complaint|praise|suggestion)$", message = "工单类型仅支持投诉、表扬与建议")
            String ticketType,
            @NotBlank @Pattern(regexp = "^(low|normal|high|urgent)$", message = "严重程度不合法") String severity,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 4000) String description,
            String source,
            UUID responsibleOrgId,
            UUID assigneeId,
            Instant dueAt
    ) {
    }

    public record AssignRequest(
            @NotNull UUID assigneeId,
            UUID responsibleOrgId,
            Instant dueAt
    ) {
    }

    public record ActionCreateRequest(
            @NotBlank @Size(max = 40) String actionType,
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record RectificationCreateRequest(
            @NotBlank @Size(max = 2000) String content,
            UUID responsibleOrgId,
            Instant dueAt
    ) {
    }

    public record FeedbackCreateRequest(
            @NotNull @Min(value = 1, message = "评分最低为 1") @Max(value = 5, message = "评分最高为 5") Integer rating,
            @NotNull Boolean satisfied,
            @Size(max = 1000) String comment,
            String channel
    ) {
    }

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "^(PASS|REJECT)$", message = "复核结论仅支持通过或退回") String decision,
            @Size(max = 1000) String comment
    ) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
