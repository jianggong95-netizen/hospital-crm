package com.hospital.crm.callcenter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 通话录音元数据。recording_key 为厂商对象存储键，同一录音多次入库复用同一条记录；
 * access_count 记录访问次数，配合审计留痕满足录音访问合规要求。
 */
@Entity
@Table(name = "crm_call_recording")
public class CallRecording {

    @Id
    private UUID id;

    @Column(name = "recording_key", nullable = false, length = 255)
    private String recordingKey;

    /** vendor/oss/local */
    @Column(name = "storage_type", nullable = false, length = 16)
    private String storageType = "vendor";

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "access_count", nullable = false)
    private Integer accessCount = 0;

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
        if (accessCount == null) {
            accessCount = 0;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRecordingKey() {
        return recordingKey;
    }

    public void setRecordingKey(String recordingKey) {
        this.recordingKey = recordingKey;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
