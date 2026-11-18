package com.hospital.crm.insurance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 商保邮件附件元数据。仅记录文件摘要与大小，原件存入对象存储；
 * materialId 可关联已归档的案件材料。
 */
@Entity
@Table(name = "crm_insurance_email_attachment")
public class InsuranceEmailAttachment {

    @Id
    private UUID id;

    @Column(name = "email_id", nullable = false)
    private UUID emailId;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmailId() {
        return emailId;
    }

    public void setEmailId(UUID emailId) {
        this.emailId = emailId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(UUID materialId) {
        this.materialId = materialId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
