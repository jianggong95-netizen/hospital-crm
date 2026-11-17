package com.hospital.crm.order.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.application.gateway.PaymentGateway;
import com.hospital.crm.order.domain.Order;
import com.hospital.crm.order.domain.OrderRepository;
import com.hospital.crm.order.domain.Payment;
import com.hospital.crm.order.domain.PaymentRepository;
import com.hospital.crm.order.domain.Refund;
import com.hospital.crm.order.domain.RefundRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 退款服务：申请→审核→渠道退款→原子累计支付单与订单退款金额，
 * 支持部分退款；退款金额不得超过支付单可退余额（amount−refunded_amount），
 * 并发超退由条件更新兜底拒绝。
 */
@Service
public class RefundService {

    private static final String RESOURCE = "CRM_REFUND";

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final AuditService auditService;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository,
                         OrderRepository orderRepository,
                         PaymentGateway paymentGateway,
                         AuditService auditService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.auditService = auditService;
    }

    @Transactional
    public OrderDtos.RefundView apply(UUID paymentId, OrderDtos.RefundApplyRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "支付单不存在"));
        if (!"SUCCESS".equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅支付成功的单据可退款");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "退款金额必须大于 0");
        }
        BigDecimal refundable = payment.getAmount().subtract(payment.getRefundedAmount());
        if (amount.compareTo(refundable) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "退款金额超过可退余额");
        }

        Refund refund = new Refund();
        refund.setRefundNo("R" + refundRepository.nextRefundNo());
        refund.setPaymentId(paymentId);
        refund.setOrderId(payment.getOrderId());
        refund.setAmount(amount);
        refund.setReason(request.reason().trim());
        refund.setStatus("PENDING");
        refund = refundRepository.save(refund);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("refundNo", refund.getRefundNo());
        audit.put("paymentNo", payment.getPaymentNo());
        audit.put("amount", amount.toPlainString());
        auditService.record("REFUND_APPLY", RESOURCE, refund.getId().toString(), audit);

        return toView(refund);
    }

    /**
     * 审核通过并执行渠道退款：stub 网关恒成功；网关失败置 FAILED 且不累计金额。
     * 成功后条件更新 APPROVED→SUCCESS，再原子累计支付单与订单退款金额，
     * 任一条件更新返回 0 行即并发超退，整体回滚拒绝。
     */
    @Transactional
    public OrderDtos.RefundView approve(UUID refundId) {
        Refund refund = load(refundId);
        if (!"PENDING".equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待审核的退款申请可以审核");
        }
        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "支付单不存在"));

        refund.setStatus("APPROVED");
        refund.setReviewedBy(CurrentUser.principal().username());
        refund.setReviewedAt(Instant.now());
        refund = refundRepository.save(refund);

        boolean gatewaySuccess = paymentGateway.executeRefund(refund.getRefundNo(), payment.getPaymentNo(),
                payment.getChannel(), refund.getAmount());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("refundNo", refund.getRefundNo());
        audit.put("paymentNo", payment.getPaymentNo());
        audit.put("amount", refund.getAmount().toPlainString());

        if (!gatewaySuccess) {
            refund.setStatus("FAILED");
            refund = refundRepository.save(refund);
            audit.put("result", "FAILED");
            auditService.record("REFUND_APPROVE", RESOURCE, refundId.toString(), audit);
            return toView(refund);
        }

        Instant now = Instant.now();
        int marked = refundRepository.markSucceeded(refundId, "RCF-" + refund.getRefundNo(), now);
        if (marked == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "退款单状态已变更，请刷新后重试");
        }
        int paymentUpdated = paymentRepository.accumulateRefunded(refund.getPaymentId(), refund.getAmount());
        if (paymentUpdated == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "并发退款超出支付单可退余额");
        }
        int orderUpdated = orderRepository.accumulateRefunded(refund.getOrderId(), refund.getAmount(), now);
        if (orderUpdated == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "并发退款超出订单实付金额");
        }

        // 条件更新已在库内置 SUCCESS，这里同步内存状态用于返回视图
        refund.setStatus("SUCCESS");
        refund.setCompletedAt(now);
        refund.setChannelRefundNo("RCF-" + refund.getRefundNo());
        audit.put("result", "SUCCESS");
        auditService.record("REFUND_APPROVE", RESOURCE, refundId.toString(), audit);

        Order order = orderRepository.findById(refund.getOrderId()).orElse(null);
        Map<String, Object> successAudit = new LinkedHashMap<>();
        successAudit.put("refundNo", refund.getRefundNo());
        successAudit.put("orderNo", order == null ? null : order.getOrderNo());
        auditService.record("REFUND_SUCCESS", RESOURCE, refundId.toString(), successAudit);
        return toView(refund);
    }

    @Transactional
    public OrderDtos.RefundView reject(UUID refundId, OrderDtos.ReasonRequest request) {
        Refund refund = load(refundId);
        if (!"PENDING".equals(refund.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待审核的退款申请可以驳回");
        }
        refund.setStatus("REJECTED");
        refund.setReviewedBy(CurrentUser.principal().username());
        refund.setReviewedAt(Instant.now());
        refund.setRejectReason(request.reason().trim());
        refund = refundRepository.save(refund);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("refundNo", refund.getRefundNo());
        audit.put("reason", refund.getRejectReason());
        auditService.record("REFUND_REJECT", RESOURCE, refundId.toString(), audit);

        return toView(refund);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.RefundView> list(String status, UUID orderId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Refund> result = refundRepository.findAll(buildSpecification(status, orderId), pageable);
        List<OrderDtos.RefundView> items = result.getContent().stream().map(RefundService::toView).toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public OrderDtos.RefundView detail(UUID refundId) {
        return toView(load(refundId));
    }

    @Transactional(readOnly = true)
    public Refund load(UUID refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "退款单不存在"));
    }

    static OrderDtos.RefundView toView(Refund refund) {
        return new OrderDtos.RefundView(
                refund.getId(),
                refund.getRefundNo(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getChannelRefundNo(),
                refund.getReviewedBy(),
                refund.getReviewedAt(),
                refund.getRejectReason(),
                refund.getCreatedAt(),
                refund.getCompletedAt());
    }

    private Specification<Refund> buildSpecification(String status, UUID orderId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (orderId != null) {
                predicates.add(cb.equal(root.get("orderId"), orderId));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
