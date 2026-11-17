package com.hospital.crm.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 会员套餐目录：TIMES 生成次卡，BALANCE 入余额，POINTS 入积分。
 * 仅 status=active 的套餐可购买。
 */
@Entity
@Table(name = "crm_member_package")
public class MemberPackage {

    @Id
    @Column(name = "package_code", length = 32)
    private String packageCode;

    @Column(name = "package_name", nullable = false, length = 120)
    private String packageName;

    @Column(name = "package_type", nullable = false, length = 20)
    private String packageType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    private Integer times;

    @Column(name = "balance_amount", precision = 18, scale = 2)
    private BigDecimal balanceAmount;

    @Column(name = "points_amount")
    private Long pointsAmount;

    @Column(name = "valid_days", nullable = false)
    private Integer validDays = 365;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getPackageCode() {
        return packageCode;
    }

    public void setPackageCode(String packageCode) {
        this.packageCode = packageCode;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getTimes() {
        return times;
    }

    public void setTimes(Integer times) {
        this.times = times;
    }

    public BigDecimal getBalanceAmount() {
        return balanceAmount;
    }

    public void setBalanceAmount(BigDecimal balanceAmount) {
        this.balanceAmount = balanceAmount;
    }

    public Long getPointsAmount() {
        return pointsAmount;
    }

    public void setPointsAmount(Long pointsAmount) {
        this.pointsAmount = pointsAmount;
    }

    public Integer getValidDays() {
        return validDays;
    }

    public void setValidDays(Integer validDays) {
        this.validDays = validDays;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
