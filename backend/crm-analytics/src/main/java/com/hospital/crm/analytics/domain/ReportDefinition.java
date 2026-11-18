package com.hospital.crm.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 报表定义。metric_summary 报表关联一组指标编码（逗号分隔），
 * customer_detail 报表输出客户明细（仅脱敏列）。
 */
@Entity
@Table(name = "crm_report_definition")
public class ReportDefinition {

    @Id
    private UUID id;

    @Column(name = "report_code", nullable = false, length = 64)
    private String reportCode;

    @Column(name = "report_name", nullable = false, length = 120)
    private String reportName;

    @Column(length = 500)
    private String description;

    @Column(name = "report_type", nullable = false, length = 32)
    private String reportType;

    @Column(name = "metric_codes")
    private String metricCodes;

    @Column(nullable = false, length = 20)
    private String status = "enabled";

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

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getMetricCodes() {
        return metricCodes;
    }

    public void setMetricCodes(String metricCodes) {
        this.metricCodes = metricCodes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
