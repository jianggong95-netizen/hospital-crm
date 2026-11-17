package com.hospital.crm.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 对账批次：按渠道与账期汇总本地支付流水与渠道账单的笔数与金额，
 * 存在差异时状态为 DIFF，差异明细见 ReconciliationDiff。
 */
@Entity
@Table(name = "crm_reconciliation_batch")
public class ReconciliationBatch {

    @Id
    private UUID id;

    @Column(name = "batch_no", nullable = false, length = 40)
    private String batchNo;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "reconcile_date", nullable = false)
    private LocalDate reconcileDate;

    @Column(name = "local_count", nullable = false)
    private int localCount;

    @Column(name = "channel_count", nullable = false)
    private int channelCount;

    @Column(name = "local_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal localAmount = BigDecimal.ZERO;

    @Column(name = "channel_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal channelAmount = BigDecimal.ZERO;

    @Column(name = "diff_count", nullable = false)
    private int diffCount;

    @Column(nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "run_by", length = 100)
    private String runBy;

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

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public LocalDate getReconcileDate() {
        return reconcileDate;
    }

    public void setReconcileDate(LocalDate reconcileDate) {
        this.reconcileDate = reconcileDate;
    }

    public int getLocalCount() {
        return localCount;
    }

    public void setLocalCount(int localCount) {
        this.localCount = localCount;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }

    public BigDecimal getLocalAmount() {
        return localAmount;
    }

    public void setLocalAmount(BigDecimal localAmount) {
        this.localAmount = localAmount;
    }

    public BigDecimal getChannelAmount() {
        return channelAmount;
    }

    public void setChannelAmount(BigDecimal channelAmount) {
        this.channelAmount = channelAmount;
    }

    public int getDiffCount() {
        return diffCount;
    }

    public void setDiffCount(int diffCount) {
        this.diffCount = diffCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRunBy() {
        return runBy;
    }

    public void setRunBy(String runBy) {
        this.runBy = runBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
