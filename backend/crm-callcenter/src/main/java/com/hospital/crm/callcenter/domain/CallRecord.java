package com.hospital.crm.callcenter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 通话记录。方向 inbound/outbound，状态 connected/missed/transferred；
 * 主被叫号码仅存 HMAC 哈希与脱敏值，vendor_call_id 用于厂商同步幂等去重。
 */
@Entity
@Table(name = "crm_call_record")
public class CallRecord {

    @Id
    private UUID id;

    @Column(name = "call_no", nullable = false, length = 40)
    private String callNo;

    @Column(name = "vendor_call_id", length = 120)
    private String vendorCallId;

    @Column(nullable = false, length = 16)
    private String direction;

    @Column(name = "caller_hash", length = 64)
    private String callerHash;

    @Column(name = "caller_masked", length = 32)
    private String callerMasked;

    @Column(name = "callee_hash", length = 64)
    private String calleeHash;

    @Column(name = "callee_masked", length = 32)
    private String calleeMasked;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "link_type", nullable = false, length = 16)
    private String linkType = "none";

    @Column(name = "link_id")
    private UUID linkId;

    @Column(name = "link_no", length = 40)
    private String linkNo;

    @Column(name = "queue_name", length = 80)
    private String queueName;

    @Column(name = "agent_user_id")
    private UUID agentUserId;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "recording_id")
    private UUID recordingId;

    @Column(nullable = false, length = 16)
    private String source = "manual";

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
        if (createdAt == null) {
            createdAt = now;
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

    public String getCallNo() {
        return callNo;
    }

    public void setCallNo(String callNo) {
        this.callNo = callNo;
    }

    public String getVendorCallId() {
        return vendorCallId;
    }

    public void setVendorCallId(String vendorCallId) {
        this.vendorCallId = vendorCallId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getCallerHash() {
        return callerHash;
    }

    public void setCallerHash(String callerHash) {
        this.callerHash = callerHash;
    }

    public String getCallerMasked() {
        return callerMasked;
    }

    public void setCallerMasked(String callerMasked) {
        this.callerMasked = callerMasked;
    }

    public String getCalleeHash() {
        return calleeHash;
    }

    public void setCalleeHash(String calleeHash) {
        this.calleeHash = calleeHash;
    }

    public String getCalleeMasked() {
        return calleeMasked;
    }

    public void setCalleeMasked(String calleeMasked) {
        this.calleeMasked = calleeMasked;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getLinkType() {
        return linkType;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public UUID getLinkId() {
        return linkId;
    }

    public void setLinkId(UUID linkId) {
        this.linkId = linkId;
    }

    public String getLinkNo() {
        return linkNo;
    }

    public void setLinkNo(String linkNo) {
        this.linkNo = linkNo;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public UUID getAgentUserId() {
        return agentUserId;
    }

    public void setAgentUserId(UUID agentUserId) {
        this.agentUserId = agentUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public UUID getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(UUID recordingId) {
        this.recordingId = recordingId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
