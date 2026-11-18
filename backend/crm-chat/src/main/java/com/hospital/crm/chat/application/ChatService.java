package com.hospital.crm.chat.application;

import com.hospital.crm.chat.api.ChatDtos;
import com.hospital.crm.chat.domain.ChatEvent;
import com.hospital.crm.chat.domain.ChatEventRepository;
import com.hospital.crm.chat.domain.ChatHandoff;
import com.hospital.crm.chat.domain.ChatHandoffRepository;
import com.hospital.crm.chat.domain.ChatMessage;
import com.hospital.crm.chat.domain.ChatMessageRepository;
import com.hospital.crm.chat.domain.ChatSession;
import com.hospital.crm.chat.domain.ChatSessionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 在线客服与人工工作台服务。
 *
 * 转人工规则：客户首次明确要求人工时仅提示确认并提供人工入口；第二次请求自动进入人工队列；
 * 投诉、紧急、敏感与 AI 低置信度可直接转人工。人工接管时必须携带完整会话上下文、患者授权范围与 AI 摘要。
 * 会话权限隔离：普通客服只能查看排队中的会话与本人接待的会话，具备管理权限的角色可查看全部历史。
 */
@Service
public class ChatService {

    private static final String RESOURCE = "CRM_CHAT";

    private static final Set<String> CHANNELS =
            Set.of("web", "h5", "mini_program", "app", "phone", "wechat");
    private static final Set<String> SENDER_TYPES = Set.of("CUSTOMER", "AI", "STAFF", "SYSTEM");
    private static final Set<String> CONTENT_TYPES = Set.of("text", "image", "file", "system");
    private static final Set<String> DIRECT_REASONS =
            Set.of("complaint", "urgent", "sensitive", "low_confidence");

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatHandoffRepository handoffRepository;
    private final ChatEventRepository eventRepository;
    private final CustomerService customerService;
    private final SysUserRepository userRepository;
    private final AuditService auditService;

    public ChatService(ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       ChatHandoffRepository handoffRepository,
                       ChatEventRepository eventRepository,
                       CustomerService customerService,
                       SysUserRepository userRepository,
                       AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.handoffRepository = handoffRepository;
        this.eventRepository = eventRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // 会话与历史
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<ChatDtos.SessionView> listSessions(String keyword,
                                                          String status,
                                                          String channel,
                                                          UUID customerId,
                                                          UUID assigneeId,
                                                          String priority,
                                                          LocalDate dateFrom,
                                                          LocalDate dateTo,
                                                          int page,
                                                          int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<ChatSession> result = sessionRepository.findAll(
                buildSpecification(keyword, status, channel, customerId, assigneeId, priority, dateFrom, dateTo),
                pageable);
        return PageResponse.of(toViews(result.getContent()), result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public ChatDtos.SessionDetail detail(UUID sessionId) {
        ChatSession session = requireSession(sessionId);
        assertAccess(session);
        List<ChatMessage> messages = new ArrayList<>(
                messageRepository.findTop200BySessionIdOrderByCreatedAtDesc(sessionId));
        messages.sort((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()));
        return buildDetail(session, messages);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChatDtos.MessageView> messages(UUID sessionId, int page, int pageSize) {
        ChatSession session = requireSession(sessionId);
        assertAccess(session);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<ChatMessage> result = messageRepository.findBySessionId(sessionId,
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.ASC, "createdAt")));
        Map<UUID, String> names = userNames(userIdsOfMessages(result.getContent()));
        List<ChatDtos.MessageView> items = result.getContent().stream()
                .map(message -> toMessageView(message, names))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.QueueItemView> queue() {
        List<ChatSession> sessions = sessionRepository.findQueue();
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = sessions.stream().map(ChatSession::getId).toList();
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                sessions.stream().map(ChatSession::getCustomerId).filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
        Map<UUID, String> lastMessages = lastMessageMap(ids);
        Map<UUID, String> reasons = new HashMap<>();
        handoffRepository.findBySessionIdInOrderByCreatedAtAsc(ids)
                .forEach(handoff -> reasons.put(handoff.getSessionId(), handoff.getReason()));
        Instant now = Instant.now();
        List<ChatDtos.QueueItemView> items = new ArrayList<>();
        int position = 1;
        for (ChatSession session : sessions) {
            CustomerDtos.CustomerBrief customer = customers.get(session.getCustomerId());
            items.add(new ChatDtos.QueueItemView(
                    session.getId(),
                    session.getSessionNo(),
                    customer == null ? null : customer.name(),
                    customer == null ? null : customer.phoneMasked(),
                    session.getChannel(),
                    session.getPriority(),
                    waitingSeconds(session, now),
                    position++,
                    lastMessages.get(session.getId()),
                    session.getAiSummary(),
                    reasons.get(session.getId())));
        }
        return items;
    }

    // ------------------------------------------------------------------
    // 会话创建与消息
    // ------------------------------------------------------------------
    @Transactional
    public ChatDtos.SessionDetail createSession(ChatDtos.CreateSessionRequest request) {
        if (!CHANNELS.contains(request.channel())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的会话渠道");
        }
        customerService.brief(request.customerId());

        ChatSession session = new ChatSession();
        session.setSessionNo(String.format("CH%d", sessionRepository.nextSessionNo()));
        session.setCustomerId(request.customerId());
        session.setChannel(request.channel());
        session.setStatus("AI");
        session.setCurrentHandlerType("ai");
        session.setPriority("normal");
        session.setAuthorizationScope(request.authorizationScope() == null || request.authorizationScope().isBlank()
                ? "basic" : request.authorizationScope());
        sessionRepository.save(session);

        ChatMessage greeting = new ChatMessage();
        greeting.setSessionId(session.getId());
        greeting.setSenderType("SYSTEM");
        greeting.setContentType("system");
        greeting.setContent("AI 接待已开启，如需人工服务请输入“转人工”。");
        messageRepository.save(greeting);
        session.setLastMessageAt(greeting.getCreatedAt() == null ? Instant.now() : greeting.getCreatedAt());
        sessionRepository.save(session);

        writeEvent(session, "CREATED", null, "AI", currentUserId(), "会话创建");
        auditService.record("CHAT_SESSION_CREATE", RESOURCE, session.getSessionNo(),
                Map.of("customerId", request.customerId().toString(), "channel", request.channel()));

        return buildDetail(session, List.of(greeting));
    }

    @Transactional
    public ChatDtos.SessionDetail sendMessage(UUID sessionId, ChatDtos.MessageCreateRequest request) {
        ChatSession session = requireSession(sessionId);
        if (!SENDER_TYPES.contains(request.senderType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的消息发送方");
        }
        String contentType = request.contentType() == null || request.contentType().isBlank()
                ? "text" : request.contentType();
        if (!CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的消息类型");
        }
        if ("CLOSED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已结束的会话不能再发送消息");
        }
        UUID senderId = null;
        if ("STAFF".equals(request.senderType())) {
            if (!"ACTIVE".equals(session.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "会话尚未被人工接管");
            }
            assertAssigneeOrManager(session);
            senderId = CurrentUser.id();
        } else if ("AI".equals(request.senderType()) && !"AI".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "AI 消息仅可在 AI 接待阶段写入");
        }

        if (request.clientMessageId() != null && !request.clientMessageId().isBlank()) {
            ChatMessage existing = messageRepository
                    .findBySessionIdAndClientMessageId(sessionId, request.clientMessageId()).orElse(null);
            if (existing != null) {
                return detail(sessionId);
            }
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType(request.senderType());
        message.setSenderId(senderId);
        message.setContent(request.content());
        message.setContentType(contentType);
        message.setAiConfidence(request.aiConfidence());
        message.setKnowledgeRefs(request.knowledgeRefs() == null ? List.of() : request.knowledgeRefs());
        message.setClientMessageId(request.clientMessageId());
        messageRepository.save(message);

        session.setLastMessageAt(message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt());
        sessionRepository.save(session);
        auditService.record("CHAT_MESSAGE_SEND", RESOURCE, session.getSessionNo(),
                Map.of("senderType", request.senderType(),
                        "contentType", contentType));
        return detail(sessionId);
    }

    // ------------------------------------------------------------------
    // 转人工
    // ------------------------------------------------------------------
    @Transactional
    public ChatDtos.HandoffResult requestHandoff(UUID sessionId, ChatDtos.HandoffRequest request) {
        ChatSession session = requireSession(sessionId);
        if ("CLOSED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "已结束的会话不能转人工");
        }
        if ("QUEUED".equals(session.getStatus())) {
            return new ChatDtos.HandoffResult(false, "会话已在人工队列中，请耐心等待客服接入",
                    queuePosition(sessionId), toViews(List.of(session)).get(0));
        }
        if (!"AI".equals(session.getStatus()) || !"ai".equals(session.getCurrentHandlerType())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "当前已有客服接待，无需重复转人工");
        }

        Instant now = Instant.now();
        int incremented = sessionRepository.incrementHandoffRequest(sessionId, now);
        if (incremented == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "当前会话状态已变化，请刷新后重试");
        }
        ChatSession refreshed = requireSession(sessionId);
        boolean direct = DIRECT_REASONS.contains(request.reason());
        boolean firstConfirm = "explicit".equals(request.reason()) && refreshed.getHandoffRequestCount() <= 1;

        if (firstConfirm) {
            recordHandoff(refreshed, "CONFIRM", "PENDING_CONFIRM", request.reason(), request.note(), null);
            writeEvent(refreshed, "HANDOFF_REQUESTED", "AI", "AI", currentUserId(),
                    "首次请求人工，等待客户确认");
            auditService.record("CHAT_HANDOFF_CONFIRM", RESOURCE, refreshed.getSessionNo(),
                    Map.of("reason", request.reason()));
            return new ChatDtos.HandoffResult(true,
                    "已收到人工服务请求，再次发送“转人工”将进入人工队列。",
                    null, toViews(List.of(refreshed)).get(0));
        }

        String priority = switch (request.reason()) {
            case "urgent" -> "urgent";
            case "complaint", "sensitive" -> "high";
            default -> "normal";
        };
        String summary = buildAiSummary(sessionId);
        int queued = sessionRepository.queueSession(sessionId, now, priority, summary);
        if (queued == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "会话已被其他请求转入人工队列");
        }
        ChatSession queuedSession = requireSession(sessionId);
        recordHandoff(queuedSession, direct ? "DIRECT" : "SECOND", "QUEUED",
                request.reason(), request.note(), null);
        writeEvent(queuedSession, "QUEUED", "AI", "QUEUED", currentUserId(),
                "转人工原因：" + request.reason());
        auditService.record("CHAT_HANDOFF_QUEUE", RESOURCE, queuedSession.getSessionNo(),
                Map.of("reason", request.reason(), "priority", priority));

        return new ChatDtos.HandoffResult(false, "已进入人工队列，正在为您分配客服",
                queuePosition(sessionId), toViews(List.of(queuedSession)).get(0));
    }

    // ------------------------------------------------------------------
    // 接管、转接与结束
    // ------------------------------------------------------------------
    @Transactional
    public ChatDtos.SessionDetail claim(UUID sessionId) {
        ChatSession session = requireSession(sessionId);
        if (!"QUEUED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "会话不在人工队列中或已被其他客服接管");
        }
        UUID staffId = CurrentUser.id();
        Instant now = Instant.now();
        int claimed = sessionRepository.claimSession(sessionId, staffId, now);
        if (claimed == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "会话已被其他客服接管");
        }
        ChatSession claimedSession = requireSession(sessionId);
        handoffRepository.findTop1BySessionIdAndStatusOrderByCreatedAtDesc(sessionId, "QUEUED")
                .ifPresent(handoff -> {
                    handoff.setStatus("ACCEPTED");
                    handoff.setToStaffId(staffId);
                    handoff.setAcceptedAt(now);
                    handoffRepository.save(handoff);
                });
        writeEvent(claimedSession, "CLAIMED", "QUEUED", "ACTIVE", staffId, "客服接管会话");
        auditService.record("CHAT_SESSION_CLAIM", RESOURCE, claimedSession.getSessionNo(),
                Map.of("staffId", staffId.toString()));
        return detail(sessionId);
    }

    @Transactional
    public ChatDtos.SessionDetail transfer(UUID sessionId, ChatDtos.TransferRequest request) {
        ChatSession session = requireSession(sessionId);
        if (!"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "仅人工接待中的会话可转接");
        }
        assertAssigneeOrManager(session);
        if (request.toStaffId().equals(session.getAssignedStaffId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能转接给当前接待客服");
        }
        if (!userRepository.existsById(request.toStaffId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标客服不存在");
        }
        UUID fromStaffId = session.getAssignedStaffId();
        Instant now = Instant.now();
        int transferred = sessionRepository.transferSession(sessionId, fromStaffId, request.toStaffId(), now);
        if (transferred == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "会话状态已变化，转接失败");
        }
        ChatSession transferredSession = requireSession(sessionId);
        ChatHandoff handoff = new ChatHandoff();
        handoff.setSessionId(sessionId);
        handoff.setRequestCount(transferredSession.getHandoffRequestCount());
        handoff.setReason("transfer");
        handoff.setMode("TRANSFER");
        handoff.setStatus("TRANSFERRED");
        handoff.setFromStaffId(fromStaffId);
        handoff.setToStaffId(request.toStaffId());
        handoff.setNote(request.note());
        handoffRepository.save(handoff);
        writeEvent(transferredSession, "TRANSFERRED", "ACTIVE", "ACTIVE", currentUserId(),
                request.note() == null ? "客服转接" : request.note());
        auditService.record("CHAT_SESSION_TRANSFER", RESOURCE, transferredSession.getSessionNo(),
                Map.of("fromStaffId", String.valueOf(fromStaffId),
                        "toStaffId", request.toStaffId().toString()));
        return detail(sessionId);
    }

    @Transactional
    public ChatDtos.SessionView close(UUID sessionId, ChatDtos.CloseRequest request) {
        ChatSession session = requireSession(sessionId);
        if ("CLOSED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "会话已结束");
        }
        if (session.getAssignedStaffId() != null) {
            assertAssigneeOrManager(session);
        }
        UUID staffId = CurrentUser.id();
        String fromStatus = session.getStatus();
        Instant now = Instant.now();
        int closed = sessionRepository.closeSession(sessionId, staffId, now);
        if (closed == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "会话状态已变化，无法结束");
        }
        ChatSession closedSession = requireSession(sessionId);
        writeEvent(closedSession, "CLOSED", fromStatus, "CLOSED", staffId,
                request.reason() == null ? "会话结束" : request.reason());
        auditService.record("CHAT_SESSION_CLOSE", RESOURCE, closedSession.getSessionNo(),
                Map.of("reason", request.reason() == null ? "" : request.reason()));
        return toViews(List.of(closedSession)).get(0);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private ChatDtos.SessionDetail buildDetail(ChatSession session, List<ChatMessage> messages) {
        Set<UUID> userIds = new HashSet<>();
        if (session.getAssignedStaffId() != null) {
            userIds.add(session.getAssignedStaffId());
        }
        if (session.getClosedBy() != null) {
            userIds.add(session.getClosedBy());
        }
        userIds.addAll(userIdsOfMessages(messages));
        List<ChatHandoff> handoffs = handoffRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        handoffs.forEach(handoff -> {
            if (handoff.getFromStaffId() != null) {
                userIds.add(handoff.getFromStaffId());
            }
            if (handoff.getToStaffId() != null) {
                userIds.add(handoff.getToStaffId());
            }
        });
        List<ChatEvent> events = eventRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        events.forEach(event -> {
            if (event.getOperatorId() != null) {
                userIds.add(event.getOperatorId());
            }
        });
        Map<UUID, String> names = userNames(userIds);
        ChatDtos.SessionView view = toViews(List.of(session)).get(0);

        List<ChatDtos.MessageView> messageViews = messages.stream()
                .map(message -> toMessageView(message, names))
                .toList();
        List<ChatDtos.HandoffView> handoffViews = handoffs.stream()
                .map(handoff -> new ChatDtos.HandoffView(handoff.getId(), handoff.getRequestCount(),
                        handoff.getReason(), handoff.getMode(), handoff.getStatus(),
                        handoff.getFromStaffId(), names.get(handoff.getFromStaffId()),
                        handoff.getToStaffId(), names.get(handoff.getToStaffId()),
                        handoff.getNote(), handoff.getRequestedAt(), handoff.getAcceptedAt()))
                .toList();
        List<ChatDtos.EventView> eventViews = events.stream()
                .map(event -> new ChatDtos.EventView(event.getId(), event.getEventType(),
                        event.getFromStatus(), event.getToStatus(), event.getOperatorId(),
                        names.get(event.getOperatorId()), event.getNote(), event.getCreatedAt()))
                .toList();
        return new ChatDtos.SessionDetail(view, messageViews, handoffViews, eventViews);
    }

    private ChatDtos.MessageView toMessageView(ChatMessage message, Map<UUID, String> names) {
        return new ChatDtos.MessageView(message.getId(), message.getSenderType(), message.getSenderId(),
                names.get(message.getSenderId()), message.getContent(), message.getContentType(),
                message.getAiConfidence(), message.getKnowledgeRefs(), message.getCreatedAt());
    }

    private List<ChatDtos.SessionView> toViews(List<ChatSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, CustomerDtos.CustomerBrief> customers = customerService.briefs(
                sessions.stream().map(ChatSession::getCustomerId).filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
        Set<UUID> userIds = new HashSet<>();
        sessions.forEach(session -> {
            if (session.getAssignedStaffId() != null) {
                userIds.add(session.getAssignedStaffId());
            }
            if (session.getClosedBy() != null) {
                userIds.add(session.getClosedBy());
            }
        });
        Map<UUID, String> names = userNames(userIds);
        Map<UUID, String> lastMessages = lastMessageMap(
                sessions.stream().map(ChatSession::getId).toList());
        Instant now = Instant.now();
        return sessions.stream().map(session -> {
            CustomerDtos.CustomerBrief customer = customers.get(session.getCustomerId());
            return new ChatDtos.SessionView(
                    session.getId(),
                    session.getSessionNo(),
                    session.getCustomerId(),
                    customer == null ? null : customer.name(),
                    customer == null ? null : customer.phoneMasked(),
                    session.getChannel(),
                    session.getStatus(),
                    session.getCurrentHandlerType(),
                    session.getPriority(),
                    session.getAssignedStaffId(),
                    names.get(session.getAssignedStaffId()),
                    session.getHandoffRequestCount(),
                    session.getQueueEnteredAt(),
                    session.getClaimedAt(),
                    session.getLastMessageAt(),
                    lastMessages.get(session.getId()),
                    session.getAiSummary(),
                    session.getAuthorizationScope(),
                    session.getStartedAt(),
                    session.getEndedAt(),
                    session.getClosedBy(),
                    names.get(session.getClosedBy()),
                    waitingSeconds(session, now));
        }).toList();
    }

    private Map<UUID, String> lastMessageMap(List<UUID> sessionIds) {
        Map<UUID, String> previews = new HashMap<>();
        if (sessionIds.isEmpty()) {
            return previews;
        }
        messageRepository.findLatestBySessionIds(sessionIds)
                .forEach(message -> previews.put(message.getSessionId(), truncate(message.getContent(), 60)));
        return previews;
    }

    private Map<UUID, String> userNames(Set<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        userRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), displayName(user)));
        return names;
    }

    private Set<UUID> userIdsOfMessages(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessage::getSenderId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String displayName(SysUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    private long waitingSeconds(ChatSession session, Instant now) {
        if (!"QUEUED".equals(session.getStatus()) || session.getQueueEnteredAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(session.getQueueEnteredAt(), now).toSeconds());
    }

    private Integer queuePosition(UUID sessionId) {
        List<ChatSession> queue = sessionRepository.findQueue();
        for (int index = 0; index < queue.size(); index++) {
            if (queue.get(index).getId().equals(sessionId)) {
                return index + 1;
            }
        }
        return null;
    }

    private String buildAiSummary(UUID sessionId) {
        List<ChatMessage> recent = new ArrayList<>(
                messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId));
        if (recent.isEmpty()) {
            return "AI 接待期间无对话记录";
        }
        recent.sort((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()));
        String body = recent.stream()
                .map(message -> senderLabel(message.getSenderType()) + "：" + truncate(message.getContent(), 60))
                .collect(Collectors.joining("；"));
        return truncate("AI 接待摘要（共 " + messageRepository.countBySessionId(sessionId) + " 条消息）：" + body, 500);
    }

    private String senderLabel(String senderType) {
        return switch (senderType) {
            case "CUSTOMER" -> "患者";
            case "AI" -> "AI";
            case "STAFF" -> "客服";
            default -> "系统";
        };
    }

    private ChatHandoff recordHandoff(ChatSession session, String mode, String status,
                                      String reason, String note, UUID toStaffId) {
        ChatHandoff handoff = new ChatHandoff();
        handoff.setSessionId(session.getId());
        handoff.setRequestCount(session.getHandoffRequestCount());
        handoff.setReason(reason);
        handoff.setMode(mode);
        handoff.setStatus(status);
        handoff.setToStaffId(toStaffId);
        handoff.setNote(note);
        return handoffRepository.save(handoff);
    }

    private void writeEvent(ChatSession session, String eventType, String fromStatus,
                            String toStatus, UUID operatorId, String note) {
        ChatEvent event = new ChatEvent();
        event.setSessionId(session.getId());
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperatorId(operatorId);
        event.setNote(note);
        eventRepository.save(event);
    }

    private ChatSession requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在"));
    }

    private void assertAccess(ChatSession session) {
        if (canManage()) {
            return;
        }
        UUID me = currentUserId();
        if (session.getAssignedStaffId() != null && session.getAssignedStaffId().equals(me)) {
            return;
        }
        if (session.getAssignedStaffId() == null
                && Set.of("AI", "QUEUED").contains(session.getStatus())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该会话");
    }

    private void assertAssigneeOrManager(ChatSession session) {
        if (canManage()) {
            return;
        }
        UUID me = currentUserId();
        if (session.getAssignedStaffId() == null || !session.getAssignedStaffId().equals(me)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅当前接待客服或主管可操作该会话");
        }
    }

    private boolean canManage() {
        return CurrentUser.hasPermission("crm:chat:manage");
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private Specification<ChatSession> buildSpecification(String keyword,
                                                         String status,
                                                         String channel,
                                                         UUID customerId,
                                                         UUID assigneeId,
                                                         String priority,
                                                         LocalDate dateFrom,
                                                         LocalDate dateTo) {
        UUID me = currentUserId();
        boolean manage = canManage();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!manage) {
                predicates.add(cb.or(
                        cb.equal(root.get("assignedStaffId"), me),
                        root.get("status").in("AI", "QUEUED")));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("sessionNo")),
                        "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assignedStaffId"), assigneeId));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"),
                        dateFrom.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("startedAt"),
                        dateTo.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
