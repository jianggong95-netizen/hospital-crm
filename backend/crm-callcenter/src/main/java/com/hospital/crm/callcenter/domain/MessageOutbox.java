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
 * 消息发送队列（Outbox）。收件人号码仅存 HMAC 哈希与脱敏值，明文不落库；
 * biz_key 用于上游重试幂等去重；发送失败按 300×2^(n-1) 秒指数退避，
 * retry_count 达到 max_retries 后 next_retry_at 置空停止自动重试，仍可人工重试。
 */
@Entity
@Table(name = "crm_message_outbox")
public class MessageOutbox {

    @Id
    private UUID id;

    @Column(name = "msg_no", nullable = false, length = 40)
    private String msgNo;

    @Column(name = "biz_key", length = 120)
    private String bizKey;

    @Column(name = "template_id")
    private UUID templateId;

    /** sms/wechat/voice */
    @Column(nullable = false, length = 16)
    private String channel;

    @Column(length = 64)
    private String scene;

    @Column(name = "recipient_hash", nullable = false, length = 64)
    private String recipientHash;

    @Column(name = "recipient_masked", length = 32)
    private String recipientMasked;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false)
    private String content;

    @Column(length = 32)
    private String provider;

    /** PENDING/SENDING/SENT/FAILED/CANCELLED */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

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
        if (status == null) {
            status = "PENDING";
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getMsgNo() {
        return msgNo;
    }

    public void setMsgNo(String msgNo) {
        this.msgNo = msgNo;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getRecipientHash() {
        return recipientHash;
    }

    public void setRecipientHash(String recipientHash) {
        this.recipientHash = recipientHash;
    }

    public String getRecipientMasked() {
        return recipientMasked;
    }

    public void setRecipientMasked(String recipientMasked) {
        this.recipientMasked = recipientMasked;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
