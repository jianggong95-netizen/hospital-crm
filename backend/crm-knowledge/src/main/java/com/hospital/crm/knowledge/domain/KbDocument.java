package com.hospital.crm.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识文档。状态机：DRAFT（草稿）→ REVIEWING（待审核）→ PUBLISHED（已发布）→ OFFLINE（已下架）。
 * currentVersion 为已发布版本号，草稿切片挂在 currentVersion + 1 上，审核通过后发布为新版本。
 */
@Entity
@Table(name = "crm_kb_document")
public class KbDocument {

    @Id
    private UUID id;

    @Column(name = "doc_no", nullable = false, length = 40)
    private String docNo;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(length = 500)
    private String summary;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType = "manual";

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 16)
    private String fileType;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "offline_at")
    private Instant offlineAt;

    @Column(name = "offline_reason", length = 500)
    private String offlineReason;

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

    public String getDocNo() {
        return docNo;
    }

    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getOfflineAt() {
        return offlineAt;
    }

    public void setOfflineAt(Instant offlineAt) {
        this.offlineAt = offlineAt;
    }

    public String getOfflineReason() {
        return offlineReason;
    }

    public void setOfflineReason(String offlineReason) {
        this.offlineReason = offlineReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
