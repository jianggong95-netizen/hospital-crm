package com.hospital.crm.insurance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 商保往来邮件。message_id 用于邮件同步幂等（入站邮件重复回传时直接复用既有记录）；
 * 正文仅保存摘要，附件完整性由 crm_insurance_email_attachment 的哈希记录。
 */
@Entity
@Table(name = "crm_insurance_email")
public class InsuranceEmail {

    @Id
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false, length = 16)
    private String direction;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(name = "from_address", length = 160)
    private String fromAddress;

    @Column(name = "to_address", length = 160)
    private String toAddress;

    @Column(name = "body_excerpt", length = 1000)
    private String bodyExcerpt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "attachment_count", nullable = false)
    private int attachmentCount;

    @Column(name = "operator_user_id")
    private UUID operatorUserId;

    @Column(nullable = false, length = 16)
    private String status;

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

    public UUID getCaseId() {
        return caseId;
    }

    public void setCaseId(UUID caseId) {
        this.caseId = caseId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getBodyExcerpt() {
        return bodyExcerpt;
    }

    public void setBodyExcerpt(String bodyExcerpt) {
        this.bodyExcerpt = bodyExcerpt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }

    public void setAttachmentCount(int attachmentCount) {
        this.attachmentCount = attachmentCount;
    }

    public UUID getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(UUID operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
