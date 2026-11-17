package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeAddress;
import com.hospital.crm.homevisit.domain.HomeAddressRepository;
import com.hospital.crm.homevisit.domain.HomeService;
import com.hospital.crm.homevisit.domain.HomeServiceRepository;
import com.hospital.crm.homevisit.domain.HomeStaffQualificationRepository;
import com.hospital.crm.homevisit.domain.HomeVisitException;
import com.hospital.crm.homevisit.domain.HomeVisitExceptionRepository;
import com.hospital.crm.homevisit.domain.HomeVisitExecution;
import com.hospital.crm.homevisit.domain.HomeVisitExecutionRepository;
import com.hospital.crm.homevisit.domain.HomeVisitOrder;
import com.hospital.crm.homevisit.domain.HomeVisitOrderRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 上门服务订单服务：状态机 PENDING→APPROVED→ASSIGNED→ACCEPTED→IN_PROGRESS→COMPLETED，
 * 审核驳回为 REJECTED，派单拒绝回 APPROVED，取消仅限未开始服务的订单，
 * REJECTED/COMPLETED/CANCELLED 为终态；派单与接单二次校验人员资质有效性，
 * 订单存在未解决异常时不能完成。
 */
@Service
public class HomeVisitOrderService {

    private static final String RESOURCE = "HOME_VISIT_ORDER";
    private static final String RESOURCE_EXCEPTION = "HOME_VISIT_EXCEPTION";
    private static final Set<String> UNRESOLVED_EXCEPTION_STATUSES = Set.of("OPEN", "ESCALATED");

    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "PENDING", Set.of("APPROVED", "REJECTED", "CANCELLED"),
            "APPROVED", Set.of("ASSIGNED", "CANCELLED"),
            "ASSIGNED", Set.of("ACCEPTED", "APPROVED", "CANCELLED"),
            "ACCEPTED", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("COMPLETED"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of(),
            "REJECTED", Set.of()
    );

    private final HomeVisitOrderRepository orderRepository;
    private final HomeServiceRepository serviceRepository;
    private final HomeAddressRepository addressRepository;
    private final HomeStaffQualificationRepository qualificationRepository;
    private final HomeVisitExecutionRepository executionRepository;
    private final HomeVisitExceptionRepository exceptionRepository;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public HomeVisitOrderService(HomeVisitOrderRepository orderRepository,
                                 HomeServiceRepository serviceRepository,
                                 HomeAddressRepository addressRepository,
                                 HomeStaffQualificationRepository qualificationRepository,
                                 HomeVisitExecutionRepository executionRepository,
                                 HomeVisitExceptionRepository exceptionRepository,
                                 CustomerService customerService,
                                 SysUserRepository userRepository,
                                 AuditService auditService) {
        this.orderRepository = orderRepository;
        this.serviceRepository = serviceRepository;
        this.addressRepository = addressRepository;
        this.qualificationRepository = qualificationRepository;
        this.executionRepository = executionRepository;
        this.exceptionRepository = exceptionRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<HomeVisitDtos.OrderSummary> list(String keyword,
                                                         String status,
                                                         String serviceCode,
                                                         UUID customerId,
                                                         UUID staffUserId,
                                                         LocalDate dateFrom,
                                                         LocalDate dateTo,
                                                         int page,
                                                         int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HomeVisitOrder> result = orderRepository.findAll(
                buildSpecification(keyword, status, serviceCode, customerId, staffUserId, dateFrom, dateTo),
                pageable);
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                result.getContent().stream().map(HomeVisitOrder::getCustomerId).collect(Collectors.toSet()));
        Map<String, HomeService> services = serviceByCodes(result.getContent().stream()
                .map(HomeVisitOrder::getServiceCode).collect(Collectors.toSet()));
        Map<UUID, SysUser> staffs = userMap(result.getContent().stream()
                .map(HomeVisitOrder::getStaffUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<HomeVisitDtos.OrderSummary> items = result.getContent().stream()
                .map(order -> toSummary(order, customers.get(order.getCustomerId()),
                        services.get(order.getServiceCode()),
                        // 未派单订单 staffUserId 为空，Map.of() 不允许 null 键查询
                        order.getStaffUserId() == null ? null : staffs.get(order.getStaffUserId())))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public HomeVisitDtos.OrderDetail detail(UUID orderId) {
        HomeVisitOrder order = load(orderId);
        CustomerDtos.CustomerBrief customer = customerService.brief(order.getCustomerId());
        HomeService service = serviceRepository.findByServiceCode(order.getServiceCode()).orElse(null);
        SysUser staff = order.getStaffUserId() == null ? null
                : userRepository.findById(order.getStaffUserId()).orElse(null);
        HomeAddress address = addressRepository.findById(order.getAddressId()).orElse(null);
        List<HomeVisitDtos.ExecutionView> executions =
                executionRepository.findByOrderIdOrderByVisitAtAsc(orderId).stream()
                        .map(e -> new HomeVisitDtos.ExecutionView(e.getId(), e.getVisitAt(),
                                e.getContent(), e.getRecordedBy()))
                        .toList();
        List<HomeVisitDtos.ExceptionView> exceptions =
                exceptionRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                        .map(x -> toExceptionView(x, order.getOrderNo()))
                        .toList();
        HomeVisitDtos.AddressSnapshot snapshot = address == null ? null
                : new HomeVisitDtos.AddressSnapshot(address.getAddress(), address.getContactName(),
                        address.getContactPhoneMasked());
        return new HomeVisitDtos.OrderDetail(
                toSummary(order, customer, service, staff),
                order.getRemark(),
                order.getReviewReason(),
                order.getCancelReason(),
                order.getAssignedAt(),
                order.getAcceptedAt(),
                snapshot,
                executions,
                exceptions,
                service == null || service.getPrice() == null ? null : service.getPrice().toPlainString(),
                service == null ? null : service.getServiceName(),
                service == null ? null : service.getRequiredQualType());
    }

    @Transactional
    public HomeVisitDtos.OrderSummary create(HomeVisitDtos.OrderCreateRequest request) {
        HomeService service = serviceRepository.findByServiceCode(request.serviceCode().trim().toUpperCase())
                .orElse(null);
        if (service == null || !"published".equals(service.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "服务不存在或未发布");
        }
        CustomerDtos.CustomerBrief customer = customerService.brief(request.customerId());
        HomeAddress address = addressRepository.findById(request.addressId()).orElse(null);
        if (address == null || !address.getCustomerId().equals(request.customerId())
                || !"active".equals(address.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "服务地址无效");
        }
        if (request.scheduledDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "预约日期不能早于今天");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结束时间必须晚于开始时间");
        }

        HomeVisitOrder order = new HomeVisitOrder();
        order.setOrderNo("H" + orderRepository.nextOrderNo());
        order.setServiceCode(service.getServiceCode());
        order.setCustomerId(request.customerId());
        order.setAddressId(request.addressId());
        order.setScheduledDate(request.scheduledDate());
        order.setStartTime(request.startTime());
        order.setEndTime(request.endTime());
        order.setStatus("PENDING");
        order.setRemark(trimToNull(request.remark()));
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("customerNo", customer.customerNo());
        audit.put("serviceCode", order.getServiceCode());
        auditService.record("HV_ORDER_CREATE", RESOURCE, order.getId().toString(), audit);

        return toSummary(order, customer, service, null);
    }

    /**
     * 订单审核：PENDING→APPROVED/REJECTED，驳回必须带原因；仅 manage 权限可操作。
     */
    @Transactional
    public HomeVisitDtos.OrderSummary review(UUID orderId, HomeVisitDtos.OrderReviewRequest request) {
        HomeVisitOrder order = load(orderId);
        String target = request.approved() ? "APPROVED" : "REJECTED";
        requireTransition(order, target, "审核");
        String reason = trimToNull(request.reason());
        if (!request.approved() && reason == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "驳回订单必须填写原因");
        }
        order.setStatus(target);
        order.setReviewedBy(CurrentUser.principal().username());
        order.setReviewedAt(Instant.now());
        order.setReviewReason(request.approved() ? null : reason);
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("approved", request.approved());
        auditService.record("HV_ORDER_REVIEW", RESOURCE, orderId.toString(), audit);

        return summaryWithNames(order);
    }

    @Transactional
    public HomeVisitDtos.OrderSummary dispatch(UUID orderId, HomeVisitDtos.DispatchRequest request) {
        HomeVisitOrder order = load(orderId);
        requireTransition(order, "ASSIGNED", "派单");
        SysUser staff = userRepository.findById(request.staffUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "服务人员账号不存在"));
        requireQualification(staff.getId(), order);

        order.setStaffUserId(staff.getId());
        order.setStatus("ASSIGNED");
        order.setAssignedAt(Instant.now());
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("username", staff.getUsername());
        auditService.record("HV_ORDER_DISPATCH", RESOURCE, orderId.toString(), audit);

        return summaryWithNames(order);
    }

    @Transactional
    public HomeVisitDtos.OrderSummary accept(UUID orderId) {
        HomeVisitOrder order = load(orderId);
        requireTransition(order, "ACCEPTED", "接单");
        // 接单再次校验资质，防止派单后资质被停用或过期
        requireQualification(order.getStaffUserId(), order);
        order.setStatus("ACCEPTED");
        order.setAcceptedAt(Instant.now());
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("HV_ORDER_ACCEPT", RESOURCE, orderId.toString(), audit);

        return summaryWithNames(order);
    }

    /**
     * 服务人员拒绝派单：ASSIGNED→APPROVED，清空派单信息，拒绝原因追加到备注。
     */
    @Transactional
    public HomeVisitDtos.OrderSummary reject(UUID orderId, HomeVisitDtos.ReasonRequest request) {
        HomeVisitOrder order = load(orderId);
        requireTransition(order, "APPROVED", "拒绝派单");
        order.setStatus("APPROVED");
        order.setStaffUserId(null);
        order.setAssignedAt(null);
        order.setRemark(appendRemark(order.getRemark(), "[拒绝] " + request.reason().trim()));
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("reason", request.reason().trim());
        auditService.record("HV_ORDER_REJECT", RESOURCE, orderId.toString(), audit);

        return summaryWithNames(order);
    }

    @Transactional
    public HomeVisitDtos.OrderSummary start(UUID orderId) {
        HomeVisitOrder order = load(orderId);
        requireTransition(order, "IN_PROGRESS", "开始服务");
        order.setStatus("IN_PROGRESS");
        order.setActualStartAt(Instant.now());
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("HV_ORDER_START", RESOURCE, orderId.toString(), audit);

        return summaryWithNames(order);
    }

    @Transactional
    public HomeVisitDtos.ExecutionView addExecution(UUID orderId,
                                                    HomeVisitDtos.ExecutionCreateRequest request) {
        HomeVisitOrder order = load(orderId);
        if (!"IN_PROGRESS".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅进行中的订单可登记执行记录");
        }
        HomeVisitExecution execution = new HomeVisitExecution();
        execution.setOrderId(orderId);
        execution.setVisitAt(request.visitAt() != null ? request.visitAt() : Instant.now());
        execution.setContent(request.content().trim());
        execution.setRecordedBy(CurrentUser.principal().username());
        execution = executionRepository.save(execution);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("HV_ORDER_EXECUTION", RESOURCE, orderId.toString(), audit);

        return new HomeVisitDtos.ExecutionView(execution.getId(), execution.getVisitAt(),
                execution.getContent(), execution.getRecordedBy());
    }

    @Transactional
    public HomeVisitDtos.ExceptionView reportException(UUID orderId,
                                                       HomeVisitDtos.ExceptionCreateRequest request) {
        HomeVisitOrder order = load(orderId);
        if (!"IN_PROGRESS".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅进行中的订单可上报异常");
        }
        HomeVisitException exception = new HomeVisitException();
        exception.setOrderId(orderId);
        exception.setLevel(request.level());
        exception.setCategory(request.category());
        exception.setDescription(request.description().trim());
        exception.setStatus("OPEN");
        exception.setReportedBy(CurrentUser.principal().username());
        exception = exceptionRepository.save(exception);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("level", exception.getLevel());
        auditService.record("HV_ORDER_EXCEPTION", RESOURCE, orderId.toString(), audit);

        return toExceptionView(exception, order.getOrderNo());
    }

    @Transactional
    public HomeVisitDtos.ExceptionView escalateException(UUID exceptionId,
                                                         HomeVisitDtos.ReasonRequest request) {
        HomeVisitException exception = loadException(exceptionId);
        if (!"OPEN".equals(exception.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待处理的异常可以升级");
        }
        exception.setStatus("ESCALATED");
        exception.setEscalatedBy(CurrentUser.principal().username());
        exception.setEscalatedAt(Instant.now());
        exception.setEscalateReason(request.reason().trim());
        exception = exceptionRepository.save(exception);

        HomeVisitOrder order = load(exception.getOrderId());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        audit.put("reason", exception.getEscalateReason());
        auditService.record("HV_EXCEPTION_ESCALATE", RESOURCE_EXCEPTION, exceptionId.toString(), audit);

        return toExceptionView(exception, order.getOrderNo());
    }

    @Transactional
    public HomeVisitDtos.ExceptionView resolveException(UUID exceptionId,
                                                        HomeVisitDtos.ResolveRequest request) {
        HomeVisitException exception = loadException(exceptionId);
        if ("RESOLVED".equals(exception.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该异常已解决，请勿重复操作");
        }
        exception.setStatus("RESOLVED");
        exception.setResolvedBy(CurrentUser.principal().username());
        exception.setResolvedAt(Instant.now());
        exception.setResolution(request.resolution().trim());
        exception = exceptionRepository.save(exception);

        HomeVisitOrder order = load(exception.getOrderId());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("HV_EXCEPTION_RESOLVE", RESOURCE_EXCEPTION, exceptionId.toString(), audit);

        return toExceptionView(exception, order.getOrderNo());
    }

    @Transactional
    public HomeVisitDtos.OrderSummary finish(UUID orderId, HomeVisitDtos.FinishRequest request) {
        HomeVisitOrder order = load(orderId);
        requireTransition(order, "COMPLETED", "完成");
        long unresolved = exceptionRepository.countByOrderIdAndStatusIn(orderId, UNRESOLVED_EXCEPTION_STATUSES);
        if (unresolved > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "存在未处理的异常上报，请先处置");
        }
        Instant now = Instant.now();
        order.setStatus("COMPLETED");
        order.setActualEndAt(now);
        if (order.getActualStartAt() != null) {
            long seconds = Duration.between(order.getActualStartAt(), now).getSeconds();
            order.setDurationMinutes((int) Math.max(0, Math.round(seconds / 60.0)));
        }
        String extra = request == null ? null : trimToNull(request.remark());
        if (extra != null) {
            order.setRemark(appendRemark(order.getRemark(), extra));
        }
        order = orderRepository.save(order);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("orderNo", order.getOrderNo());
        auditService.record("HV_ORDER_FINISH", RESOURCE, orderId.toString(), audit);

        return summaryWithNames(order);
    }

    @Transactional
    public HomeVisitDtos.OrderSummary cancel(UUID orderId, HomeVisitDtos.ReasonRequest request) {
        HomeVisitOrder order = load(orderId);
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
        auditService.record("HV_ORDER_CANCEL", RESOURCE, orderId.toString(), audit);

        return summaryWithNames(order);
    }

    /**
     * 全局异常看板：按状态与级别过滤，供管理端处置未解决异常。
     */
    @Transactional(readOnly = true)
    public PageResponse<HomeVisitDtos.ExceptionView> listExceptions(String status,
                                                                    String level,
                                                                    int page,
                                                                    int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<HomeVisitException> result = exceptionRepository.findAll(
                buildExceptionSpecification(status, level), pageable);
        Map<UUID, HomeVisitOrder> orders = orderRepository.findAllById(result.getContent().stream()
                        .map(HomeVisitException::getOrderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(HomeVisitOrder::getId, Function.identity()));
        List<HomeVisitDtos.ExceptionView> items = result.getContent().stream()
                .map(exception -> {
                    HomeVisitOrder order = orders.get(exception.getOrderId());
                    return toExceptionView(exception, order == null ? null : order.getOrderNo());
                })
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public HomeVisitOrder load(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "上门服务订单不存在"));
    }

    private HomeVisitException loadException(UUID exceptionId) {
        return exceptionRepository.findById(exceptionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "异常记录不存在"));
    }

    /**
     * 资质校验：服务要求资质非空时，人员必须持有对应类型、启用且
     * 到期日为空或不早于预约日期的资质；无资质要求的项目直接放行。
     */
    private void requireQualification(UUID staffUserId, HomeVisitOrder order) {
        HomeService service = serviceRepository.findByServiceCode(order.getServiceCode()).orElse(null);
        if (service == null || service.getRequiredQualType() == null
                || service.getRequiredQualType().isBlank()) {
            return;
        }
        boolean valid = qualificationRepository
                .findByUserIdAndQualTypeAndStatus(staffUserId, service.getRequiredQualType(), "active")
                .stream()
                .anyMatch(q -> q.getExpiresAt() == null || !q.getExpiresAt().isBefore(order.getScheduledDate()));
        if (!valid) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "上门服务人员不具备该项目的有效资质");
        }
    }

    private void requireTransition(HomeVisitOrder order, String target, String operation) {
        Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED,
                    "当前状态 " + order.getStatus() + " 不能" + operation);
        }
    }

    private Map<String, HomeService> serviceByCodes(Set<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return serviceRepository.findByServiceCodeIn(codes).stream()
                .collect(Collectors.toMap(HomeService::getServiceCode, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, SysUser> userMap(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private HomeVisitDtos.OrderSummary summaryWithNames(HomeVisitOrder order) {
        CustomerDtos.CustomerBrief customer = customerService.brief(order.getCustomerId());
        HomeService service = serviceRepository.findByServiceCode(order.getServiceCode()).orElse(null);
        SysUser staff = order.getStaffUserId() == null ? null
                : userRepository.findById(order.getStaffUserId()).orElse(null);
        return toSummary(order, customer, service, staff);
    }

    private HomeVisitDtos.OrderSummary toSummary(HomeVisitOrder order,
                                                 CustomerDtos.CustomerBrief customer,
                                                 HomeService service,
                                                 SysUser staff) {
        return new HomeVisitDtos.OrderSummary(
                order.getId(),
                order.getOrderNo(),
                order.getServiceCode(),
                service == null ? null : service.getServiceName(),
                order.getCustomerId(),
                customer == null ? null : customer.name(),
                order.getAddressId(),
                order.getScheduledDate(),
                order.getStartTime(),
                order.getEndTime(),
                order.getStatus(),
                order.getStaffUserId(),
                staff == null ? null : staff.getDisplayName(),
                order.getActualStartAt(),
                order.getActualEndAt(),
                order.getDurationMinutes(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private HomeVisitDtos.ExceptionView toExceptionView(HomeVisitException exception, String orderNo) {
        return new HomeVisitDtos.ExceptionView(
                exception.getId(),
                exception.getOrderId(),
                orderNo,
                exception.getLevel(),
                exception.getCategory(),
                exception.getDescription(),
                exception.getStatus(),
                exception.getReportedBy(),
                exception.getEscalatedAt(),
                exception.getResolvedAt());
    }

    private Specification<HomeVisitOrder> buildSpecification(String keyword,
                                                             String status,
                                                             String serviceCode,
                                                             UUID customerId,
                                                             UUID staffUserId,
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
            if (serviceCode != null && !serviceCode.isBlank()) {
                predicates.add(cb.equal(root.get("serviceCode"), serviceCode.trim().toUpperCase()));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (staffUserId != null) {
                predicates.add(cb.equal(root.get("staffUserId"), staffUserId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDate"), dateTo));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<HomeVisitException> buildExceptionSpecification(String status, String level) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (level != null && !level.isBlank()) {
                predicates.add(cb.equal(root.get("level"), level.trim()));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String appendRemark(String current, String extra) {
        if (current == null || current.isBlank()) {
            return extra;
        }
        return current + "；" + extra;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
