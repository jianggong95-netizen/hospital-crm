package com.hospital.crm.order.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    // ------------------------------------------------------------------
    // 统一订单
    // ------------------------------------------------------------------

    public record OrderCreateRequest(
            @NotNull(message = "客户不能为空")
            UUID customerId,
            @NotBlank(message = "订单类型不能为空")
            @Size(max = 32, message = "订单类型长度不能超过 32")
            String orderType,
            @NotEmpty(message = "订单明细不能为空")
            @Size(max = 50, message = "订单明细不能超过 50 行")
            List<ItemLine> items,
            BigDecimal discountAmount,
            @Size(max = 100, message = "幂等键长度不能超过 100")
            String idempotencyKey,
            @Size(max = 32, message = "来源渠道长度不能超过 32")
            String sourceChannel
    ) {
    }

    public record ItemLine(
            @NotBlank(message = "明细编码不能为空")
            @Size(max = 40, message = "明细编码长度不能超过 40")
            String itemCode,
            @NotBlank(message = "明细名称不能为空")
            @Size(max = 120, message = "明细名称长度不能超过 120")
            String itemName,
            @NotNull(message = "数量不能为空")
            BigDecimal quantity,
            @NotNull(message = "单价不能为空")
            BigDecimal unitPrice
    ) {
    }

    public record ReasonRequest(
            @NotBlank(message = "原因不能为空")
            @Size(max = 500, message = "原因长度不能超过 500")
            String reason
    ) {
    }

    public record OrderSummary(
            UUID id,
            String orderNo,
            UUID customerId,
            String customerName,
            String orderType,
            String status,
            String currency,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal payableAmount,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            String sourceChannel,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record OrderItemView(
            UUID id,
            String itemCode,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal amount
    ) {
    }

    public record OrderDetail(
            OrderSummary summary,
            String cancelReason,
            List<OrderItemView> items,
            List<PaymentView> payments,
            List<RefundView> refunds
    ) {
    }

    // ------------------------------------------------------------------
    // 支付单与渠道回调
    // ------------------------------------------------------------------

    public record PaymentCreateRequest(
            @NotBlank(message = "支付渠道不能为空")
            @Pattern(regexp = "wechat|alipay|bank|cash|other", message = "支付渠道取值不合法")
            String channel,
            @NotNull(message = "支付金额不能为空")
            @DecimalMin(value = "0.01", message = "支付金额必须大于 0")
            BigDecimal amount,
            @NotBlank(message = "幂等键不能为空")
            @Size(max = 100, message = "幂等键长度不能超过 100")
            String idempotencyKey
    ) {
    }

    public record CallbackRequest(
            @NotBlank(message = "支付单号不能为空")
            String paymentNo,
            String channelTradeNo,
            @NotNull(message = "回调金额不能为空")
            BigDecimal amount,
            @NotNull(message = "回调结果不能为空")
            Boolean success,
            @NotBlank(message = "回调签名不能为空")
            String signature
    ) {
    }

    public record CallbackResult(
            String paymentNo,
            String status,
            boolean replayed
    ) {
    }

    public record PaymentView(
            UUID id,
            String paymentNo,
            UUID orderId,
            String orderNo,
            String channel,
            String channelTradeNo,
            BigDecimal amount,
            BigDecimal refundedAmount,
            String status,
            Instant paidAt,
            Instant createdAt
    ) {
    }

    // ------------------------------------------------------------------
    // 退款
    // ------------------------------------------------------------------

    public record RefundApplyRequest(
            @NotNull(message = "退款金额不能为空")
            @DecimalMin(value = "0.01", message = "退款金额必须大于 0")
            BigDecimal amount,
            @NotBlank(message = "退款原因不能为空")
            @Size(max = 1000, message = "退款原因长度不能超过 1000")
            String reason
    ) {
    }

    public record RefundView(
            UUID id,
            String refundNo,
            UUID paymentId,
            UUID orderId,
            BigDecimal amount,
            String reason,
            String status,
            String channelRefundNo,
            String reviewedBy,
            Instant reviewedAt,
            String rejectReason,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    // ------------------------------------------------------------------
    // 渠道对账
    // ------------------------------------------------------------------

    public record ReconRunRequest(
            @NotBlank(message = "对账渠道不能为空")
            @Pattern(regexp = "wechat|alipay|bank|cash|other", message = "对账渠道取值不合法")
            String channel,
            @NotNull(message = "对账日期不能为空")
            LocalDate reconcileDate
    ) {
    }

    public record ReconciliationView(
            UUID id,
            String batchNo,
            String channel,
            LocalDate reconcileDate,
            int localCount,
            int channelCount,
            BigDecimal localAmount,
            BigDecimal channelAmount,
            int diffCount,
            String status,
            String runBy,
            Instant createdAt
    ) {
    }

    public record ReconciliationDiffView(
            UUID id,
            String diffType,
            String paymentNo,
            String channelTradeNo,
            BigDecimal localAmount,
            BigDecimal channelAmount,
            boolean handled
    ) {
    }

    public record ReconciliationDetail(
            ReconciliationView batch,
            List<ReconciliationDiffView> diffs
    ) {
    }

    // ------------------------------------------------------------------
    // 发票
    // ------------------------------------------------------------------

    public record InvoiceApplyRequest(
            @NotBlank(message = "发票抬头不能为空")
            @Size(max = 200, message = "发票抬头长度不能超过 200")
            String title,
            @Size(max = 64, message = "税号长度不能超过 64")
            String taxNo,
            @NotNull(message = "开票金额不能为空")
            @DecimalMin(value = "0.01", message = "开票金额必须大于 0")
            BigDecimal amount,
            @Pattern(regexp = "general|special", message = "发票类型取值不合法")
            String invoiceType
    ) {
    }

    public record InvoiceView(
            UUID id,
            String invoiceNo,
            UUID orderId,
            String orderNo,
            UUID customerId,
            String title,
            String taxNo,
            BigDecimal amount,
            String invoiceType,
            String status,
            String provider,
            String fileRef,
            String appliedBy,
            Instant appliedAt,
            Instant issuedAt,
            String voidReason
    ) {
    }
}
