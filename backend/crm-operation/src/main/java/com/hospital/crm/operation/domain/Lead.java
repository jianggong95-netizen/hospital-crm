package com.hospital.crm.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 运营线索。手机号与证件号仅保存密文、检索哈希与脱敏值，任何接口都不返回明文。
 * 线索转客户后保留 converted_customer_id 与 crm_lead_conversion 记录，保证可追溯。
 */
@Entity
@Table(name = "crm_lead")
public class Lead {

    @Id
    private UUID id;

    @Column(name = "lead_no", nullable = false, length = 32)
    private String leadNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 16)
    private String gender = "unknown";

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_encrypted")
    private String phoneEncrypted;

    @Column(name = "phone_hash", length = 128)
    private String phoneHash;

    @Column(name = "phone_masked", length = 32)
    private String phoneMasked;

    @Column(name = "id_no_encrypted")
    private String idNoEncrypted;

    @Column(name = "id_no_hash", length = 128)
    private String idNoHash;

    @Column(name = "id_no_masked", length = 32)
    private String idNoMasked;

    @Column(name = "source_channel_code", nullable = false, length = 64)
    private String sourceChannelCode;

    @Column(nullable = false, length = 20)
    private String status = "NEW";

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(length = 1000)
    private String remark;

    @Column(name = "converted_customer_id")
    private UUID convertedCustomerId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "merged_into_lead_id")
    private UUID mergedIntoLeadId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

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

    public String getLeadNo() {
        return leadNo;
    }

    public void setLeadNo(String leadNo) {
        this.leadNo = leadNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getPhoneEncrypted() {
        return phoneEncrypted;
    }

    public void setPhoneEncrypted(String phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked) {
        this.phoneMasked = phoneMasked;
    }

    public String getIdNoEncrypted() {
        return idNoEncrypted;
    }

    public void setIdNoEncrypted(String idNoEncrypted) {
        this.idNoEncrypted = idNoEncrypted;
    }

    public String getIdNoHash() {
        return idNoHash;
    }

    public void setIdNoHash(String idNoHash) {
        this.idNoHash = idNoHash;
    }

    public String getIdNoMasked() {
        return idNoMasked;
    }

    public void setIdNoMasked(String idNoMasked) {
        this.idNoMasked = idNoMasked;
    }

    public String getSourceChannelCode() {
        return sourceChannelCode;
    }

    public void setSourceChannelCode(String sourceChannelCode) {
        this.sourceChannelCode = sourceChannelCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public UUID getConvertedCustomerId() {
        return convertedCustomerId;
    }

    public void setConvertedCustomerId(UUID convertedCustomerId) {
        this.convertedCustomerId = convertedCustomerId;
    }

    public Instant getConvertedAt() {
        return convertedAt;
    }

    public void setConvertedAt(Instant convertedAt) {
        this.convertedAt = convertedAt;
    }

    public UUID getMergedIntoLeadId() {
        return mergedIntoLeadId;
    }

    public void setMergedIntoLeadId(UUID mergedIntoLeadId) {
        this.mergedIntoLeadId = mergedIntoLeadId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
