package com.hospital.crm.order.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.application.gateway.InvoiceProvider;
import com.hospital.crm.order.domain.Invoice;
import com.hospital.crm.order.domain.InvoiceRepository;
import com.hospital.crm.order.domain.Order;
import com.hospital.crm.order.domain.OrderRepository;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 发票服务：接口抽象 InvoiceProvider（stub 恒开成功），
 * 可开票净额 = paid_amount − refunded_amount 且不超过 payable_amount，
 * 同一订单 APPLIED/ISSUED 发票累计不得超过可开票净额；红冲后置 VOID 释放额度。
 */
@Service
public class InvoiceService {

    private static final String RESOURCE = "CRM_INVOICE";
    private static final Set<String> INVOICEABLE_ORDER_STATUSES = Set.of("PAID", "COMPLETED", "PARTIAL_REFUNDED");

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final InvoiceProvider invoiceProvider;
    private final AuditService auditService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          OrderRepository orderRepository,
                          InvoiceProvider invoiceProvider,
                          AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.invoiceProvider = invoiceProvider;
        this.auditService = auditService;
    }

    @Transactional
    public OrderDtos.InvoiceView apply(UUID orderId, OrderDtos.InvoiceApplyRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        if (!INVOICEABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "订单当前状态不可开票");
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "开票金额必须大于 0");
        }
        BigDecimal invoiceable = order.getPaidAmount().subtract(order.getRefundedAmount());
        if (amount.compareTo(invoiceable) > 0 || amount.compareTo(order.getPayableAmount()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "开票金额超过可开票金额");
        }
        BigDecimal issued = invoiceRepository.sumActiveAmount(orderId);
        if (issued.add(amount).compareTo(invoiceable) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "开票金额超过可开票金额");
        }

        Invoice invoice = new Invoice();
        invoice.setInvoiceNo("INV" + invoiceRepository.nextInvoiceNo());
        invoice.setOrderId(orderId);
        invoice.setCustomerId(order.getCustomerId());
        invoice.setTitle(request.title().trim());
        invoice.setTaxNo(trimToNull(request.taxNo()));
        invoice.setAmount(amount);
        invoice.setInvoiceType(request.invoiceType() == null ? "general" : request.invoiceType().trim());
        invoice.setStatus("APPLIED");
        invoice.setAppliedBy(CurrentUser.principal().username());
        invoice.setAppliedAt(Instant.now());
        invoice = invoiceRepository.save(invoice);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("invoiceNo", invoice.getInvoiceNo());
        audit.put("orderNo", order.getOrderNo());
        audit.put("amount", amount.toPlainString());
        auditService.record("INVOICE_APPLY", RESOURCE, invoice.getId().toString(), audit);

        return toView(invoice, order.getOrderNo());
    }

    @Transactional
    public OrderDtos.InvoiceView issue(UUID invoiceId) {
        Invoice invoice = load(invoiceId);
        if (!"APPLIED".equals(invoice.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "当前状态 " + invoice.getStatus() + " 不能开票");
        }
        Order order = orderRepository.findById(invoice.getOrderId()).orElse(null);
        InvoiceProvider.InvoiceIssueResult result = invoiceProvider.issue(
                new InvoiceProvider.InvoiceIssueCommand(
                        invoice.getInvoiceNo(),
                        order == null ? null : order.getOrderNo(),
                        invoice.getTitle(),
                        invoice.getTaxNo(),
                        invoice.getAmount(),
                        invoice.getInvoiceType()));
        if (!result.success()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "开票失败：" + result.message());
        }
        invoice.setStatus("ISSUED");
        invoice.setProvider("stub");
        invoice.setFileRef(result.fileRef());
        invoice.setIssuedAt(Instant.now());
        invoice = invoiceRepository.save(invoice);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("invoiceNo", invoice.getInvoiceNo());
        auditService.record("INVOICE_ISSUE", RESOURCE, invoiceId.toString(), audit);

        return toView(invoice, order == null ? null : order.getOrderNo());
    }

    @Transactional
    public OrderDtos.InvoiceView voidInvoice(UUID invoiceId, OrderDtos.ReasonRequest request) {
        Invoice invoice = load(invoiceId);
        if (!"APPLIED".equals(invoice.getStatus()) && !"ISSUED".equals(invoice.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该发票已红冲，请勿重复操作");
        }
        invoice.setStatus("VOID");
        invoice.setVoidReason(request.reason().trim());
        invoice = invoiceRepository.save(invoice);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("invoiceNo", invoice.getInvoiceNo());
        audit.put("reason", invoice.getVoidReason());
        auditService.record("INVOICE_VOID", RESOURCE, invoiceId.toString(), audit);

        Order order = orderRepository.findById(invoice.getOrderId()).orElse(null);
        return toView(invoice, order == null ? null : order.getOrderNo());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.InvoiceView> list(UUID orderId, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Invoice> result = invoiceRepository.findAll(buildSpecification(orderId, status), pageable);
        Map<UUID, Order> orders = orderRepository.findAllById(result.getContent().stream()
                        .map(Invoice::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<OrderDtos.InvoiceView> items = result.getContent().stream()
                .map(invoice -> {
                    Order order = orders.get(invoice.getOrderId());
                    return toView(invoice, order == null ? null : order.getOrderNo());
                })
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public Invoice load(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "发票不存在"));
    }

    private static OrderDtos.InvoiceView toView(Invoice invoice, String orderNo) {
        return new OrderDtos.InvoiceView(
                invoice.getId(),
                invoice.getInvoiceNo(),
                invoice.getOrderId(),
                orderNo,
                invoice.getCustomerId(),
                invoice.getTitle(),
                invoice.getTaxNo(),
                invoice.getAmount(),
                invoice.getInvoiceType(),
                invoice.getStatus(),
                invoice.getProvider(),
                invoice.getFileRef(),
                invoice.getAppliedBy(),
                invoice.getAppliedAt(),
                invoice.getIssuedAt(),
                invoice.getVoidReason());
    }

    private Specification<Invoice> buildSpecification(UUID orderId, String status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (orderId != null) {
                predicates.add(cb.equal(root.get("orderId"), orderId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
