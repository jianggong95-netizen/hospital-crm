package com.hospital.crm.insurance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商保中心接口契约。金额统一使用 BigDecimal，视图不含任何敏感明文。
 */
public final class InsuranceDtos {

    private InsuranceDtos() {
    }

    // ------------------------------------------------------------------
    // 保险公司
    // ------------------------------------------------------------------
    public record CompanyView(
            UUID id,
            String companyCode,
            String companyName,
            String contactName,
            String contactPhoneMasked,
            String contactEmail,
            String settlementType,
            String status,
            String remark,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateCompanyRequest(
            @NotBlank(message = "保险公司编码不能为空")
            @Size(max = 32, message = "保险公司编码长度不能超过 32")
            String companyCode,

            @NotBlank(message = "保险公司名称不能为空")
            @Size(max = 120, message = "保险公司名称长度不能超过 120")
            String companyName,

            @Size(max = 60, message = "联系人姓名长度不能超过 60")
            String contactName,

            @Size(max = 32, message = "联系电话长度不合法")
            String contactPhone,

            @Size(max = 160, message = "联系邮箱长度不能超过 160")
            String contactEmail,

            @Pattern(regexp = "api|email|manual", message = "结算方式取值不合法")
            String settlementType,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record UpdateCompanyRequest(
            @Size(max = 120, message = "保险公司名称长度不能超过 120")
            String companyName,

            @Size(max = 60, message = "联系人姓名长度不能超过 60")
            String contactName,

            @Size(max = 32, message = "联系电话长度不合法")
            String contactPhone,

            @Size(max = 160, message = "联系邮箱长度不能超过 160")
            String contactEmail,

            @Pattern(regexp = "api|email|manual", message = "结算方式取值不合法")
            String settlementType,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record StatusRequest(
            @NotBlank(message = "状态不能为空")
            String status
    ) {
    }

    // ------------------------------------------------------------------
    // 保单
    // ------------------------------------------------------------------
    public record PolicyView(
            UUID id,
            String policyNo,
            UUID companyId,
            String companyName,
            UUID customerId,
            String customerName,
            String customerNo,
            String holderName,
            String insuredName,
            String relation,
            LocalDate validFrom,
            LocalDate validTo,
            BigDecimal coverageAmount,
            String status,
            String remark,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreatePolicyRequest(
            @NotBlank(message = "保单号不能为空")
            @Size(max = 64, message = "保单号长度不能超过 64")
            String policyNo,

            @NotNull(message = "保险公司不能为空")
            UUID companyId,

            @NotNull(message = "客户不能为空")
            UUID customerId,

            @Size(max = 100, message = "投保人姓名长度不能超过 100")
            String holderName,

            @Size(max = 100, message = "被保人姓名长度不能超过 100")
            String insuredName,

            @Pattern(regexp = "self|spouse|child|parent|other", message = "与被保人关系取值不合法")
            String relation,

            LocalDate validFrom,

            LocalDate validTo,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "保障额度不能为负数")
            BigDecimal coverageAmount,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    // ------------------------------------------------------------------
    // 商保案件
    // ------------------------------------------------------------------
    public record CaseView(
            UUID id,
            String caseNo,
            UUID companyId,
            String companyName,
            UUID policyId,
            String policyNo,
            UUID customerId,
            String customerName,
            String customerNo,
            String caseType,
            String serviceDesc,
            String state,
            BigDecimal hospitalReceivable,
            BigDecimal patientPay,
            BigDecimal estimatedPayout,
            BigDecimal actualReceived,
            BigDecimal quotationAmount,
            Instant quotationAt,
            String preauthNo,
            BigDecimal preauthAmount,
            Instant preauthAt,
            String claimNo,
            Instant claimSubmittedAt,
            String claimDecision,
            BigDecimal approvedAmount,
            String decisionReason,
            Instant decidedAt,
            UUID handlerUserId,
            String handlerName,
            Instant reconciledAt,
            BigDecimal reconciliationDiff,
            String remark,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CaseDetailView(
            @com.fasterxml.jackson.annotation.JsonProperty("case") CaseView caseView,
            List<MaterialView> materials,
            List<EmailView> emails,
            List<EventView> events
    ) {
    }

    public record CreateCaseRequest(
            @NotNull(message = "保单不能为空")
            UUID policyId,

            @Pattern(regexp = "outpatient|inpatient|physical_exam|other", message = "案件类型取值不合法")
            String caseType,

            @Size(max = 500, message = "服务内容长度不能超过 500")
            String serviceDesc,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "医院应收不能为负数")
            BigDecimal hospitalReceivable,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "患者自付不能为负数")
            BigDecimal patientPay,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }

    public record TransitionRequest(
            @NotBlank(message = "目标状态不能为空")
            String targetState,

            @Size(max = 500, message = "处理说明长度不能超过 500")
            String note,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "询价金额不能为负数")
            BigDecimal quotationAmount,

            @Size(max = 64, message = "预授权号长度不能超过 64")
            String preauthNo,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "预授权金额不能为负数")
            BigDecimal preauthAmount,

            @Size(max = 64, message = "理赔号长度不能超过 64")
            String claimNo,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "医院应收不能为负数")
            BigDecimal hospitalReceivable,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "患者自付不能为负数")
            BigDecimal patientPay,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "核定赔付不能为负数")
            BigDecimal approvedAmount,

            @jakarta.validation.constraints.DecimalMin(value = "0", message = "实际到账不能为负数")
            BigDecimal actualReceived
    ) {
    }

    public record AssignRequest(
            @NotNull(message = "处理人不能为空")
            UUID handlerUserId
    ) {
    }

    // ------------------------------------------------------------------
    // 案件材料
    // ------------------------------------------------------------------
    public record MaterialView(
            UUID id,
            UUID caseId,
            String materialType,
            String fileName,
            String fileKey,
            String fileHash,
            Long fileSize,
            String source,
            String status,
            String note,
            UUID uploadedBy,
            String uploadedByName,
            Instant createdAt
    ) {
    }

    public record MaterialCreateRequest(
            @NotBlank(message = "材料类型不能为空")
            @Pattern(regexp = "diagnosis|invoice|detail|prescription|report|other", message = "材料类型取值不合法")
            String materialType,

            @NotBlank(message = "文件名不能为空")
            @Size(max = 200, message = "文件名长度不能超过 200")
            String fileName,

            @NotBlank(message = "文件存储键不能为空")
            @Size(max = 255, message = "文件存储键长度不能超过 255")
            String fileKey,

            @NotBlank(message = "文件哈希不能为空")
            @Pattern(regexp = "[0-9a-fA-F]{64}", message = "文件哈希必须为 64 位十六进制")
            String fileHash,

            Long fileSize,

            @Size(max = 500, message = "备注长度不能超过 500")
            String note
    ) {
    }

    // ------------------------------------------------------------------
    // 邮件
    // ------------------------------------------------------------------
    public record EmailView(
            UUID id,
            UUID caseId,
            String direction,
            String messageId,
            String subject,
            String fromAddress,
            String toAddress,
            String bodyExcerpt,
            Instant sentAt,
            Instant receivedAt,
            int attachmentCount,
            UUID operatorUserId,
            String operatorName,
            String status,
            Instant createdAt,
            List<AttachmentView> attachments
    ) {
    }

    public record AttachmentView(
            UUID id,
            String fileName,
            String fileHash,
            Long fileSize,
            UUID materialId,
            Instant createdAt
    ) {
    }

    public record EmailCreateRequest(
            @NotBlank(message = "邮件方向不能为空")
            @Pattern(regexp = "inbound|outbound", message = "邮件方向取值不合法")
            String direction,

            @Size(max = 255, message = "邮件 Message-ID 长度不能超过 255")
            String messageId,

            @NotBlank(message = "邮件主题不能为空")
            @Size(max = 300, message = "邮件主题长度不能超过 300")
            String subject,

            @Size(max = 160, message = "发件人地址长度不能超过 160")
            String fromAddress,

            @Size(max = 160, message = "收件人地址长度不能超过 160")
            String toAddress,

            @Size(max = 1000, message = "正文摘要长度不能超过 1000")
            String bodyExcerpt,

            Instant sentAt,

            Instant receivedAt,

            List<AttachmentCreateRequest> attachments
    ) {
    }

    public record AttachmentCreateRequest(
            @NotBlank(message = "附件文件名不能为空")
            @Size(max = 200, message = "附件文件名长度不能超过 200")
            String fileName,

            @NotBlank(message = "附件哈希不能为空")
            @Pattern(regexp = "[0-9a-fA-F]{64}", message = "附件哈希必须为 64 位十六进制")
            String fileHash,

            Long fileSize
    ) {
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------
    public record EventView(
            UUID id,
            String eventType,
            String fromState,
            String toState,
            UUID operatorUserId,
            String operatorName,
            Map<String, Object> detail,
            Instant createdAt
    ) {
    }

    // ------------------------------------------------------------------
    // 对账
    // ------------------------------------------------------------------
    public record ReconciliationView(
            UUID id,
            UUID companyId,
            String companyName,
            String period,
            int caseCount,
            BigDecimal receivableAmount,
            BigDecimal receivedAmount,
            BigDecimal differenceAmount,
            String status,
            UUID confirmedBy,
            String confirmedByName,
            Instant confirmedAt,
            String remark,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ReconciliationCreateRequest(
            @NotNull(message = "保险公司不能为空")
            UUID companyId,

            @NotBlank(message = "对账账期不能为空")
            @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "对账账期格式必须为 YYYY-MM")
            String period,

            @Size(max = 500, message = "备注长度不能超过 500")
            String remark
    ) {
    }
}
