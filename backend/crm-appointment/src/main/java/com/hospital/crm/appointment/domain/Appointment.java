package com.hospital.crm.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 预约单。联系电话仅保存密文、检索哈希与脱敏值，接口不返回明文。
 * 状态机：BOOKED → CONFIRMED → COMPLETED/CANCELLED/NO_SHOW，取消规则与容量释放由服务层保证。
 */
@Entity
@Table(name = "crm_appointment")
public class Appointment {

    @Id
    private UUID id;

    @Column(name = "appointment_no", nullable = false, length = 32)
    private String appointmentNo;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "type_code", nullable = false, length = 64)
    private String typeCode;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(nullable = false, length = 20)
    private String status = "BOOKED";

    @Column(name = "contact_phone_encrypted")
    private String contactPhoneEncrypted;

    @Column(name = "contact_phone_hash", length = 128)
    private String contactPhoneHash;

    @Column(name = "contact_phone_masked", length = 32)
    private String contactPhoneMasked;

    @Column(length = 1000)
    private String remark;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    @Column(name = "org_id")
    private UUID orgId;

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

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(String appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public void setSlotId(UUID slotId) {
        this.slotId = slotId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getNoShowAt() {
        return noShowAt;
    }

    public void setNoShowAt(Instant noShowAt) {
        this.noShowAt = noShowAt;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
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
