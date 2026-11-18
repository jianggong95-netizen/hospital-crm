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
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线客服：首次确认/二次排队、直接转人工、并发接管、权限隔离与消息幂等。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID OTHER_STAFF_ID = UUID.randomUUID();

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatHandoffRepository handoffRepository;
    @Mock
    private ChatEventRepository eventRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private ChatService service;

    @BeforeEach
    void setUp() {
        service = new ChatService(sessionRepository, messageRepository, handoffRepository,
                eventRepository, customerService, userRepository, auditService);
        login(STAFF_ID, "crm:chat:read", "crm:chat:handle");

        when(sessionRepository.nextSessionNo()).thenReturn(500001L);
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                ReflectionTestUtils.setField(session, "id", SESSION_ID);
            }
            if (session.getStartedAt() == null) {
                ReflectionTestUtils.setField(session, "startedAt", Instant.now());
            }
            ReflectionTestUtils.setField(session, "updatedAt", Instant.now());
            return session;
        });
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
            }
            if (message.getCreatedAt() == null) {
                ReflectionTestUtils.setField(message, "createdAt", Instant.now());
            }
            return message;
        });
        when(handoffRepository.save(any(ChatHandoff.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any(ChatEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findTop200BySessionIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(messageRepository.findLatestBySessionIds(any())).thenReturn(List.of());
        when(messageRepository.countBySessionId(any())).thenReturn(0L);
        when(handoffRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(handoffRepository.findBySessionIdInOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(eventRepository.findBySessionIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(customerService.brief(any(UUID.class)))
                .thenReturn(new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", "138****1111", "active"));
        when(customerService.briefs(any()))
                .thenReturn(Map.of(CUSTOMER_ID, new CustomerDtos.CustomerBrief(
                        CUSTOMER_ID, "C001", "张三", "138****1111", "active")));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.existsById(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSessionGeneratesNumberAndGreeting() {
        ChatDtos.SessionDetail detail = service.createSession(
                new ChatDtos.CreateSessionRequest(CUSTOMER_ID, "h5", null));

        assertThat(detail.session().sessionNo()).isEqualTo("CH500001");
        assertThat(detail.session().status()).isEqualTo("AI");
        assertThat(detail.session().currentHandlerType()).isEqualTo("ai");
        assertThat(detail.session().authorizationScope()).isEqualTo("basic");
        verify(messageRepository).save(any(ChatMessage.class));
        verify(eventRepository).save(any(ChatEvent.class));
    }

    @Test
    void createSessionRejectsUnknownChannel() {
        assertThatThrownBy(() -> service.createSession(new ChatDtos.CreateSessionRequest(CUSTOMER_ID, "fax", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的会话渠道");
    }

    @Test
    void firstExplicitHandoffOnlyNeedsConfirmation() {
        ChatSession session = session("AI", 0, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.incrementHandoffRequest(eq(SESSION_ID), any())).thenAnswer(invocation -> {
            session.setHandoffRequestCount(session.getHandoffRequestCount() + 1);
            return 1;
        });

        ChatDtos.HandoffResult result = service.requestHandoff(SESSION_ID,
                new ChatDtos.HandoffRequest("explicit", "想找人工"));

        assertThat(result.needsConfirmation()).isTrue();
        assertThat(result.session().status()).isEqualTo("AI");
        assertThat(result.session().handoffRequestCount()).isEqualTo(1);
        verify(sessionRepository, never()).queueSession(any(), any(), anyString(), anyString());
        ArgumentCaptor<ChatHandoff> captor = ArgumentCaptor.forClass(ChatHandoff.class);
        verify(handoffRepository).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo("CONFIRM");
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING_CONFIRM");
    }

    @Test
    void secondExplicitHandoffEntersQueueWithSummary() {
        ChatSession session = session("AI", 1, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.incrementHandoffRequest(eq(SESSION_ID), any())).thenAnswer(invocation -> {
            session.setHandoffRequestCount(session.getHandoffRequestCount() + 1);
            return 1;
        });
        when(messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(SESSION_ID))
                .thenReturn(List.of(message("CUSTOMER", "我想咨询体检套餐")));
        when(messageRepository.countBySessionId(SESSION_ID)).thenReturn(3L);
        when(sessionRepository.queueSession(eq(SESSION_ID), any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    session.setStatus("QUEUED");
                    session.setCurrentHandlerType("human");
                    session.setQueueEnteredAt(Instant.now());
                    session.setPriority(invocation.getArgument(2));
                    session.setAiSummary(invocation.getArgument(3));
                    return 1;
                });
        when(sessionRepository.findQueue()).thenReturn(List.of(session));

        ChatDtos.HandoffResult result = service.requestHandoff(SESSION_ID,
                new ChatDtos.HandoffRequest("explicit", "再次要求人工"));

        assertThat(result.needsConfirmation()).isFalse();
        assertThat(result.queuePosition()).isEqualTo(1);
        assertThat(result.session().status()).isEqualTo("QUEUED");
        assertThat(result.session().aiSummary()).contains("AI 接待摘要");
        assertThat(result.session().priority()).isEqualTo("normal");
    }

    @Test
    void directReasonQueuesImmediatelyWithPriority() {
        ChatSession session = session("AI", 0, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.incrementHandoffRequest(eq(SESSION_ID), any())).thenAnswer(invocation -> {
            session.setHandoffRequestCount(session.getHandoffRequestCount() + 1);
            return 1;
        });
        when(sessionRepository.queueSession(eq(SESSION_ID), any(), eq("high"), anyString())).thenAnswer(invocation -> {
            session.setStatus("QUEUED");
            session.setPriority("high");
            session.setQueueEnteredAt(Instant.now());
            return 1;
        });
        when(sessionRepository.findQueue()).thenReturn(List.of(session));

        ChatDtos.HandoffResult result = service.requestHandoff(SESSION_ID,
                new ChatDtos.HandoffRequest("complaint", "患者要投诉"));

        assertThat(result.needsConfirmation()).isFalse();
        assertThat(result.session().priority()).isEqualTo("high");
        ArgumentCaptor<ChatHandoff> captor = ArgumentCaptor.forClass(ChatHandoff.class);
        verify(handoffRepository).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo("DIRECT");
    }

    @Test
    void handoffOnActiveSessionIsRejected() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACTIVE", 2, STAFF_ID)));

        assertThatThrownBy(() -> service.requestHandoff(SESSION_ID,
                new ChatDtos.HandoffRequest("explicit", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前已有客服接待");
    }

    @Test
    void claimRequiresQueuedStatus() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("AI", 0, null)));

        assertThatThrownBy(() -> service.claim(SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在人工队列");
    }

    @Test
    void claimConflictWhenAnotherStaffWinsRace() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("QUEUED", 2, null)));
        when(sessionRepository.claimSession(eq(SESSION_ID), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.claim(SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他客服接管");
    }

    @Test
    void claimSuccessKeepsAiContextAndAcceptsHandoff() {
        ChatSession session = session("QUEUED", 2, null);
        session.setAiSummary("AI 接待摘要：患者咨询体检套餐");
        session.setAuthorizationScope("appointment");
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.claimSession(eq(SESSION_ID), any(), any())).thenAnswer(invocation -> {
            session.setStatus("ACTIVE");
            session.setAssignedStaffId(STAFF_ID);
            session.setClaimedAt(Instant.now());
            return 1;
        });
        ChatHandoff queuedHandoff = new ChatHandoff();
        queuedHandoff.setSessionId(SESSION_ID);
        queuedHandoff.setStatus("QUEUED");
        when(handoffRepository.findTop1BySessionIdAndStatusOrderByCreatedAtDesc(SESSION_ID, "QUEUED"))
                .thenReturn(Optional.of(queuedHandoff));

        ChatDtos.SessionDetail detail = service.claim(SESSION_ID);

        assertThat(detail.session().status()).isEqualTo("ACTIVE");
        assertThat(detail.session().assignedStaffId()).isEqualTo(STAFF_ID);
        assertThat(detail.session().aiSummary()).contains("体检套餐");
        assertThat(detail.session().authorizationScope()).isEqualTo("appointment");
        assertThat(queuedHandoff.getStatus()).isEqualTo("ACCEPTED");
        assertThat(queuedHandoff.getAcceptedAt()).isNotNull();
    }

    @Test
    void transferOtherStaffSessionRequiresManagePermission() {
        ChatSession session = session("ACTIVE", 2, OTHER_STAFF_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.transfer(SESSION_ID,
                new ChatDtos.TransferRequest(UUID.randomUUID(), "转接")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅当前接待客服或主管");
    }

    @Test
    void managerTransferRecordsHandoff() {
        login(STAFF_ID, "crm:chat:read", "crm:chat:handle", "crm:chat:manage");
        ChatSession session = session("ACTIVE", 2, OTHER_STAFF_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.transferSession(eq(SESSION_ID), eq(OTHER_STAFF_ID), eq(STAFF_ID), any()))
                .thenAnswer(invocation -> {
                    session.setAssignedStaffId(STAFF_ID);
                    return 1;
                });

        ChatDtos.SessionDetail detail = service.transfer(SESSION_ID,
                new ChatDtos.TransferRequest(STAFF_ID, "由主管转接"));

        assertThat(detail.session().assignedStaffId()).isEqualTo(STAFF_ID);
        ArgumentCaptor<ChatHandoff> captor = ArgumentCaptor.forClass(ChatHandoff.class);
        verify(handoffRepository).save(captor.capture());
        assertThat(captor.getValue().getMode()).isEqualTo("TRANSFER");
        assertThat(captor.getValue().getStatus()).isEqualTo("TRANSFERRED");
        assertThat(captor.getValue().getFromStaffId()).isEqualTo(OTHER_STAFF_ID);
        assertThat(captor.getValue().getToStaffId()).isEqualTo(STAFF_ID);
    }

    @Test
    void closeOtherStaffSessionForbidden() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACTIVE", 2, OTHER_STAFF_ID)));

        assertThatThrownBy(() -> service.close(SESSION_ID, new ChatDtos.CloseRequest("结束")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅当前接待客服或主管");
    }

    @Test
    void closeSetClosedAndEndedAt() {
        ChatSession session = session("ACTIVE", 2, STAFF_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.closeSession(eq(SESSION_ID), any(), any())).thenAnswer(invocation -> {
            session.setStatus("CLOSED");
            session.setEndedAt(Instant.now());
            session.setClosedBy(STAFF_ID);
            return 1;
        });

        ChatDtos.SessionView view = service.close(SESSION_ID, new ChatDtos.CloseRequest("患者已离开"));

        assertThat(view.status()).isEqualTo("CLOSED");
        assertThat(session.getEndedAt()).isNotNull();
        verify(eventRepository).save(any(ChatEvent.class));
    }

    @Test
    void staffMessageRequiresActiveSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("QUEUED", 2, null)));

        assertThatThrownBy(() -> service.sendMessage(SESSION_ID, new ChatDtos.MessageCreateRequest(
                "STAFF", "您好", "text", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未被人工接管");
    }

    @Test
    void staffMessageOnOthersSessionForbidden() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACTIVE", 2, OTHER_STAFF_ID)));

        assertThatThrownBy(() -> service.sendMessage(SESSION_ID, new ChatDtos.MessageCreateRequest(
                "STAFF", "您好", "text", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅当前接待客服或主管");
    }

    @Test
    void sendingMessageUpdatesLastMessageAt() {
        ChatSession session = session("ACTIVE", 2, STAFF_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        service.sendMessage(SESSION_ID, new ChatDtos.MessageCreateRequest(
                "STAFF", "已为您预约明天上午", "text", null, null, null));

        assertThat(session.getLastMessageAt()).isNotNull();
        verify(messageRepository).save(any(ChatMessage.class));
    }

    @Test
    void duplicateClientMessageIdIsIdempotent() {
        ChatSession session = session("ACTIVE", 2, STAFF_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionIdAndClientMessageId(SESSION_ID, "m-001"))
                .thenReturn(Optional.of(message("CUSTOMER", "重复消息")));

        service.sendMessage(SESSION_ID, new ChatDtos.MessageCreateRequest(
                "CUSTOMER", "重复消息", "text", null, null, "m-001"));

        verify(messageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void aiMessageRejectedAfterHandoff() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("QUEUED", 2, null)));

        assertThatThrownBy(() -> service.sendMessage(SESSION_ID, new ChatDtos.MessageCreateRequest(
                "AI", "AI 回复", "text", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 接待阶段");
    }

    @Test
    void detailIsolationForOtherStaffSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACTIVE", 2, OTHER_STAFF_ID)));

        assertThatThrownBy(() -> service.detail(SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权查看该会话");
    }

    @Test
    void queuedSessionVisibleToHandlers() {
        ChatSession session = session("QUEUED", 2, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ChatDtos.SessionDetail detail = service.detail(SESSION_ID);

        assertThat(detail.session().status()).isEqualTo("QUEUED");
    }

    @Test
    void queueListsWaitingSecondsAndPosition() {
        ChatSession first = session("QUEUED", 2, null);
        first.setQueueEnteredAt(Instant.now().minus(90, ChronoUnit.SECONDS));
        ChatSession second = session("QUEUED", 1, null);
        ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(second, "sessionNo", "CH500002");
        second.setQueueEnteredAt(Instant.now().minus(10, ChronoUnit.SECONDS));
        when(sessionRepository.findQueue()).thenReturn(List.of(first, second));

        List<ChatDtos.QueueItemView> items = service.queue();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).queuePosition()).isEqualTo(1);
        assertThat(items.get(0).waitingSeconds()).isGreaterThanOrEqualTo(80);
        assertThat(items.get(1).queuePosition()).isEqualTo(2);
    }

    private void login(UUID userId, String... permissions) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "staff", "客服", null, null, null,
                        Set.of(), Set.of(), List.of()), "", authorities));
    }

    private ChatSession session(String status, int handoffCount, UUID assigneeId) {
        ChatSession session = new ChatSession();
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        session.setSessionNo("CH500001");
        session.setCustomerId(CUSTOMER_ID);
        session.setChannel("h5");
        session.setStatus(status);
        session.setCurrentHandlerType("AI".equals(status) ? "ai" : "human");
        session.setPriority("normal");
        session.setHandoffRequestCount(handoffCount);
        session.setAssignedStaffId(assigneeId);
        session.setAuthorizationScope("basic");
        ReflectionTestUtils.setField(session, "startedAt", Instant.now());
        ReflectionTestUtils.setField(session, "updatedAt", Instant.now());
        return session;
    }

    private ChatMessage message(String senderType, String content) {
        ChatMessage message = new ChatMessage();
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        message.setSessionId(SESSION_ID);
        message.setSenderType(senderType);
        message.setContent(content);
        message.setContentType("text");
        ReflectionTestUtils.setField(message, "createdAt", Instant.now());
        return message;
    }
}
