package com.hospital.crm.insurance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 保险公司。联系人手机号仅保存 HMAC 哈希与脱敏值，明文不落库；
 * 结算方式 api/email/manual，停用公司不允许新建保单。
 */
@Entity
@Table(name = "crm_insurance_company")
public class InsuranceCompany {

    @Id
    private UUID id;

    @Column(name = "company_code", nullable = false, length = 32)
    private String companyCode;

    @Column(name = "company_name", nullable = false, length = 120)
    private String companyName;

    @Column(name = "contact_name", length = 60)
    private String contactName;

    @Column(name = "contact_phone_hash", length = 64)
    private String contactPhoneHash;

    @Column(name = "contact_phone_masked", length = 32)
    private String contactPhoneMasked;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

    @Column(name = "settlement_type", nullable = false, length = 16)
    private String settlementType = "manual";

    @Column(nullable = false, length = 16)
    private String status = "active";

    @Column(length = 500)
    private String remark;

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

    public String getCompanyCode() {
        return companyCode;
    }

    public void setCompanyCode(String companyCode) {
        this.companyCode = companyCode;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhoneHash() {
        return contactPhoneHash;
    }

    public void setContactPhoneHash(String contactPhoneHash) {
        this.contactPhoneHash = contactPhoneHash;
    }

    public String getContactPhoneMasked() {
        return contactPhoneMasked;
    }

    public void setContactPhoneMasked(String contactPhoneMasked) {
        this.contactPhoneMasked = contactPhoneMasked;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getSettlementType() {
        return settlementType;
    }

    public void setSettlementType(String settlementType) {
        this.settlementType = settlementType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
