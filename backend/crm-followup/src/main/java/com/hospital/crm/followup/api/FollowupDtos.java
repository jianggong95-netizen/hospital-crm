package com.hospital.crm.followup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 随访中心接口契约。
 */
public final class FollowupDtos {

    private FollowupDtos() {
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------
    public record CategoryView(
            String categoryCode,
            String categoryName,
            String description,
            String status,
            int sortNo
    ) {
    }

    public record CategoryCreateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{2,40}$", message = "分类编码只允许字母、数字与下划线") String categoryCode,
            @NotBlank @Size(max = 100) String categoryName,
            @Size(max = 500) String description,
            Integer sortNo
    ) {
    }

    public record CategoryUpdateRequest(
            @NotBlank @Size(max = 100) String categoryName,
            @Size(max = 500) String description,
            Integer sortNo
    ) {
    }

    // ------------------------------------------------------------------
    // 模板
    // ------------------------------------------------------------------
    public record TemplateView(
            String templateCode,
            String categoryCode,
            String templateName,
            String channel,
            List<Map<String, Object>> questions,
            Map<String, Object> riskRules,
            int version,
            String status,
            String description
    ) {
    }

    public record TemplateCreateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{2,40}$", message = "模板编码只允许字母、数字与下划线") String templateCode,
            @NotBlank String categoryCode,
            @NotBlank @Size(max = 120) String templateName,
            @NotBlank String channel,
            List<Map<String, Object>> questions,
            Map<String, Object> riskRules,
            @Size(max = 500) String description
    ) {
    }

    public record TemplateUpdateRequest(
            @NotBlank @Size(max = 120) String templateName,
            @NotBlank String channel,
            List<Map<String, Object>> questions,
            Map<String, Object> riskRules,
            @Size(max = 500) String description
    ) {
    }

    // ------------------------------------------------------------------
    // 计划
    // ------------------------------------------------------------------
    public record PlanView(
            String planNo,
            String planName,
            String categoryCode,
            String categoryName,
            String templateCode,
            String triggerType,
            int offsetDays,
            String channel,
            UUID defaultAssigneeId,
            String defaultAssigneeName,
            String status,
            String description
    ) {
    }

    public record PlanCreateRequest(
            @NotBlank @Size(max = 120) String planName,
            @NotBlank String categoryCode,
            @NotBlank String templateCode,
            @NotBlank String triggerType,
            @NotNull Integer offsetDays,
            @NotBlank String channel,
            UUID defaultAssigneeId,
            @Size(max = 500) String description
    ) {
    }

    public record PlanUpdateRequest(
            @NotBlank @Size(max = 120) String planName,
            @NotBlank String triggerType,
            @NotNull Integer offsetDays,
            @NotBlank String channel,
            UUID defaultAssigneeId,
            @Size(max = 500) String description
    ) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    // ------------------------------------------------------------------
    // 任务
    // ------------------------------------------------------------------
    public record TaskView(
            UUID id,
            String taskNo,
            UUID customerId,
            String customerName,
            String phoneMasked,
            String categoryCode,
            String categoryName,
            String templateCode,
            String channel,
            UUID assigneeId,
            String assigneeName,
            Instant plannedAt,
            String status,
            String riskLevel,
            boolean escalationRequired,
            String resultCode,
            Instant completedAt,
            Instant createdAt,
            String planNo
    ) {
    }

    public record RecordView(
            UUID id,
            String riskLevel,
            Map<String, Object> answers,
            String resultCode,
            String remark,
            UUID operatorId,
            Instant createdAt
    ) {
    }

    public record TaskDetail(
            TaskView task,
            List<Map<String, Object>> questions,
            List<RecordView> records
    ) {
    }

    public record TaskCreateRequest(
            @NotNull UUID customerId,
            @NotBlank String categoryCode,
            String templateCode,
            @NotBlank String channel,
            @NotNull Instant plannedAt,
            UUID assigneeId
    ) {
    }

    public record TriggerRequest(
            @NotBlank String triggerType,
            @NotBlank @Size(max = 120) String eventKey,
            @NotNull UUID customerId,
            Instant occurredAt
    ) {
    }

    public record TriggerResult(
            int created,
            int skipped,
            List<TaskView> tasks
    ) {
    }

    public record AssignRequest(@NotNull UUID assigneeId) {
    }

    public record RecordCreateRequest(
            Map<String, String> answers,
            String resultCode,
            @Size(max = 1000) String remark
    ) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ResolveRequest(@Size(max = 1000) String remark) {
    }
}
