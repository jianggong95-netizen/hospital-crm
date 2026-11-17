package com.hospital.crm.followup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 随访任务。状态机：PENDING→IN_PROGRESS→COMPLETED，
 * 提交高风险答案或人工升级进入 ESCALATED，处置后回到 COMPLETED；
 * CANCELLED/COMPLETED 为终态。trigger_event_key 与 plan_id 联合唯一，
 * 保证同一事件重复投递不会重复生成任务。
 */
@Entity
@Table(name = "crm_followup_task")
public class FollowupTask {

    @Id
    private UUID id;

    @Column(name = "task_no", nullable = false, length = 40)
    private String taskNo;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "category_code", nullable = false, length = 40)
    private String categoryCode;

    @Column(nullable = false, length = 24)
    private String channel;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "planned_at", nullable = false)
    private Instant plannedAt;

    @Column(nullable = false, length = 24)
    private String status = "PENDING";

    @Column(name = "result_code", length = 40)
    private String resultCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", nullable = false)
    private Map<String, Object> resultPayload = new LinkedHashMap<>();

    @Column(name = "escalation_required", nullable = false)
    private boolean escalationRequired;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "template_code", length = 40)
    private String templateCode;

    @Column(name = "trigger_type", length = 40)
    private String triggerType;

    @Column(name = "trigger_event_key", length = 120)
    private String triggerEventKey;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "escalation_reason", length = 500)
    private String escalationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public Instant getPlannedAt() {
        return plannedAt;
    }

    public void setPlannedAt(Instant plannedAt) {
        this.plannedAt = plannedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public Map<String, Object> getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(Map<String, Object> resultPayload) {
        this.resultPayload = resultPayload;
    }

    public boolean isEscalationRequired() {
        return escalationRequired;
    }

    public void setEscalationRequired(boolean escalationRequired) {
        this.escalationRequired = escalationRequired;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerEventKey() {
        return triggerEventKey;
    }

    public void setTriggerEventKey(String triggerEventKey) {
        this.triggerEventKey = triggerEventKey;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(Instant escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
