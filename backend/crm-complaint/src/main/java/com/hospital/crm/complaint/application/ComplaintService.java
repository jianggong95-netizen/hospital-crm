package com.hospital.crm.complaint.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.complaint.api.ComplaintDtos;
import com.hospital.crm.complaint.domain.Complaint;
import com.hospital.crm.complaint.domain.ComplaintAction;
import com.hospital.crm.complaint.domain.ComplaintActionRepository;
import com.hospital.crm.complaint.domain.ComplaintFeedback;
import com.hospital.crm.complaint.domain.ComplaintFeedbackRepository;
import com.hospital.crm.complaint.domain.ComplaintRectification;
import com.hospital.crm.complaint.domain.ComplaintRectificationRepository;
import com.hospital.crm.complaint.domain.ComplaintRepository;
import com.hospital.crm.complaint.domain.ComplaintReview;
import com.hospital.crm.complaint.domain.ComplaintReviewRepository;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.org.SysOrgRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 投诉表扬中心服务：工单建单、分派、处理动作、整改、患者反馈、提交复核、结案审核、
 * 手动升级与超时自动升级。所有流转均写入处理动作时间线，保证全过程留痕。
 */
@Service
public class ComplaintService {

    private static final String RESOURCE = "CRM_COMPLAINT";

    private static final Set<String> TICKET_TYPES = Set.of("complaint", "praise", "suggestion");
    private static final Set<String> SEVERITIES = Set.of("low", "normal", "high", "urgent");
    private static final Set<String> SOURCES =
            Set.of("manual", "phone", "online", "h5", "mini_program", "his", "letter");
    private static final Set<String> ACTION_TYPES = Set.of(
            "CONTACT", "INVESTIGATE", "REPLY", "NOTE", "RECTIFY", "FEEDBACK", "ESCALATE", "REVIEW", "OTHER");
    private static final Set<String> FEEDBACK_CHANNELS =
            Set.of("manual", "phone", "online", "h5", "mini_program");
    private static final Set<String> TERMINAL_STATUSES = Set.of("CLOSED", "CANCELLED");

    /**
     * 未指定处理时限时按严重程度套用默认 SLA（小时）。
     */
    private static final Map<String, Long> SLA_HOURS =
            Map.of("urgent", 4L, "high", 24L, "normal", 48L, "low", 72L);

    private final ComplaintRepository complaintRepository;
    private final ComplaintActionRepository actionRepository;
    private final ComplaintRectificationRepository rectificationRepository;
    private final ComplaintFeedbackRepository feedbackRepository;
    private final ComplaintReviewRepository reviewRepository;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final SysOrgRepository orgRepository;
    private final AuditService auditService;

    public ComplaintService(ComplaintRepository complaintRepository,
                            ComplaintActionRepository actionRepository,
                            ComplaintRectificationRepository rectificationRepository,
                            ComplaintFeedbackRepository feedbackRepository,
                            ComplaintReviewRepository reviewRepository,
                            CustomerService customerService,
                            SysUserRepository userRepository,
                            SysOrgRepository orgRepository,
                            AuditService auditService) {
        this.complaintRepository = complaintRepository;
        this.actionRepository = actionRepository;
        this.rectificationRepository = rectificationRepository;
        this.feedbackRepository = feedbackRepository;
        this.reviewRepository = reviewRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<ComplaintDtos.TicketView> list(String keyword,
                                                       String ticketType,
                                                       String severity,
                                                       String status,
                                                       UUID assigneeId,
                                                       UUID customerId,
                                                       Boolean escalated,
                                                       Boolean overdue,
                                                       LocalDate dateFrom,
                                                       LocalDate dateTo,
                                                       int page,
                                                       int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Complaint> result = complaintRepository.findAll(
                buildSpecification(keyword, ticketType, severity, status, assigneeId, customerId,
                        escalated, overdue, dateFrom, dateTo),
                pageable);
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public ComplaintDtos.TicketDetail detail(UUID ticketId) {
        Complaint ticket = requireTicket(ticketId);
        ComplaintDtos.TicketView view = toViews(List.of(ticket)).get(0);
        List<ComplaintAction> actions = actionRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        List<ComplaintReview> reviews = reviewRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        Set<UUID> userIds = new HashSet<>();
        actions.stream().map(ComplaintAction::getOperatorId).filter(java.util.Objects::nonNull).forEach(userIds::add);
        reviews.stream().map(ComplaintReview::getReviewerId).filter(java.util.Objects::nonNull).forEach(userIds::add);
        Map<UUID, String> userNames = userNames(userIds);

        List<ComplaintDtos.ActionView> actionViews = actions.stream()
                .map(action -> new ComplaintDtos.ActionView(action.getId(), action.getActionType(),
                        action.getFromStatus(), action.getToStatus(), action.getContent(),
                        action.getOperatorId(), userNames.get(action.getOperatorId()), action.getCreatedAt()))
                .toList();
        List<ComplaintDtos.RectificationView> rectificationViews =
                rectificationRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                        .map(item -> new ComplaintDtos.RectificationView(item.getId(), item.getContent(),
                                item.getResponsibleOrgId(), item.getDueAt(), item.getStatus(),
                                item.getSubmittedAt(), item.getVerifiedAt(), item.getVerifiedBy(),
                                item.getCreatedAt()))
                        .toList();
        List<ComplaintDtos.FeedbackView> feedbackViews =
                feedbackRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                        .map(item -> new ComplaintDtos.FeedbackView(item.getId(), item.getRating(),
                                item.isSatisfied(), item.getComment(), item.getChannel(), item.getCreatedAt()))
                        .toList();
        List<ComplaintDtos.ReviewView> reviewViews = reviews.stream()
                .map(item -> new ComplaintDtos.ReviewView(item.getId(), item.getReviewerId(),
                        userNames.get(item.getReviewerId()), item.getDecision(), item.getComment(),
                        item.getCreatedAt()))
                .toList();
        return new ComplaintDtos.TicketDetail(view, actionViews, rectificationViews, feedbackViews, reviewViews);
    }

    // ------------------------------------------------------------------
    // 建单与分派
    // ------------------------------------------------------------------
    @Transactional
    public ComplaintDtos.TicketView create(ComplaintDtos.CreateRequest request) {
        validateTicketType(request.ticketType());
        validateSeverity(request.severity());
        String source = request.source() == null || request.source().isBlank() ? "manual" : request.source();
        if (!SOURCES.contains(source)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的工单来源");
        }
        customerService.brief(request.customerId());
        validateAssignee(request.assigneeId());
        validateOrg(request.responsibleOrgId());

        Instant now = Instant.now();
        Complaint ticket = new Complaint();
        ticket.setTicketNo(String.format("CP%d", complaintRepository.nextTicketNo()));
        ticket.setCustomerId(request.customerId());
        ticket.setTicketType(request.ticketType());
        ticket.setSeverity(request.severity());
        ticket.setSubject(request.subject());
        ticket.setDescription(request.description());
        ticket.setSource(source);
        ticket.setCreatedBy(currentUserId());
        ticket.setResponsibleOrgId(request.responsibleOrgId());
        ticket.setDueAt(request.dueAt() == null
                ? now.plus(SLA_HOURS.getOrDefault(request.severity(), 48L), ChronoUnit.HOURS)
                : request.dueAt());
        if (request.assigneeId() != null) {
            ticket.setAssigneeId(request.assigneeId());
            ticket.setStatus("ASSIGNED");
        } else {
            ticket.setStatus("OPEN");
        }
        complaintRepository.save(ticket);

        writeAction(ticket, "CREATE", null, ticket.getStatus(), request.subject(), currentUserId());
        auditService.record("COMPLAINT_CREATE", RESOURCE, ticket.getTicketNo(),
                Map.of("customerId", ticket.getCustomerId().toString(),
                        "ticketType", ticket.getTicketType(),
                        "severity", ticket.getSeverity(),
                        "source", ticket.getSource()));
        return toViews(List.of(ticket)).get(0);
    }

    @Transactional
    public ComplaintDtos.TicketView assign(UUID ticketId, ComplaintDtos.AssignRequest request) {
        Complaint ticket = requireTicket(ticketId);
        if (!Set.of("OPEN", "ASSIGNED").contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅未开始处理的工单可分派或转派");
        }
        validateAssignee(request.assigneeId());
        validateOrg(request.responsibleOrgId());
        String fromStatus = ticket.getStatus();
        ticket.setAssigneeId(request.assigneeId());
        if (request.responsibleOrgId() != null) {
            ticket.setResponsibleOrgId(request.responsibleOrgId());
        }
        if (request.dueAt() != null) {
            ticket.setDueAt(request.dueAt());
        } else if (ticket.getDueAt() == null) {
            ticket.setDueAt(Instant.now().plus(
                    SLA_HOURS.getOrDefault(ticket.getSeverity(), 48L), ChronoUnit.HOURS));
        }
        ticket.setStatus("ASSIGNED");
        complaintRepository.save(ticket);

        writeAction(ticket, "ASSIGN", fromStatus, "ASSIGNED", "分派给处理人", currentUserId());
        auditService.record("COMPLAINT_ASSIGN", RESOURCE, ticket.getTicketNo(),
                Map.of("assigneeId", request.assigneeId().toString(),
                        "responsibleOrgId", request.responsibleOrgId() == null
                                ? "" : request.responsibleOrgId().toString()));
        return toViews(List.of(ticket)).get(0);
    }

    @Transactional
    public ComplaintDtos.TicketView start(UUID ticketId) {
        Complaint ticket = requireTicket(ticketId);
        if (!"ASSIGNED".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅已分派的工单可开始处理");
        }
        ticket.setStatus("PROCESSING");
        ticket.setStartedAt(Instant.now());
        complaintRepository.save(ticket);

        writeAction(ticket, "START", "ASSIGNED", "PROCESSING", "开始处理", currentUserId());
        auditService.record("COMPLAINT_START", RESOURCE, ticket.getTicketNo(), Map.of());
        return toViews(List.of(ticket)).get(0);
    }

    // ------------------------------------------------------------------
    // 处理动作
    // ------------------------------------------------------------------
    @Transactional
    public ComplaintDtos.TicketDetail addAction(UUID ticketId, ComplaintDtos.ActionCreateRequest request) {
        Complaint ticket = requireTicket(ticketId);
        requireNotTerminal(ticket, "已结案或已取消的工单不能登记处理动作");
        if (!ACTION_TYPES.contains(request.actionType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的处理动作类型");
        }
        writeAction(ticket, request.actionType(), ticket.getStatus(), ticket.getStatus(),
                request.content(), currentUserId());
        auditService.record("COMPLAINT_ACTION", RESOURCE, ticket.getTicketNo(),
                Map.of("actionType", request.actionType()));
        return detail(ticketId);
    }

    // ------------------------------------------------------------------
    // 整改
    // ------------------------------------------------------------------
    @Transactional
    public ComplaintDtos.TicketDetail addRectification(UUID ticketId,
                                                       ComplaintDtos.RectificationCreateRequest request) {
        Complaint ticket = requireTicket(ticketId);
        if (!Set.of("PROCESSING", "RECTIFYING").contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅处理中或整改中的工单可提交整改措施");
        }
        validateOrg(request.responsibleOrgId());
        ComplaintRectification rectification = new ComplaintRectification();
        rectification.setTicketId(ticketId);
        rectification.setContent(request.content());
        rectification.setResponsibleOrgId(request.responsibleOrgId() == null
                ? ticket.getResponsibleOrgId() : request.responsibleOrgId());
        rectification.setDueAt(request.dueAt());
        rectification.setStatus("PENDING");
        rectificationRepository.save(rectification);

        String fromStatus = ticket.getStatus();
        ticket.setStatus("RECTIFYING");
        complaintRepository.save(ticket);

        writeAction(ticket, "RECTIFY", fromStatus, "RECTIFYING", request.content(), currentUserId());
        auditService.record("COMPLAINT_RECTIFICATION_CREATE", RESOURCE, ticket.getTicketNo(),
                Map.of("rectificationId", rectification.getId() == null ? "" : rectification.getId().toString()));
        return detail(ticketId);
    }

    @Transactional
    public ComplaintDtos.TicketDetail submitRectification(UUID ticketId, UUID rectificationId) {
        Complaint ticket = requireTicket(ticketId);
        ComplaintRectification rectification = rectificationRepository.findById(rectificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "整改任务不存在"));
        if (!rectification.getTicketId().equals(ticketId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "整改任务不属于该工单");
        }
        if (!"PENDING".equals(rectification.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "该整改任务已提交，不能重复提交");
        }
        rectification.setStatus("SUBMITTED");
        rectification.setSubmittedAt(Instant.now());
        rectificationRepository.save(rectification);

        writeAction(ticket, "RECTIFY", ticket.getStatus(), ticket.getStatus(),
                "整改措施已提交", currentUserId());
        auditService.record("COMPLAINT_RECTIFICATION_SUBMIT", RESOURCE, ticket.getTicketNo(),
                Map.of("rectificationId", rectificationId.toString()));
        return detail(ticketId);
    }

    // ------------------------------------------------------------------
    // 患者反馈
    // ------------------------------------------------------------------
    @Transactional
    public ComplaintDtos.TicketDetail addFeedback(UUID ticketId, ComplaintDtos.FeedbackCreateRequest request) {
        Complaint ticket = requireTicket(ticketId);
        if (!Set.of("PROCESSING", "RECTIFYING", "PENDING_REVIEW", "CLOSED").contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "当前状态的工单暂不能登记患者反馈");
        }
        String channel = request.channel() == null || request.channel().isBlank() ? "manual" : request.channel();
        if (!FEEDBACK_CHANNELS.contains(channel)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的反馈渠道");
        }
        ComplaintFeedback feedback = new ComplaintFeedback();
        feedback.setTicketId(ticketId);
        feedback.setRating(request.rating());
        feedback.setSatisfied(Boolean.TRUE.equals(request.satisfied()));
        feedback.setComment(request.comment());
        feedback.setChannel(channel);
        feedbackRepository.save(feedback);

        writeAction(ticket, "FEEDBACK", ticket.getStatus(), ticket.getStatus(),
                String.format("患者反馈：%d 分，%s", request.rating(),
                        Boolean.TRUE.equals(request.satisfied()) ? "满意" : "不满意"), currentUserId());
        auditService.record("COMPLAINT_FEEDBACK", RESOURCE, ticket.getTicketNo(),
                Map.of("rating", request.rating(), "satisfied", Boolean.TRUE.equals(request.satisfied())));
        return detail(ticketId);
    }

    // ------------------------------------------------------------------
    // 复核与结案
    // ------------------------------------------------------------------
    @Transactional
    public ComplaintDtos.TicketView submitForReview(UUID ticketId) {
        Complaint ticket = requireTicket(ticketId);
        if (!Set.of("PROCESSING", "RECTIFYING").contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅处理中或整改中的工单可提交结案复核");
        }
        if (actionRepository.countByTicketId(ticketId) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "工单缺少处理记录，不能提交结案复核");
        }
        if (rectificationRepository.countByTicketIdAndStatus(ticketId, "PENDING") > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "存在未提交的整改任务，不能提交结案复核");
        }
        String fromStatus = ticket.getStatus();
        ticket.setStatus("PENDING_REVIEW");
        ticket.setSubmittedAt(Instant.now());
        complaintRepository.save(ticket);

        writeAction(ticket, "REVIEW", fromStatus, "PENDING_REVIEW", "提交结案复核", currentUserId());
        auditService.record("COMPLAINT_SUBMIT_REVIEW", RESOURCE, ticket.getTicketNo(), Map.of());
        return toViews(List.of(ticket)).get(0);
    }

    @Transactional
    public ComplaintDtos.TicketDetail review(UUID ticketId, ComplaintDtos.ReviewRequest request) {
        Complaint ticket = requireTicket(ticketId);
        if (!"PENDING_REVIEW".equals(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅待复核的工单可执行结案审核");
        }
        UUID reviewerId = CurrentUser.id();
        if (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "结案审核人不得为当前处理人");
        }
        boolean pass = "PASS".equals(request.decision());

        ComplaintReview review = new ComplaintReview();
        review.setTicketId(ticketId);
        review.setReviewerId(reviewerId);
        review.setDecision(request.decision());
        review.setComment(request.comment());
        reviewRepository.save(review);

        String fromStatus = ticket.getStatus();
        if (pass) {
            Instant now = Instant.now();
            ticket.setStatus("CLOSED");
            ticket.setClosedAt(now);
            ticket.setClosedBy(reviewerId);
            complaintRepository.save(ticket);
            rectificationRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                    .filter(item -> "SUBMITTED".equals(item.getStatus()))
                    .forEach(item -> {
                        item.setStatus("VERIFIED");
                        item.setVerifiedAt(now);
                        item.setVerifiedBy(reviewerId);
                        rectificationRepository.save(item);
                    });
            writeAction(ticket, "REVIEW", fromStatus, "CLOSED",
                    request.comment() == null ? "复核通过并结案" : request.comment(), reviewerId);
        } else {
            ticket.setStatus("PROCESSING");
            ticket.setSubmittedAt(null);
            complaintRepository.save(ticket);
            writeAction(ticket, "REVIEW", fromStatus, "PROCESSING",
                    request.comment() == null ? "复核退回重新处理" : request.comment(), reviewerId);
        }
        auditService.record("COMPLAINT_REVIEW", RESOURCE, ticket.getTicketNo(),
                Map.of("decision", request.decision()));
        return detail(ticketId);
    }

    // ------------------------------------------------------------------
    // 升级与取消
    // ------------------------------------------------------------------
    @Transactional
    public ComplaintDtos.TicketView escalate(UUID ticketId, ComplaintDtos.ReasonRequest request) {
        Complaint ticket = requireTicket(ticketId);
        requireNotTerminal(ticket, "已结案或已取消的工单不能升级");
        Instant now = Instant.now();
        ticket.setEscalated(true);
        ticket.setEscalationLevel(ticket.getEscalationLevel() + 1);
        ticket.setEscalatedAt(now);
        ticket.setEscalationReason(request.reason());
        complaintRepository.save(ticket);

        writeAction(ticket, "ESCALATE", ticket.getStatus(), ticket.getStatus(),
                request.reason(), currentUserId());
        auditService.record("COMPLAINT_ESCALATE", RESOURCE, ticket.getTicketNo(),
                Map.of("reason", request.reason(), "level", ticket.getEscalationLevel()));
        return toViews(List.of(ticket)).get(0);
    }

    @Transactional
    public ComplaintDtos.TicketView cancel(UUID ticketId, ComplaintDtos.ReasonRequest request) {
        Complaint ticket = requireTicket(ticketId);
        requireNotTerminal(ticket, "终态工单不能取消");
        String fromStatus = ticket.getStatus();
        ticket.setStatus("CANCELLED");
        ticket.setClosedAt(Instant.now());
        ticket.setClosedBy(currentUserId());
        ticket.setEscalationReason(request.reason());
        complaintRepository.save(ticket);

        writeAction(ticket, "CANCEL", fromStatus, "CANCELLED", request.reason(), currentUserId());
        auditService.record("COMPLAINT_CANCEL", RESOURCE, ticket.getTicketNo(),
                Map.of("reason", request.reason()));
        return toViews(List.of(ticket)).get(0);
    }

    /**
     * 超时未结案扫描：逐单条件更新标记升级，保证同一工单不会被重复升级。
     */
    @Transactional
    public int scanOverdue() {
        Instant now = Instant.now();
        String reason = "超过处理时限未结案，系统自动升级";
        List<UUID> overdueIds = complaintRepository.findOverdueIds(now);
        int escalatedCount = 0;
        for (UUID ticketId : overdueIds) {
            if (complaintRepository.markEscalated(ticketId, now, reason) == 0) {
                continue;
            }
            Complaint ticket = complaintRepository.findById(ticketId).orElse(null);
            if (ticket != null) {
                writeAction(ticket, "ESCALATE", ticket.getStatus(), ticket.getStatus(), reason, null);
            }
            escalatedCount++;
        }
        if (escalatedCount > 0) {
            auditService.record("COMPLAINT_ESCALATION_SCAN", RESOURCE, "OVERDUE",
                    Map.of("escalated", escalatedCount));
        }
        return escalatedCount;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private Specification<Complaint> buildSpecification(String keyword,
                                                        String ticketType,
                                                        String severity,
                                                        String status,
                                                        UUID assigneeId,
                                                        UUID customerId,
                                                        Boolean escalated,
                                                        Boolean overdue,
                                                        LocalDate dateFrom,
                                                        LocalDate dateTo) {
        Instant now = Instant.now();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ticketNo")), like),
                        cb.like(cb.lower(root.get("subject")), like)));
            }
            if (ticketType != null && !ticketType.isBlank()) {
                predicates.add(cb.equal(root.get("ticketType"), ticketType));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (escalated != null) {
                predicates.add(cb.equal(root.get("escalated"), escalated));
            }
            if (overdue != null) {
                if (overdue) {
                    predicates.add(cb.and(
                            cb.isNotNull(root.get("dueAt")),
                            cb.lessThan(root.get("dueAt"), now),
                            cb.not(root.get("status").in(TERMINAL_STATUSES))));
                } else {
                    predicates.add(cb.or(
                            cb.isNull(root.get("dueAt")),
                            cb.greaterThanOrEqualTo(root.get("dueAt"), now),
                            root.get("status").in(TERMINAL_STATUSES)));
                }
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<ComplaintDtos.TicketView> toViews(List<Complaint> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                tickets.stream().map(Complaint::getCustomerId).collect(Collectors.toSet()));
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> orgIds = new HashSet<>();
        for (Complaint ticket : tickets) {
            if (ticket.getAssigneeId() != null) {
                userIds.add(ticket.getAssigneeId());
            }
            if (ticket.getClosedBy() != null) {
                userIds.add(ticket.getClosedBy());
            }
            if (ticket.getResponsibleOrgId() != null) {
                orgIds.add(ticket.getResponsibleOrgId());
            }
        }
        Map<UUID, String> userNames = userNames(userIds);
        Map<UUID, String> orgNames = orgNames(orgIds);
        Instant now = Instant.now();
        return tickets.stream().map(ticket -> {
            CustomerDtos.CustomerBrief customer = customers.get(ticket.getCustomerId());
            boolean overdueFlag = ticket.getDueAt() != null && ticket.getDueAt().isBefore(now)
                    && !TERMINAL_STATUSES.contains(ticket.getStatus());
            return new ComplaintDtos.TicketView(
                    ticket.getId(),
                    ticket.getTicketNo(),
                    ticket.getCustomerId(),
                    customer == null ? null : customer.name(),
                    customer == null ? null : customer.phoneMasked(),
                    ticket.getTicketType(),
                    ticket.getSeverity(),
                    ticket.getSubject(),
                    ticket.getDescription(),
                    ticket.getStatus(),
                    ticket.getSource(),
                    ticket.getResponsibleOrgId(),
                    ticket.getResponsibleOrgId() == null ? null : orgNames.get(ticket.getResponsibleOrgId()),
                    ticket.getAssigneeId(),
                    ticket.getAssigneeId() == null ? null : userNames.get(ticket.getAssigneeId()),
                    ticket.getDueAt(),
                    overdueFlag,
                    ticket.isEscalated(),
                    ticket.getEscalationLevel(),
                    ticket.getEscalatedAt(),
                    ticket.getEscalationReason(),
                    ticket.getCreatedAt(),
                    ticket.getUpdatedAt(),
                    ticket.getClosedAt(),
                    ticket.getClosedBy(),
                    ticket.getClosedBy() == null ? null : userNames.get(ticket.getClosedBy()));
        }).toList();
    }

    private Map<UUID, String> userNames(Set<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        userRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), displayName(user)));
        return names;
    }

    private Map<UUID, String> orgNames(Set<UUID> orgIds) {
        Map<UUID, String> names = new HashMap<>();
        if (orgIds.isEmpty()) {
            return names;
        }
        orgRepository.findAllById(orgIds).forEach(org -> names.put(org.getId(), org.getOrgName()));
        return names;
    }

    private String displayName(SysUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    private void writeAction(Complaint ticket, String actionType, String fromStatus, String toStatus,
                             String content, UUID operatorId) {
        ComplaintAction action = new ComplaintAction();
        action.setTicketId(ticket.getId());
        action.setActionType(actionType);
        action.setFromStatus(fromStatus);
        action.setToStatus(toStatus);
        action.setContent(content);
        action.setOperatorId(operatorId);
        actionRepository.save(action);
    }

    private Complaint requireTicket(UUID ticketId) {
        return complaintRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工单不存在"));
    }

    private void requireNotTerminal(Complaint ticket, String message) {
        if (TERMINAL_STATUSES.contains(ticket.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, message);
        }
    }

    private void validateTicketType(String ticketType) {
        if (ticketType == null || !TICKET_TYPES.contains(ticketType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的工单类型");
        }
    }

    private void validateSeverity(String severity) {
        if (severity == null || !SEVERITIES.contains(severity)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的严重程度");
        }
    }

    private void validateAssignee(UUID assigneeId) {
        if (assigneeId != null && !userRepository.existsById(assigneeId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "处理人不存在");
        }
    }

    private void validateOrg(UUID orgId) {
        if (orgId != null && !orgRepository.existsById(orgId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "责任部门不存在");
        }
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
