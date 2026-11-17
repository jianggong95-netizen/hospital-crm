package com.hospital.crm.order.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.domain.Order;
import com.hospital.crm.order.domain.OrderItem;
import com.hospital.crm.order.domain.OrderItemRepository;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 统一订单服务：服务端重算行金额与应付金额，状态机 CREATED→PAID→COMPLETED，
 * 未支付可取消，已支付取消须走退款流程；金额一律 BigDecimal 两位 HALF_UP。
 */
@Service
public class OrderService {

    private static final String RESOURCE = "CRM_ORDER";
    private static final int MAX_ITEMS = 50;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final CustomerService customerService;
    private final AuditService auditService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PaymentRepository paymentRepository,
                        RefundRepository refundRepository,
                        CustomerService customerService,
                        AuditService auditService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    @Transactional
    public OrderDtos.OrderSummary create(OrderDtos.OrderCreateRequest request) {
        CustomerDtos.CustomerBrief customer = customerService.brief(request.customerId());
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "订单明细不能为空");
        }
        if (request.items().size() > MAX_ITEMS) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "订单明细不能超过 50 行");
        }
        BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "优惠金额不能超过订单总额");
        }

        List<OrderDtos.ItemLine> lines = request.items();
        List<OrderItem> items = new ArrayList<>(lines.size());
        BigDecimal total = BigDecimal.ZERO;
        for (OrderDtos.ItemLine line : lines) {
            if (line.quantity() == null || line.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "明细数量必须大于 0");
            }
            if (line.unitPrice() == null || line.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "明细单价不能为负数");
            }
            BigDecimal amount = line.quantity().multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            OrderItem item = new OrderItem();
            item.setItemCode(line.itemCode().trim());
            item.setItemName(line.itemName().trim());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice().setScale(2, RoundingMode.HALF_UP));
            item.setAmount(amount);
            items.add(item);
            total = total.add(amount);
        }
        if (discount.compareTo(total) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "优惠金额不能超过订单总额");
        }

        String idempotencyKey = trimToNull(request.idempotencyKey());
        if (idempotencyKey != null
                && orderRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT, "订单幂等键已存在");
        }

        Order order = new Order();
        order.setOrderNo("O" + orderRepository.nextOrderNo());
        order.setCustomerId(request.customerId());
        order.setOrderType(request.orderType().trim());
        order.setStatus("CREATED");
        order.setTotalAmount(total);
        order.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
        order.setPayableAmount(total.subtract(discount.setScale(2, RoundingMode.HALF_UP)));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setRefundedAmount(BigDecimal.ZERO);
        order.setIdempotencyKey(idempotencyKey);
        order.setSourceChannel(trimToNull(request.sourceChannel()));
        order = orderRepository.save(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        orderItemRepository.saveAll(items);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("customerNo", customer.customerNo());
        audit.put("totalAmount", order.getTotalAmount().toPlainString());
        audit.put("payableAmount", order.getPayableAmount().toPlainString());
        auditService.record("ORDER_CREATE", RESOURCE, order.getId().toString(), audit);

        return toSummary(order, customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.OrderSummary> list(String keyword,
                                                     String status,
                                                     UUID customerId,
                                                     LocalDate dateFrom,
                                                     LocalDate dateTo,
                                                     int page,
                                                     int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = orderRepository.findAll(
                buildSpecification(keyword, status, customerId, dateFrom, dateTo), pageable);
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                result.getContent().stream().map(Order::getCustomerId).collect(Collectors.toSet()));
        List<OrderDtos.OrderSummary> items = result.getContent().stream()
                .map(order -> toSummary(order, customers.get(order.getCustomerId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail detail(UUID orderId) {
        Order order = load(orderId);
        CustomerDtos.CustomerBrief customer = customerService.brief(order.getCustomerId());
        List<OrderDtos.OrderItemView> items = orderItemRepository.findByOrderId(orderId).stream()
                .map(item -> new OrderDtos.OrderItemView(item.getId(), item.getItemCode(),
                        item.getItemName(), item.getQuantity(), item.getUnitPrice(), item.getAmount()))
                .toList();
        List<OrderDtos.PaymentView> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(payment -> PaymentService.toView(payment, order.getOrderNo()))
                .toList();
        List<OrderDtos.RefundView> refunds = refundRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(refund -> RefundService.toView(refund))
                .toList();
        return new OrderDtos.OrderDetail(toSummary(order, customer), order.getCancelReason(),
                items, payments, refunds);
    }

    @Transactional
    public OrderDtos.OrderSummary cancel(UUID orderId, OrderDtos.ReasonRequest request) {
        Order order = load(orderId);
        if (!"CREATED".equals(order.getStatus())
                || order.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "订单已有支付，请走退款流程");
        }
        order.setStatus("CANCELLED");
        order.setCancelReason(trimToNull(request.reason()));
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("reason", order.getCancelReason());
        auditService.record("ORDER_CANCEL", RESOURCE, orderId.toString(), audit);

        return summaryWithCustomer(order);
    }

    @Transactional
    public OrderDtos.OrderSummary complete(UUID orderId) {
        Order order = load(orderId);
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "当前状态 " + order.getStatus() + " 不能完成");
        }
        order.setStatus("COMPLETED");
        order.setCompletedAt(Instant.now());
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("ORDER_COMPLETE", RESOURCE, orderId.toString(), audit);

        return summaryWithCustomer(order);
    }

    @Transactional(readOnly = true)
    public Order load(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
    }

    private Specification<Order> buildSpecification(String keyword,
                                                    String status,
                                                    UUID customerId,
                                                    LocalDate dateFrom,
                                                    LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("orderNo")),
                        "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private OrderDtos.OrderSummary summaryWithCustomer(Order order) {
        return toSummary(order, customerService.brief(order.getCustomerId()));
    }

    private OrderDtos.OrderSummary toSummary(Order order, CustomerDtos.CustomerBrief customer) {
        return new OrderDtos.OrderSummary(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerId(),
                customer == null ? null : customer.name(),
                order.getOrderType(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getPayableAmount(),
                order.getPaidAmount(),
                order.getRefundedAmount(),
                order.getSourceChannel(),
                order.getCompletedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
