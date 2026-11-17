package com.hospital.crm.homevisit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户服务地址。联系电话仅保存密文、检索哈希与脱敏三列，接口不返回明文；
 * 每个客户最多一个启用状态的默认地址（部分唯一索引兜底，服务层保证切换）。
 */
@Entity
@Table(name = "crm_home_address")
public class HomeAddress {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone_encrypted")
    private String contactPhoneEncrypted;

    @Column(name = "contact_phone_hash", length = 128)
    private String contactPhoneHash;

    @Column(name = "contact_phone_masked", length = 32)
    private String contactPhoneMasked;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false, length = 20)
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
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhoneEncrypted() {
        return contactPhoneEncrypted;
    }

    public void setContactPhoneEncrypted(String contactPhoneEncrypted) {
        this.contactPhoneEncrypted = contactPhoneEncrypted;
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

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
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
