package com.hospital.crm.followup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HIS 推送的随访事件（接口预留落地点）：按 eventKey 幂等，
 * 状态 PROCESSED（已触发计划）/ NO_MATCH（无法定位客户）/ FAILED（处理异常）。
 */
@Entity
@Table(name = "crm_followup_his_event")
public class FollowupHisEvent {

    @Id
    private UUID id;

    @Column(name = "event_key", nullable = false, length = 120)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "external_patient_id", length = 100)
    private String externalPatientId;

    @Column(name = "source_system", nullable = false, length = 50)
    private String sourceSystem = "HIS";

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload = new LinkedHashMap<>();

    @Column(nullable = false, length = 20)
    private String status = "PROCESSED";

    @Column(name = "created_tasks", nullable = false)
    private int createdTasks;

    @Column(name = "skipped_tasks", nullable = false)
    private int skippedTasks;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getExternalPatientId() {
        return externalPatientId;
    }

    public void setExternalPatientId(String externalPatientId) {
        this.externalPatientId = externalPatientId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCreatedTasks() {
        return createdTasks;
    }

    public void setCreatedTasks(int createdTasks) {
        this.createdTasks = createdTasks;
    }

    public int getSkippedTasks() {
        return skippedTasks;
    }

    public void setSkippedTasks(int skippedTasks) {
        this.skippedTasks = skippedTasks;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
