package com.hospital.crm.ops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 备份记录：由 ops/pg 备份脚本通过接口上报，含 64 位 SHA-256 与是否完成恢复演练。
 * 恢复演练结果单独登记，用于验收「备份恢复演练通过」的可追溯证据。
 */
@Entity
@Table(name = "crm_ops_backup_record")
public class OpsBackupRecord {

    @Id
    private UUID id;

    @Column(name = "backup_code", nullable = false, length = 80)
    private String backupCode;

    @Column(name = "backup_type", nullable = false, length = 16)
    private String backupType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(length = 120)
    private String target;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(length = 500)
    private String message;

    @Column(name = "reported_by", length = 120)
    private String reportedBy;

    @Column(name = "restore_verified", nullable = false)
    private boolean restoreVerified = false;

    @Column(name = "restore_verified_at")
    private Instant restoreVerifiedAt;

    @Column(name = "restore_message", length = 500)
    private String restoreMessage;

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

    public String getBackupCode() {
        return backupCode;
    }

    public void setBackupCode(String backupCode) {
        this.backupCode = backupCode;
    }

    public String getBackupType() {
        return backupType;
    }

    public void setBackupType(String backupType) {
        this.backupType = backupType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public boolean isRestoreVerified() {
        return restoreVerified;
    }

    public void setRestoreVerified(boolean restoreVerified) {
        this.restoreVerified = restoreVerified;
    }

    public Instant getRestoreVerifiedAt() {
        return restoreVerifiedAt;
    }

    public void setRestoreVerifiedAt(Instant restoreVerifiedAt) {
        this.restoreVerifiedAt = restoreVerifiedAt;
    }

    public String getRestoreMessage() {
        return restoreMessage;
    }

    public void setRestoreMessage(String restoreMessage) {
        this.restoreMessage = restoreMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
