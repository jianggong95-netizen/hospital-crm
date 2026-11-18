package com.hospital.crm.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 客服会话。状态机：AI（AI 接待）→ QUEUED（人工队列）→ ACTIVE（人工接待中）→ CLOSED。
 * 首次明确要求人工时仅记录确认，第二次请求自动进入队列；投诉、紧急、敏感与低置信度可直接转人工。
 */
@Entity
@Table(name = "crm_chat_session")
public class ChatSession {

    @Id
    private UUID id;

    @Column(name = "session_no", nullable = false, length = 40)
    private String sessionNo;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 24)
    private String channel;

    @Column(name = "current_handler_type", nullable = false, length = 20)
    private String currentHandlerType = "ai";

    @Column(name = "assigned_staff_id")
    private UUID assignedStaffId;

    @Column(name = "handoff_request_count", nullable = false)
    private int handoffRequestCount;

    @Column(nullable = false, length = 20)
    private String status = "AI";

    @Column(nullable = false, length = 16)
    private String priority = "normal";

    @Column(name = "queue_entered_at")
    private Instant queueEnteredAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "ai_summary")
    private String aiSummary;

    @Column(name = "authorization_scope", nullable = false, length = 200)
    private String authorizationScope = "basic";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (startedAt == null) {
            startedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSessionNo() {
        return sessionNo;
    }

    public void setSessionNo(String sessionNo) {
        this.sessionNo = sessionNo;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getCurrentHandlerType() {
        return currentHandlerType;
    }

    public void setCurrentHandlerType(String currentHandlerType) {
        this.currentHandlerType = currentHandlerType;
    }

    public UUID getAssignedStaffId() {
        return assignedStaffId;
    }

    public void setAssignedStaffId(UUID assignedStaffId) {
        this.assignedStaffId = assignedStaffId;
    }

    public int getHandoffRequestCount() {
        return handoffRequestCount;
    }

    public void setHandoffRequestCount(int handoffRequestCount) {
        this.handoffRequestCount = handoffRequestCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Instant getQueueEnteredAt() {
        return queueEnteredAt;
    }

    public void setQueueEnteredAt(Instant queueEnteredAt) {
        this.queueEnteredAt = queueEnteredAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public String getAuthorizationScope() {
        return authorizationScope;
    }

    public void setAuthorizationScope(String authorizationScope) {
        this.authorizationScope = authorizationScope;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public UUID getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(UUID closedBy) {
        this.closedBy = closedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
