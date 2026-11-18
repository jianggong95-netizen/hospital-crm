package com.hospital.crm.insurance.domain;

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
 * 商保案件事件。每次状态迁移、分配、材料与邮件动作均写入一条事件，
 * detail 仅保留业务字段快照，不含患者身份信息等敏感内容。
 */
@Entity
@Table(name = "crm_insurance_event")
public class InsuranceEvent {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "from_state", length = 24)
    private String fromState;

    @Column(name = "to_state", length = 24)
    private String toState;

    @Column(name = "operator_user_id")
    private UUID operatorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> detail = new LinkedHashMap<>();

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
        if (detail == null) {
            detail = new LinkedHashMap<>();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getFromState() {
        return fromState;
    }

    public void setFromState(String fromState) {
        this.fromState = fromState;
    }

    public String getToState() {
        return toState;
    }

    public void setToState(String toState) {
        this.toState = toState;
    }

    public UUID getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(UUID operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
