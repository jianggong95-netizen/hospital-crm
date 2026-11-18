package com.hospital.crm.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 转人工与客服转接记录，用于追踪请求次数、来源与处理结果。
 */
@Entity
@Table(name = "crm_chat_handoff")
public class ChatHandoff {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    @Column(nullable = false, length = 32)
    private String reason;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "from_staff_id")
    private UUID fromStaffId;

    @Column(name = "to_staff_id")
    private UUID toStaffId;

    @Column(length = 500)
    private String note;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (requestedAt == null) {
            requestedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getFromStaffId() {
        return fromStaffId;
    }

    public void setFromStaffId(UUID fromStaffId) {
        this.fromStaffId = fromStaffId;
    }

    public UUID getToStaffId() {
        return toStaffId;
    }

    public void setToStaffId(UUID toStaffId) {
        this.toStaffId = toStaffId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
