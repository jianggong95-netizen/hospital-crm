package com.hospital.crm.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 会员资产账本。余额与积分每次变动都必须写一条，记录变动前后的余额与积分快照，
 * 便于对账与权益追溯（不做物理删除）。
 */
@Entity
@Table(name = "crm_member_ledger")
public class MemberLedger {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "ledger_type", nullable = false, length = 24)
    private String ledgerType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "points_delta", nullable = false)
    private Long pointsDelta = 0L;

    @Column(name = "balance_after", precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "points_after")
    private Long pointsAfter;

    @Column(name = "related_order_id")
    private UUID relatedOrderId;

    @Column(name = "biz_no", length = 64)
    private String bizNo;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "operator_id")
    private UUID operatorId;

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

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getLedgerType() {
        return ledgerType;
    }

    public void setLedgerType(String ledgerType) {
        this.ledgerType = ledgerType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getPointsDelta() {
        return pointsDelta;
    }

    public void setPointsDelta(Long pointsDelta) {
        this.pointsDelta = pointsDelta;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Long getPointsAfter() {
        return pointsAfter;
    }

    public void setPointsAfter(Long pointsAfter) {
        this.pointsAfter = pointsAfter;
    }

    public UUID getRelatedOrderId() {
        return relatedOrderId;
    }

    public void setRelatedOrderId(UUID relatedOrderId) {
        this.relatedOrderId = relatedOrderId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(UUID operatorId) {
        this.operatorId = operatorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
