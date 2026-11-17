package com.hospital.crm.member.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class MemberDtos {

    private MemberDtos() {
    }

    public record LevelView(
            String levelCode,
            String levelName,
            Long minPoints,
            BigDecimal discountRate,
            Integer sortNo
    ) {
    }

    public record AccountView(
            UUID id,
            String accountNo,
            UUID customerId,
            String customerName,
            String memberLevel,
            String levelName,
            BigDecimal discountRate,
            BigDecimal balance,
            BigDecimal frozenBalance,
            Long points,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CardSummaryView(
            UUID id,
            String cardNo,
            String packageCode,
            String packageName,
            Integer totalTimes,
            Integer usedTimes,
            Integer remainingTimes,
            LocalDate validFrom,
            LocalDate validTo,
            String status
    ) {
    }

    public record AccountDetailView(
            AccountView account,
            List<CardSummaryView> cards
    ) {
    }

    public record CreateAccountRequest(
            @NotNull(message = "客户不能为空") UUID customerId
    ) {
    }

    public record RechargeRequest(
            @NotNull(message = "账户不能为空") UUID accountId,
            @NotNull(message = "充值金额不能为空")
            @DecimalMin(value = "0.01", message = "充值金额必须大于 0") BigDecimal amount,
            UUID orderId,
            @Size(max = 200, message = "备注不能超过 200 字") String reason
    ) {
    }

    public record ConsumeRequest(
            @NotNull(message = "客户不能为空") UUID customerId,
            @NotNull(message = "消费金额不能为空")
            @DecimalMin(value = "0.01", message = "消费金额必须大于 0") BigDecimal amount,
            @Size(max = 32, message = "业务类型不能超过 32 字") String bizType,
            @Size(max = 64, message = "业务单号不能超过 64 字") String bizNo,
            Boolean allowShared
    ) {
    }

    public record PointsAdjustRequest(
            @NotNull(message = "账户不能为空") UUID accountId,
            @NotNull(message = "积分变动不能为空") Long points,
            @Size(max = 200, message = "备注不能超过 200 字") String reason
    ) {
    }

    public record RefundReversalRequest(
            @NotNull(message = "订单不能为空") UUID orderId,
            @NotNull(message = "退款金额不能为空")
            @DecimalMin(value = "0.01", message = "退款金额必须大于 0") BigDecimal refundAmount
    ) {
    }

    public record RefundReversalResult(
            UUID orderId,
            BigDecimal balanceReversed,
            Long pointsReversed,
            int cardsVoided,
            int cardTimesReturned
    ) {
    }

    public record LedgerView(
            UUID id,
            String ledgerType,
            BigDecimal amount,
            Long pointsDelta,
            BigDecimal balanceAfter,
            Long pointsAfter,
            UUID relatedOrderId,
            String bizNo,
            String reason,
            Instant createdAt
    ) {
    }

    public record PackageView(
            String packageCode,
            String packageName,
            String packageType,
            BigDecimal price,
            Integer times,
            BigDecimal balanceAmount,
            Long pointsAmount,
            Integer validDays,
            String status,
            String description
    ) {
    }

    public record PackageCreateRequest(
            @NotBlank(message = "套餐编码不能为空")
            @Pattern(regexp = "^[A-Za-z0-9_]{2,32}$", message = "套餐编码只能包含字母、数字与下划线，长度 2-32")
            String packageCode,
            @NotBlank(message = "套餐名称不能为空") @Size(max = 120, message = "套餐名称不能超过 120 字") String packageName,
            @NotBlank(message = "套餐类型不能为空")
            @Pattern(regexp = "TIMES|BALANCE|POINTS", message = "套餐类型不合法") String packageType,
            @NotNull(message = "套餐价格不能为空")
            @DecimalMin(value = "0.00", message = "套餐价格不能为负") BigDecimal price,
            @Min(value = 1, message = "次数必须大于 0") Integer times,
            BigDecimal balanceAmount,
            Long pointsAmount,
            @Min(value = 1, message = "有效期必须大于 0") Integer validDays,
            @Size(max = 500, message = "描述不能超过 500 字") String description
    ) {
    }

    public record PackageUpdateRequest(
            @NotBlank(message = "套餐名称不能为空") @Size(max = 120, message = "套餐名称不能超过 120 字") String packageName,
            @NotNull(message = "套餐价格不能为空")
            @DecimalMin(value = "0.00", message = "套餐价格不能为负") BigDecimal price,
            @Min(value = 1, message = "次数必须大于 0") Integer times,
            BigDecimal balanceAmount,
            Long pointsAmount,
            @Min(value = 1, message = "有效期必须大于 0") Integer validDays,
            @Size(max = 500, message = "描述不能超过 500 字") String description
    ) {
    }

    public record PackageStatusRequest(
            @NotBlank(message = "状态不能为空")
            @Pattern(regexp = "active|disabled", message = "套餐状态不合法") String status
    ) {
    }

    public record PurchaseRequest(
            @NotNull(message = "客户不能为空") UUID customerId,
            UUID orderId
    ) {
    }

    public record PurchaseResult(
            AccountView account,
            CardSummaryView card,
            BigDecimal balanceAdded,
            Long pointsAdded
    ) {
    }

    public record CardView(
            UUID id,
            String cardNo,
            UUID customerId,
            String customerName,
            String packageCode,
            String packageName,
            Integer totalTimes,
            Integer usedTimes,
            Integer remainingTimes,
            LocalDate validFrom,
            LocalDate validTo,
            String status,
            UUID sourceOrderId
    ) {
    }

    public record CardUsageView(
            UUID id,
            Integer times,
            String bizType,
            String bizNo,
            String status,
            String remark,
            Instant createdAt,
            Instant refundedAt
    ) {
    }

    public record CardDetailView(
            CardView card,
            List<CardUsageView> usages
    ) {
    }

    public record CardConsumeRequest(
            @NotNull(message = "扣次次数不能为空") @Min(value = 1, message = "扣次次数必须大于 0") Integer times,
            @Size(max = 32, message = "业务类型不能超过 32 字") String bizType,
            @Size(max = 64, message = "业务单号不能超过 64 字") String bizNo,
            @Size(max = 500, message = "备注不能超过 500 字") String remark
    ) {
    }

    public record CardRefundRequest(
            @NotNull(message = "返还次数不能为空") @Min(value = 1, message = "返还次数必须大于 0") Integer times,
            UUID usageId,
            @Size(max = 500, message = "原因不能超过 500 字") String reason
    ) {
    }

    public record ShareView(
            UUID id,
            UUID ownerCustomerId,
            String ownerCustomerName,
            UUID beneficiaryCustomerId,
            String beneficiaryCustomerName,
            String resourceType,
            UUID resourceId,
            Boolean canConsume,
            Instant validFrom,
            Instant validTo,
            String status,
            Instant createdAt
    ) {
    }

    public record ShareCreateRequest(
            @NotNull(message = "授权人不能为空") UUID ownerCustomerId,
            @NotNull(message = "使用人不能为空") UUID beneficiaryCustomerId,
            @NotBlank(message = "资源类型不能为空")
            @Pattern(regexp = "BALANCE|POINTS|CARD", message = "资源类型不合法") String resourceType,
            UUID resourceId,
            Instant validTo,
            Boolean canConsume
    ) {
    }
}
