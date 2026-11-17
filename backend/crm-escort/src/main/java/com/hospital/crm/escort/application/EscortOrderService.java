package com.hospital.crm.escort.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.customer.support.CustomerMasking;
import com.hospital.crm.escort.api.EscortDtos;
import com.hospital.crm.escort.domain.Escort;
import com.hospital.crm.escort.domain.EscortOrder;
import com.hospital.crm.escort.domain.EscortOrderRepository;
import com.hospital.crm.escort.domain.EscortQualificationRepository;
import com.hospital.crm.escort.domain.EscortRepository;
import com.hospital.crm.escort.domain.EscortReview;
import com.hospital.crm.escort.domain.EscortReviewRepository;
import com.hospital.crm.escort.domain.EscortScheduleRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 陪诊订单服务：创建后进入派单链路，状态机
 * PENDING→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED，拒绝回退 PENDING，
 * 取消仅限未开始服务的订单，COMPLETED/CANCELLED 为终态；
 * 派单按顺序校验陪诊师状态、资质有效期、排班覆盖与同日时段冲突。
 */
@Service
public class EscortOrderService {

    private static final String RESOURCE = "ESCORT_ORDER";
    private static final Set<String> OCCUPYING_STATUSES = Set.of("ASSIGNED", "ACCEPTED", "IN_PROGRESS");

    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "PENDING", Set.of("ASSIGNED", "CANCELLED"),
            "ASSIGNED", Set.of("ACCEPTED", "PENDING", "CANCELLED"),
            "ACCEPTED", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("COMPLETED"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of()
    );

    private final EscortOrderRepository orderRepository;
    private final EscortRepository escortRepository;
    private final EscortQualificationRepository qualificationRepository;
    private final EscortScheduleRepository scheduleRepository;
    private final EscortReviewRepository reviewRepository;
    private final CustomerService customerService;
    private final SensitiveDataCipher cipher;
    private final SensitiveDataHasher hasher;
    private final AuditService auditService;

    public EscortOrderService(EscortOrderRepository orderRepository,
                              EscortRepository escortRepository,
                              EscortQualificationRepository qualificationRepository,
                              EscortScheduleRepository scheduleRepository,
                              EscortReviewRepository reviewRepository,
                              CustomerService customerService,
                              SensitiveDataCipher cipher,
                              SensitiveDataHasher hasher,
                              AuditService auditService) {
        this.orderRepository = orderRepository;
        this.escortRepository = escortRepository;
        this.qualificationRepository = qualificationRepository;
        this.scheduleRepository = scheduleRepository;
        this.reviewRepository = reviewRepository;
        this.customerService = customerService;
        this.cipher = cipher;
        this.hasher = hasher;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<EscortDtos.OrderSummary> list(String keyword,
                                                      String status,
                                                      UUID escortId,
                                                      UUID customerId,
                                                      LocalDate dateFrom,
                                                      LocalDate dateTo,
                                                      int page,
                                                      int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<EscortOrder> result = orderRepository.findAll(
                buildSpecification(keyword, status, escortId, customerId, dateFrom, dateTo), pageable);
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                result.getContent().stream().map(EscortOrder::getCustomerId).collect(Collectors.toSet()));
        Map<UUID, Escort> escorts = escortNames(result.getContent().stream()
                .map(EscortOrder::getEscortId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        List<EscortDtos.OrderSummary> items = result.getContent().stream()
                .map(order -> toSummary(order, customers.get(order.getCustomerId()),
                        order.getEscortId() == null ? null : escorts.get(order.getEscortId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public EscortDtos.OrderDetail detail(UUID orderId) {
        EscortOrder order = load(orderId);
        CustomerDtos.CustomerBrief customer = customerService.brief(order.getCustomerId());
        Escort escort = order.getEscortId() == null ? null
                : escortRepository.findById(order.getEscortId()).orElse(null);
        EscortDtos.OrderReviewView review = reviewRepository.findByOrderId(orderId)
                .map(r -> new EscortDtos.OrderReviewView(r.getId(), r.getRating(), r.getContent(),
                        r.getCreatedBy(), r.getCreatedAt()))
                .orElse(null);
        return new EscortDtos.OrderDetail(
                toSummary(order, customer, escort),
                order.getContactPhoneMasked(),
                order.getRemark(),
                order.getRejectReason(),
                order.getCancelReason(),
                order.getAssignedAt(),
                order.getAcceptedAt(),
                review);
    }

    @Transactional
    public EscortDtos.OrderSummary create(EscortDtos.OrderCreateRequest request) {
        CustomerDtos.CustomerBrief customer = customerService.brief(request.customerId());
        if (request.serviceDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "服务日期不能早于今天");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结束时间必须晚于开始时间");
        }

        EscortOrder order = new EscortOrder();
        order.setOrderNo("E" + orderRepository.nextOrderNo());
        order.setCustomerId(request.customerId());
        order.setAppointmentId(request.appointmentId());
        order.setServiceDate(request.serviceDate());
        order.setStartTime(request.startTime());
        order.setEndTime(request.endTime());
        order.setArea(request.area().trim());
        applyContactPhone(order, request.contactPhone());
        order.setStatus("PENDING");
        order.setRemark(trimToNull(request.remark()));
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("customerNo", customer.customerNo());
        auditService.record("ESCORTORDER_CREATE", RESOURCE, order.getId().toString(), audit);

        return toSummary(order, customer, null);
    }

    @Transactional
    public EscortDtos.OrderSummary dispatch(UUID orderId, EscortDtos.DispatchRequest request) {
        EscortOrder order = load(orderId);
        requireTransition(order, "ASSIGNED", "派单");
        Escort escort = escortRepository.findById(request.escortId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "陪诊师不存在"));
        if (!"active".equals(escort.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "陪诊师已停用，不能派单");
        }
        requireValidQualification(escort, order);
        requireScheduleCoverage(escort, order);
        requireNoConflictingOrder(escort, order);

        order.setEscortId(escort.getId());
        order.setStatus("ASSIGNED");
        order.setAssignedAt(Instant.now());
        order.setRejectReason(null);
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("escortNo", escort.getEscortNo());
        auditService.record("ESCORTORDER_DISPATCH", RESOURCE, orderId.toString(), audit);

        return toSummary(order, customerService.brief(order.getCustomerId()), escort);
    }

    @Transactional
    public EscortDtos.OrderSummary accept(UUID orderId) {
        EscortOrder order = load(orderId);
        requireTransition(order, "ACCEPTED", "接单");
        order.setStatus("ACCEPTED");
        order.setAcceptedAt(Instant.now());
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("ESCORTORDER_ACCEPT", RESOURCE, orderId.toString(), audit);

        return summaryWithEscort(order);
    }

    @Transactional
    public EscortDtos.OrderSummary reject(UUID orderId, EscortDtos.ReasonRequest request) {
        EscortOrder order = load(orderId);
        requireTransition(order, "PENDING", "拒绝");
        order.setStatus("PENDING");
        order.setRejectReason(trimToNull(request.reason()));
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("reason", order.getRejectReason());
        auditService.record("ESCORTORDER_REJECT", RESOURCE, orderId.toString(), audit);

        return summaryWithEscort(order);
    }

    @Transactional
    public EscortDtos.OrderSummary start(UUID orderId) {
        EscortOrder order = load(orderId);
        requireTransition(order, "IN_PROGRESS", "开始服务");
        order.setStatus("IN_PROGRESS");
        order.setActualStartAt(Instant.now());
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("ESCORTORDER_START", RESOURCE, orderId.toString(), audit);

        return summaryWithEscort(order);
    }

    @Transactional
    public EscortDtos.OrderSummary finish(UUID orderId, EscortDtos.FinishRequest request) {
        EscortOrder order = load(orderId);
        requireTransition(order, "COMPLETED", "完成");
        Instant now = Instant.now();
        order.setStatus("COMPLETED");
        order.setActualEndAt(now);
        if (order.getActualStartAt() != null) {
            long seconds = Duration.between(order.getActualStartAt(), now).getSeconds();
            order.setDurationMinutes((int) Math.max(0, Math.round(seconds / 60.0)));
        }
        String extra = request == null ? null : trimToNull(request.remark());
        if (extra != null) {
            order.setRemark(order.getRemark() == null || order.getRemark().isBlank()
                    ? extra : order.getRemark() + "；" + extra);
        }
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("ESCORTORDER_FINISH", RESOURCE, orderId.toString(), audit);

        return summaryWithEscort(order);
    }

    @Transactional
    public EscortDtos.OrderSummary cancel(UUID orderId, EscortDtos.ReasonRequest request) {
        EscortOrder order = load(orderId);
        if ("IN_PROGRESS".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "服务已开始，请使用完成");
        }
        requireTransition(order, "CANCELLED", "取消");
        order.setStatus("CANCELLED");
        order.setCancelReason(trimToNull(request.reason()));
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("reason", order.getCancelReason());
        auditService.record("ESCORTORDER_CANCEL", RESOURCE, orderId.toString(), audit);

        return summaryWithEscort(order);
    }

    @Transactional
    public EscortDtos.OrderReviewView review(UUID orderId, EscortDtos.ReviewRequest request) {
        EscortOrder order = load(orderId);
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅已完成订单可以评价");
        }
        if (order.getEscortId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "订单未关联陪诊师，无法评价");
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该订单已评价，请勿重复提交");
        }
        EscortReview review = new EscortReview();
        review.setOrderId(orderId);
        review.setEscortId(order.getEscortId());
        review.setRating(request.rating());
        review.setContent(trimToNull(request.content()));
        review.setCreatedBy(CurrentUser.principal().username());
        review = reviewRepository.save(review);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("ESCORTORDER_REVIEW", RESOURCE, orderId.toString(), audit);

        return new EscortDtos.OrderReviewView(review.getId(), review.getRating(),
                review.getContent(), review.getCreatedBy(), review.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<EscortDtos.ReviewView> reviews(UUID escortId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize);
        // escortId 可选：为空时查询全部评价
        Page<EscortReview> result = escortId == null
                ? reviewRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reviewRepository.findByEscortIdOrderByCreatedAtDesc(escortId, pageable);
        Map<UUID, EscortOrder> orders = orderRepository.findAllById(result.getContent().stream()
                        .map(EscortReview::getOrderId).toList()).stream()
                .collect(Collectors.toMap(EscortOrder::getId, Function.identity()));
        Map<UUID, Escort> escorts = escortRepository.findAllById(result.getContent().stream()
                        .map(EscortReview::getEscortId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Escort::getId, Function.identity()));
        List<EscortDtos.ReviewView> items = result.getContent().stream()
                .map(review -> {
                    EscortOrder order = orders.get(review.getOrderId());
                    Escort escort = escorts.get(review.getEscortId());
                    return new EscortDtos.ReviewView(
                            review.getId(),
                            order == null ? null : order.getOrderNo(),
                            review.getEscortId(),
                            escort == null ? null : escort.getName(),
                            review.getRating(),
                            review.getContent(),
                            review.getCreatedBy(),
                            review.getCreatedAt());
                })
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public EscortOrder load(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "陪诊订单不存在"));
    }

    /**
     * 派单校验一：至少一条有效资质（active 且到期日为空或不早于服务日期）。
     */
    private void requireValidQualification(Escort escort, EscortOrder order) {
        boolean valid = qualificationRepository
                .findByEscortIdAndStatus(escort.getId(), "active").stream()
                .anyMatch(q -> q.getExpiresAt() == null || !q.getExpiresAt().isBefore(order.getServiceDate()));
        if (!valid) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "陪诊师无有效资质");
        }
    }

    /**
     * 派单校验二：服务日存在完整覆盖订单时间窗的排班。
     */
    private void requireScheduleCoverage(Escort escort, EscortOrder order) {
        boolean covered = scheduleRepository
                .findByEscortIdAndWorkDate(escort.getId(), order.getServiceDate()).stream()
                .anyMatch(s -> !s.getStartTime().isAfter(order.getStartTime())
                        && !s.getEndTime().isBefore(order.getEndTime()));
        if (!covered) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "陪诊师该服务时段无覆盖排班");
        }
    }

    /**
     * 派单校验三：同日进行中链路订单时间窗不得与本单重叠。
     */
    private void requireNoConflictingOrder(Escort escort, EscortOrder order) {
        List<EscortOrder> conflicts = orderRepository.findConflictingOrders(
                escort.getId(), order.getServiceDate(), OCCUPYING_STATUSES,
                order.getId(), order.getStartTime(), order.getEndTime());
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "陪诊师该时段已有其他订单");
        }
    }

    private void requireTransition(EscortOrder order, String target, String operation) {
        Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "当前状态 " + order.getStatus() + " 不能" + operation);
        }
    }

    private void applyContactPhone(EscortOrder order, String phone) {
        String normalized = CustomerMasking.normalizePhone(phone);
        if (normalized == null) {
            return;
        }
        order.setContactPhoneEncrypted(cipher.encrypt(normalized));
        order.setContactPhoneHash(hasher.hash(normalized));
        order.setContactPhoneMasked(CustomerMasking.maskPhone(normalized));
    }

    private EscortDtos.OrderSummary summaryWithEscort(EscortOrder order) {
        Escort escort = order.getEscortId() == null ? null
                : escortRepository.findById(order.getEscortId()).orElse(null);
        return toSummary(order, customerService.brief(order.getCustomerId()), escort);
    }

    private Map<UUID, Escort> escortNames(Set<UUID> escortIds) {
        if (escortIds.isEmpty()) {
            return Map.of();
        }
        return escortRepository.findAllById(escortIds).stream()
                .collect(Collectors.toMap(Escort::getId, Function.identity()));
    }

    private EscortDtos.OrderSummary toSummary(EscortOrder order,
                                              CustomerDtos.CustomerBrief customer,
                                              Escort escort) {
        return new EscortDtos.OrderSummary(
                order.getId(),
                order.getOrderNo(),
                order.getCustomerId(),
                customer == null ? null : customer.name(),
                order.getAppointmentId(),
                order.getEscortId(),
                escort == null ? null : escort.getName(),
                order.getServiceDate(),
                order.getStartTime(),
                order.getEndTime(),
                order.getArea(),
                order.getStatus(),
                order.getActualStartAt(),
                order.getActualEndAt(),
                order.getDurationMinutes(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private Specification<EscortOrder> buildSpecification(String keyword,
                                                          String status,
                                                          UUID escortId,
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
            if (escortId != null) {
                predicates.add(cb.equal(root.get("escortId"), escortId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("serviceDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("serviceDate"), dateTo));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
