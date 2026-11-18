package com.hospital.crm.insurance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 商保案件。状态机：
 * registered → eligibility_check → quotation → pre_authorization → service_done → claim_submitted
 * → supplement_required | under_review → approved | partially_approved | rejected → reconciled。
 *
 * 金额口径：hospital_receivable 医院应收、patient_pay 患者自付、estimated_payout 预计赔付
 * （仅在审批通过时写入）、actual_received 实际到账（仅在对账时写入），后两者互不覆盖。
 */
@Entity
@Table(name = "crm_insurance_case")
public class InsuranceCase {

    @Id
    private UUID id;

    @Column(name = "case_no", nullable = false, length = 40)
    private String caseNo;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "case_type", nullable = false, length = 20)
    private String caseType = "other";

    @Column(name = "service_desc", length = 500)
    private String serviceDesc;

    @Column(nullable = false, length = 24)
    private String state = "registered";

    @Column(name = "hospital_receivable", nullable = false)
    private BigDecimal hospitalReceivable = BigDecimal.ZERO;

    @Column(name = "patient_pay", nullable = false)
    private BigDecimal patientPay = BigDecimal.ZERO;

    @Column(name = "estimated_payout", nullable = false)
    private BigDecimal estimatedPayout = BigDecimal.ZERO;

    @Column(name = "actual_received", nullable = false)
    private BigDecimal actualReceived = BigDecimal.ZERO;

    @Column(name = "quotation_amount")
    private BigDecimal quotationAmount;

    @Column(name = "quotation_at")
    private Instant quotationAt;

    @Column(name = "preauth_no", length = 64)
    private String preauthNo;

    @Column(name = "preauth_amount")
    private BigDecimal preauthAmount;

    @Column(name = "preauth_at")
    private Instant preauthAt;

    @Column(name = "claim_no", length = 64)
    private String claimNo;

    @Column(name = "claim_submitted_at")
    private Instant claimSubmittedAt;

    @Column(name = "claim_decision", length = 24)
    private String claimDecision;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "handler_user_id")
    private UUID handlerUserId;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciliation_diff")
    private BigDecimal reconciliationDiff;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_by")
    private UUID createdBy;

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
        if (hospitalReceivable == null) {
            hospitalReceivable = BigDecimal.ZERO;
        }
        if (patientPay == null) {
            patientPay = BigDecimal.ZERO;
        }
        if (estimatedPayout == null) {
            estimatedPayout = BigDecimal.ZERO;
        }
        if (actualReceived == null) {
            actualReceived = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(String caseNo) {
        this.caseNo = caseNo;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getCaseType() {
        return caseType;
    }

    public void setCaseType(String caseType) {
        this.caseType = caseType;
    }

    public String getServiceDesc() {
        return serviceDesc;
    }

    public void setServiceDesc(String serviceDesc) {
        this.serviceDesc = serviceDesc;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public BigDecimal getHospitalReceivable() {
        return hospitalReceivable;
    }

    public void setHospitalReceivable(BigDecimal hospitalReceivable) {
        this.hospitalReceivable = hospitalReceivable;
    }

    public BigDecimal getPatientPay() {
        return patientPay;
    }

    public void setPatientPay(BigDecimal patientPay) {
        this.patientPay = patientPay;
    }

    public BigDecimal getEstimatedPayout() {
        return estimatedPayout;
    }

    public void setEstimatedPayout(BigDecimal estimatedPayout) {
        this.estimatedPayout = estimatedPayout;
    }

    public BigDecimal getActualReceived() {
        return actualReceived;
    }

    public void setActualReceived(BigDecimal actualReceived) {
        this.actualReceived = actualReceived;
    }

    public BigDecimal getQuotationAmount() {
        return quotationAmount;
    }

    public void setQuotationAmount(BigDecimal quotationAmount) {
        this.quotationAmount = quotationAmount;
    }

    public Instant getQuotationAt() {
        return quotationAt;
    }

    public void setQuotationAt(Instant quotationAt) {
        this.quotationAt = quotationAt;
    }

    public String getPreauthNo() {
        return preauthNo;
    }

    public void setPreauthNo(String preauthNo) {
        this.preauthNo = preauthNo;
    }

    public BigDecimal getPreauthAmount() {
        return preauthAmount;
    }

    public void setPreauthAmount(BigDecimal preauthAmount) {
        this.preauthAmount = preauthAmount;
    }

    public Instant getPreauthAt() {
        return preauthAt;
    }

    public void setPreauthAt(Instant preauthAt) {
        this.preauthAt = preauthAt;
    }

    public String getClaimNo() {
        return claimNo;
    }

    public void setClaimNo(String claimNo) {
        this.claimNo = claimNo;
    }

    public Instant getClaimSubmittedAt() {
        return claimSubmittedAt;
    }

    public void setClaimSubmittedAt(Instant claimSubmittedAt) {
        this.claimSubmittedAt = claimSubmittedAt;
    }

    public String getClaimDecision() {
        return claimDecision;
    }

    public void setClaimDecision(String claimDecision) {
        this.claimDecision = claimDecision;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public UUID getHandlerUserId() {
        return handlerUserId;
    }

    public void setHandlerUserId(UUID handlerUserId) {
        this.handlerUserId = handlerUserId;
    }

    public Instant getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(Instant reconciledAt) {
        this.reconciledAt = reconciledAt;
    }

    public BigDecimal getReconciliationDiff() {
        return reconciliationDiff;
    }

    public void setReconciliationDiff(BigDecimal reconciliationDiff) {
        this.reconciliationDiff = reconciliationDiff;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
