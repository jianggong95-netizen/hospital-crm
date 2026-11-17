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
import com.hospital.crm.system.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付服务：发起支付走 PaymentGateway 抽象（stub 不接真实渠道），
 * 渠道回调先 HMAC-SHA256 验签，再按条件更新保证入账幂等——
 * 同一支付单同一渠道交易号的重复回调只回放不重复入账。
 */
@Service
public class PaymentService {

    private static final String RESOURCE = "CRM_PAYMENT";
    static final Set<String> CHANNELS = Set.of("wechat", "alipay", "bank", "cash", "other");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final AuditService auditService;

    public PaymentService(OrderRepository orderRepository,
                          PaymentRepository paymentRepository,
                          PaymentGateway paymentGateway,
                          AuditService auditService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.auditService = auditService;
    }

    @Transactional
    public OrderDtos.PaymentView initiate(UUID orderId, OrderDtos.PaymentCreateRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        if (!"CREATED".equals(order.getStatus()) && !"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "订单当前状态不可支付");
        }
        String channel = request.channel().trim().toLowerCase();
        if (!CHANNELS.contains(channel)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "支付渠道取值不合法");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "支付金额必须大于 0");
        }
        BigDecimal outstanding = order.getPayableAmount().subtract(order.getPaidAmount());
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "支付金额超过未付余额");
        }
        String idempotencyKey = request.idempotencyKey().trim();
        if (paymentRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "支付幂等键已存在");
        }

        Payment payment = new Payment();
        payment.setPaymentNo("P" + paymentRepository.nextPaymentNo());
        payment.setOrderId(orderId);
        payment.setChannel(channel);
        payment.setAmount(amount);
        payment.setStatus("PROCESSING");
        payment.setIdempotencyKey(idempotencyKey);
        payment.setChannelTradeNo(paymentGateway.initiatePayment(payment.getPaymentNo(), channel, amount));
        payment = paymentRepository.save(payment);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("paymentNo", payment.getPaymentNo());
        audit.put("orderNo", order.getOrderNo());
        audit.put("channel", channel);
        audit.put("amount", amount.toPlainString());
        auditService.record("PAYMENT_INITIATE", RESOURCE, payment.getId().toString(), audit);

        return toView(payment, order.getOrderNo());
    }

    /**
     * 渠道回调（permitAll，验签保护）：验签失败 401；金额不符 422；
     * 已 SUCCESS 且渠道交易号相同 → 幂等回放 replayed=true，不重复入账；
     * 并发回调 markSuccess 返回 0 → 同样按回放处理。
     */
    @Transactional
    public OrderDtos.CallbackResult handleCallback(String channel, OrderDtos.CallbackRequest request) {
        BigDecimal amount = request.amount();
        boolean success = Boolean.TRUE.equals(request.success());
        String channelTradeNo = request.channelTradeNo() == null ? "" : request.channelTradeNo().trim();
        if (!paymentGateway.verifyCallback(request.paymentNo(), amount, success, channelTradeNo,
                request.signature())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "回调验签失败");
        }
        Payment payment = paymentRepository.findByPaymentNo(request.paymentNo().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "支付单不存在"));
        if (payment.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "回调金额与支付单不符");
        }

        boolean replayed = false;
        String resultStatus = payment.getStatus();
        switch (payment.getStatus()) {
            case "SUCCESS" -> {
                if (channelTradeNo.equals(payment.getChannelTradeNo())) {
                    replayed = true;
                    resultStatus = "SUCCESS";
                } else {
                    throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "支付单已在其他渠道交易号下完成");
                }
            }
            case "FAILED", "CLOSED" -> {
                if (success) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "支付单已关闭");
                }
                replayed = true;
            }
            default -> {
                if (success) {
                    Instant now = Instant.now();
                    int updated = paymentRepository.markSuccess(payment.getId(), channelTradeNo,
                            payloadHash(request), now);
                    if (updated == 0) {
                        // 并发重复回调：另一路已入账，本请求按幂等回放处理
                        replayed = true;
                        resultStatus = "SUCCESS";
                    } else {
                        int posted = orderRepository.accumulatePaid(payment.getOrderId(), payment.getAmount(), now);
                        if (posted == 0) {
                            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "入账金额超出订单应付");
                        }
                        resultStatus = "SUCCESS";
                    }
                } else {
                    int updated = paymentRepository.markFailed(payment.getId());
                    if (updated == 0) {
                        replayed = true;
                    }
                    resultStatus = "FAILED";
                }
            }
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("paymentNo", payment.getPaymentNo());
        audit.put("channel", channel);
        audit.put("success", success);
        audit.put("replayed", replayed);
        auditService.record("PAYMENT_CALLBACK", RESOURCE, payment.getId().toString(), audit);

        return new OrderDtos.CallbackResult(payment.getPaymentNo(), resultStatus, replayed);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.PaymentView> list(String keyword,
                                                    String status,
                                                    String channel,
                                                    UUID orderId,
                                                    int page,
                                                    int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Payment> result = paymentRepository.findAll(
                buildSpecification(keyword, status, channel, orderId), pageable);
        Map<UUID, Order> orders = orderRepository.findAllById(result.getContent().stream()
                        .map(Payment::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<OrderDtos.PaymentView> items = result.getContent().stream()
                .map(payment -> {
                    Order order = orders.get(payment.getOrderId());
                    return toView(payment, order == null ? null : order.getOrderNo());
                })
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public OrderDtos.PaymentView detail(UUID paymentId) {
        Payment payment = load(paymentId);
        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        return toView(payment, order == null ? null : order.getOrderNo());
    }

    @Transactional(readOnly = true)
    public Payment load(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "支付单不存在"));
    }

    static OrderDtos.PaymentView toView(Payment payment, String orderNo) {
        return new OrderDtos.PaymentView(
                payment.getId(),
                payment.getPaymentNo(),
                payment.getOrderId(),
                orderNo,
                payment.getChannel(),
                payment.getChannelTradeNo(),
                payment.getAmount(),
                payment.getRefundedAmount(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }

    /**
     * 回调报文 SHA-256 哈希，用于入账留痕。
     */
    private String payloadHash(OrderDtos.CallbackRequest request) {
        String payload = "{\"paymentNo\":\"" + request.paymentNo()
                + "\",\"channelTradeNo\":\"" + (request.channelTradeNo() == null ? "" : request.channelTradeNo())
                + "\",\"amount\":\"" + (request.amount() == null ? "" : request.amount().toPlainString())
                + "\",\"success\":" + request.success() + "}";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("回调报文哈希计算失败", e);
        }
    }

    private Specification<Payment> buildSpecification(String keyword, String status, String channel, UUID orderId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("paymentNo")),
                        "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel.trim()));
            }
            if (orderId != null) {
                predicates.add(cb.equal(root.get("orderId"), orderId));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
