package com.hospital.crm.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户外部身份标识：如 HIS 患者主索引。仅保存密文、检索哈希与核验状态。
 */
@Entity
@Table(name = "crm_customer_identity")
public class CustomerIdentity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "source_system", nullable = false, length = 50)
    private String sourceSystem;

    @Column(name = "identity_type", nullable = false, length = 32)
    private String identityType;

    @Column(name = "identity_value_encrypted")
    private String identityValueEncrypted;

    @Column(name = "identity_value_hash", nullable = false, length = 128)
    private String identityValueHash;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (validFrom == null) {
            validFrom = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public String getIdentityValueEncrypted() {
        return identityValueEncrypted;
    }

    public void setIdentityValueEncrypted(String identityValueEncrypted) {
        this.identityValueEncrypted = identityValueEncrypted;
    }

    public String getIdentityValueHash() {
        return identityValueHash;
    }

    public void setIdentityValueHash(String identityValueHash) {
        this.identityValueHash = identityValueHash;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
