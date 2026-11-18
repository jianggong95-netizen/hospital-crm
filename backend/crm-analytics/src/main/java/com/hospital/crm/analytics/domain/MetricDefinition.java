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
 * 指标定义。每项指标必须携带口径说明（验收点），来源表与时间字段如实登记；
 * 分类限定 customer/operation/appointment/revenue/followup/complaint/callcenter/insurance。
 */
@Entity
@Table(name = "crm_metric_definition")
public class MetricDefinition {

    @Id
    private UUID id;

    @Column(name = "metric_code", nullable = false, length = 64)
    private String metricCode;

    @Column(name = "metric_name", nullable = false, length = 120)
    private String metricName;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "caliber_description", nullable = false)
    private String caliberDescription;

    @Column(name = "data_source", nullable = false, length = 120)
    private String dataSource;

    @Column(length = 20)
    private String unit;

    @Column(name = "decimal_places", nullable = false)
    private Integer decimalPlaces = 0;

    @Column(nullable = false, length = 20)
    private String status = "enabled";

    @Column(length = 500)
    private String remark;

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

    public String getMetricCode() {
        return metricCode;
    }

    public void setMetricCode(String metricCode) {
        this.metricCode = metricCode;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCaliberDescription() {
        return caliberDescription;
    }

    public void setCaliberDescription(String caliberDescription) {
        this.caliberDescription = caliberDescription;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(Integer decimalPlaces) {
        this.decimalPlaces = decimalPlaces;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
