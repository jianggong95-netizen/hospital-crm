package com.hospital.crm.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 线索转客户追溯记录。一次线索仅允许成功转化一次。
 */
@Entity
@Table(name = "crm_lead_conversion")
public class LeadConversion {

    @Id
    private UUID id;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "conversion_mode", nullable = false, length = 32)
    private String conversionMode;

    @Column(length = 500)
    private String remark;

    @Column(name = "converted_by")
    private UUID convertedBy;

    @Column(name = "converted_at", nullable = false)
    private Instant convertedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (convertedAt == null) {
            convertedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public void setLeadId(UUID leadId) {
        this.leadId = leadId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getConversionMode() {
        return conversionMode;
    }

    public void setConversionMode(String conversionMode) {
        this.conversionMode = conversionMode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public UUID getConvertedBy() {
        return convertedBy;
    }

    public void setConvertedBy(UUID convertedBy) {
        this.convertedBy = convertedBy;
    }

    public Instant getConvertedAt() {
        return convertedAt;
    }
}
