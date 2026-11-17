package com.hospital.crm.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 对账差异明细：LOCAL_ONLY 本地有渠道无、CHANNEL_ONLY 渠道有本地无、
 * AMOUNT_MISMATCH 两边都有金额不等。
 */
@Entity
@Table(name = "crm_reconciliation_diff")
public class ReconciliationDiff {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "diff_type", nullable = false, length = 20)
    private String diffType;

    @Column(name = "payment_no", length = 40)
    private String paymentNo;

    @Column(name = "channel_trade_no", length = 100)
    private String channelTradeNo;

    @Column(name = "local_amount", precision = 18, scale = 2)
    private BigDecimal localAmount;

    @Column(name = "channel_amount", precision = 18, scale = 2)
    private BigDecimal channelAmount;

    @Column(nullable = false)
    private boolean handled;

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

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getDiffType() {
        return diffType;
    }

    public void setDiffType(String diffType) {
        this.diffType = diffType;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public void setPaymentNo(String paymentNo) {
        this.paymentNo = paymentNo;
    }

    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
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

    public boolean isHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
