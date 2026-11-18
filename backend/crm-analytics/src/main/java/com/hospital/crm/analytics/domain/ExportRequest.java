package com.hospital.crm.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 数据导出申请。pending → approved/rejected；审批通过时按全院口径重新计算报表，
 * 生成脱敏 CSV（csv_content）、行数与 SHA-256 文件哈希，保证导出结果可追溯。
 */
@Entity
@Table(name = "crm_export_request")
public class ExportRequest {

    @Id
    private UUID id;

    @Column(name = "report_code", nullable = false, length = 64)
    private String reportCode;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(name = "data_from", nullable = false)
    private LocalDate dataFrom;

    @Column(name = "data_to", nullable = false)
    private LocalDate dataTo;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "approver_user_id")
    private UUID approverUserId;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "csv_content")
    private String csvContent;

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

    public String getReportCode() {
        return reportCode;
    }

    public void setReportCode(String reportCode) {
        this.reportCode = reportCode;
    }

    public UUID getRequesterUserId() {
        return requesterUserId;
    }

    public void setRequesterUserId(UUID requesterUserId) {
        this.requesterUserId = requesterUserId;
    }

    public LocalDate getDataFrom() {
        return dataFrom;
    }

    public void setDataFrom(LocalDate dataFrom) {
        this.dataFrom = dataFrom;
    }

    public LocalDate getDataTo() {
        return dataTo;
    }

    public void setDataTo(LocalDate dataTo) {
        this.dataTo = dataTo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getApproverUserId() {
        return approverUserId;
    }

    public void setApproverUserId(UUID approverUserId) {
        this.approverUserId = approverUserId;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getCsvContent() {
        return csvContent;
    }

    public void setCsvContent(String csvContent) {
        this.csvContent = csvContent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
