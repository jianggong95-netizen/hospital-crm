package com.hospital.crm.followup.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
            String visitType,
            String description,
            String status,
            int sortNo
    ) {
    }

    public record CategoryCreateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{2,40}$", message = "分类编码只允许字母、数字与下划线") String categoryCode,
            @NotBlank @Size(max = 100) String categoryName,
            @NotBlank @Pattern(regexp = "^(OUTPATIENT|INPATIENT|SURGERY|EXAM|MARKETING)$", message = "回访板块只允许门诊/住院/手术/体检/营销") String visitType,
            @Size(max = 500) String description,
            Integer sortNo
    ) {
    }

    public record CategoryUpdateRequest(
            @NotBlank @Size(max = 100) String categoryName,
            @Pattern(regexp = "^(OUTPATIENT|INPATIENT|SURGERY|EXAM|MARKETING)$", message = "回访板块只允许门诊/住院/手术/体检/营销") String visitType,
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

    /**
     * 随访任务统计。completionRate = completed / (completed+pending+inProgress+escalated)，
     * 保留两位小数，分母为 0 时取 0；overdue 为待执行且计划时间已过的任务数。
     * visitTypes 为板块/分类维度明细（/stats 的 visitType 参数过滤明细行，全局计数不受影响）：
     * groupBy=visitType（默认）每行一个板块，categoryCode/categoryName 为 null；
     * groupBy=category 每行一个自定义分类，并回填其所属板块。
     */
    public record StatsView(
            long total,
            long pending,
            long inProgress,
            long completed,
            long escalated,
            long cancelled,
            long overdue,
            BigDecimal completionRate,
            List<VisitTypeStats> visitTypes
    ) {
    }

    /**
     * 板块/分类维度任务统计行，completionRate 算法与全局一致。
     */
    public record VisitTypeStats(
            String visitType,
            String visitTypeLabel,
            String categoryCode,
            String categoryName,
            long totalTasks,
            long completedTasks,
            long pendingTasks,
            long inProgressTasks,
            long escalatedTasks,
            long cancelledTasks,
            long overdueTasks,
            BigDecimal completionRate
    ) {
    }

    // ------------------------------------------------------------------
    // HIS 事件对接（接口预留：HIS/集成平台推送就诊、出院、手术、体检报告事件）
    // ------------------------------------------------------------------
    public record HisEventRequest(
            @NotBlank @Pattern(regexp = "^(HIS_OUTPATIENT_VISIT|HIS_DISCHARGE|HIS_SURGERY|HIS_EXAM_REPORT)$",
                    message = "事件类型只支持 HIS_OUTPATIENT_VISIT/HIS_DISCHARGE/HIS_SURGERY/HIS_EXAM_REPORT") String eventType,
            @NotBlank @Size(max = 120) String eventKey,
            UUID customerId,
            @Size(max = 100) String externalPatientId,
            @Size(max = 50) String sourceSystem,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
    }

    public record HisEventView(
            UUID id,
            String eventKey,
            String eventType,
            UUID customerId,
            String customerName,
            String externalPatientId,
            String sourceSystem,
            Instant occurredAt,
            String status,
            int createdTasks,
            int skippedTasks,
            String errorMessage,
            Instant createdAt
    ) {
    }

    /**
     * HIS 事件 → 随访映射目录（HIS 对接"视图需求"数据源）：
     * defaultCategories 为该事件映射板块下当前启用中的分类编码；
     * recommendedPlans 为当前启用中、按该事件类型触发的计划编号；
     * requiredHisFields 为 HIS/集成平台推送该事件时应携带的业务字段清单
     * （eventKey、患者标识、事件发生时间为四类事件通用必备字段）。
     */
    public record HisEventCatalogItem(
            String eventType,
            String eventTypeLabel,
            String visitType,
            String visitTypeLabel,
            List<String> defaultCategories,
            List<String> recommendedPlans,
            List<String> requiredHisFields,
            String triggerDescription
    ) {
    }
}
