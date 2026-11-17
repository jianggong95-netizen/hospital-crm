package com.hospital.crm.escort.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 陪诊订单。联系电话仅保存密文、检索哈希与脱敏值，接口不返回明文。
 * 状态机：PENDING→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED，
 * 拒绝回退 PENDING，取消仅限未开始服务的订单；COMPLETED/CANCELLED 为终态。
 */
@Entity
@Table(name = "crm_escort_order")
public class EscortOrder {

    @Id
    private UUID id;

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "escort_id")
    private UUID escortId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 100)
    private String area;

    @Column(name = "contact_phone_encrypted")
    private String contactPhoneEncrypted;

    @Column(name = "contact_phone_hash", length = 128)
    private String contactPhoneHash;

    @Column(name = "contact_phone_masked", length = 32)
    private String contactPhoneMasked;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(length = 1000)
    private String remark;

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

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(UUID appointmentId) {
        this.appointmentId = appointmentId;
    }

    public UUID getEscortId() {
        return escortId;
    }

    public void setEscortId(UUID escortId) {
        this.escortId = escortId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getActualStartAt() {
        return actualStartAt;
    }

    public void setActualStartAt(Instant actualStartAt) {
        this.actualStartAt = actualStartAt;
    }

    public Instant getActualEndAt() {
        return actualEndAt;
    }

    public void setActualEndAt(Instant actualEndAt) {
        this.actualEndAt = actualEndAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
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

    public int getVersion() {
        return version;
    }
}
