package com.hospital.crm.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 共享授权：资源所有人（owner）授权家庭成员（beneficiary）消费余额/积分/次卡。
 * 仅 ACTIVE 且在有效期内、can_consume=true 的授权可用于代付。
 */
@Entity
@Table(name = "crm_member_share")
public class MemberShare {

    @Id
    private UUID id;

    @Column(name = "owner_customer_id", nullable = false)
    private UUID ownerCustomerId;

    @Column(name = "beneficiary_customer_id", nullable = false)
    private UUID beneficiaryCustomerId;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "can_consume", nullable = false)
    private Boolean canConsume = Boolean.TRUE;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (validFrom == null) {
            validFrom = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerCustomerId() {
        return ownerCustomerId;
    }

    public void setOwnerCustomerId(UUID ownerCustomerId) {
        this.ownerCustomerId = ownerCustomerId;
    }

    public UUID getBeneficiaryCustomerId() {
        return beneficiaryCustomerId;
    }

    public void setBeneficiaryCustomerId(UUID beneficiaryCustomerId) {
        this.beneficiaryCustomerId = beneficiaryCustomerId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public Boolean getCanConsume() {
        return canConsume;
    }

    public void setCanConsume(Boolean canConsume) {
        this.canConsume = canConsume;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
