package com.hospital.crm.complaint.application;

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
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 投诉表扬中心：建单 SLA、状态机、整改与复核分权、超时自动升级不重复。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComplaintServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID TICKET_ID = UUID.randomUUID();
    private static final UUID ASSIGNEE_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private ComplaintActionRepository actionRepository;
    @Mock
    private ComplaintRectificationRepository rectificationRepository;
    @Mock
    private ComplaintFeedbackRepository feedbackRepository;
    @Mock
    private ComplaintReviewRepository reviewRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private SysOrgRepository orgRepository;
    @Mock
    private AuditService auditService;

    private ComplaintService service;

    @BeforeEach
    void setUp() {
        service = new ComplaintService(complaintRepository, actionRepository, rectificationRepository,
                feedbackRepository, reviewRepository, customerService, userRepository, orgRepository,
                auditService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(REVIEWER_ID, "reviewer", "审核员", null, null, null,
                        Set.of(), Set.of(), List.of()), "", List.of()));

        when(complaintRepository.nextTicketNo()).thenReturn(100001L);
        when(complaintRepository.save(any(Complaint.class))).thenAnswer(invocation -> {
            Complaint ticket = invocation.getArgument(0);
            if (ticket.getId() == null) {
                ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
            }
            if (ticket.getCreatedAt() == null) {
                ReflectionTestUtils.setField(ticket, "createdAt", Instant.now());
            }
            return ticket;
        });
        when(actionRepository.save(any(ComplaintAction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rectificationRepository.save(any(ComplaintRectification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(feedbackRepository.save(any(ComplaintFeedback.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.save(any(ComplaintReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(actionRepository.findByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(rectificationRepository.findByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(feedbackRepository.findByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(reviewRepository.findByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(customerService.brief(any(UUID.class)))
                .thenReturn(new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", "138****1111", "active"));
        when(customerService.briefs(any()))
                .thenReturn(Map.of(CUSTOMER_ID, new CustomerDtos.CustomerBrief(
                        CUSTOMER_ID, "C001", "张三", "138****1111", "active")));
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(orgRepository.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAppliesSlaAndTicketNumber() {
        ComplaintDtos.TicketView view = service.create(new ComplaintDtos.CreateRequest(
                CUSTOMER_ID, "complaint", "high", "候诊时间过长", "患者反映候诊超过两小时",
                null, null, null, null));

        assertThat(view.ticketNo()).isEqualTo("CP100001");
        assertThat(view.status()).isEqualTo("OPEN");
        assertThat(view.source()).isEqualTo("manual");
        assertThat(view.customerName()).isEqualTo("张三");
        assertThat(view.dueAt()).isNotNull();
        assertThat(view.dueAt()).isBefore(Instant.now().plus(25, ChronoUnit.HOURS));
        assertThat(view.dueAt()).isAfter(Instant.now().plus(23, ChronoUnit.HOURS));
        verify(actionRepository).save(any(ComplaintAction.class));
    }

    @Test
    void createWithAssigneeStartsInAssignedStatus() {
        when(userRepository.existsById(ASSIGNEE_ID)).thenReturn(true);

        ComplaintDtos.TicketView view = service.create(new ComplaintDtos.CreateRequest(
                CUSTOMER_ID, "praise", "low", "表扬护士", "护士服务细致耐心",
                "phone", null, ASSIGNEE_ID, null));

        assertThat(view.status()).isEqualTo("ASSIGNED");
        assertThat(view.assigneeId()).isEqualTo(ASSIGNEE_ID);
        assertThat(view.severity()).isEqualTo("low");
    }

    @Test
    void createRejectsUnknownTicketType() {
        assertThatThrownBy(() -> service.create(new ComplaintDtos.CreateRequest(
                CUSTOMER_ID, "unknown", "normal", "标题", "描述", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的工单类型");
    }

    @Test
    void createRejectsUnknownSource() {
        assertThatThrownBy(() -> service.create(new ComplaintDtos.CreateRequest(
                CUSTOMER_ID, "complaint", "normal", "标题", "描述", "fax", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的工单来源");
    }

    @Test
    void assignRejectsProcessingTicket() {
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket("PROCESSING")));

        assertThatThrownBy(() -> service.assign(TICKET_ID, new ComplaintDtos.AssignRequest(ASSIGNEE_ID, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅未开始处理的工单");
    }

    @Test
    void startRequiresAssignedStatus() {
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket("OPEN")));

        assertThatThrownBy(() -> service.start(TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已分派的工单");
    }

    @Test
    void startMovesAssignedToProcessing() {
        Complaint ticket = ticket("ASSIGNED");
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        ComplaintDtos.TicketView view = service.start(TICKET_ID);

        assertThat(view.status()).isEqualTo("PROCESSING");
        assertThat(ticket.getStartedAt()).isNotNull();
    }

    @Test
    void addActionRejectsTerminalTicket() {
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket("CLOSED")));

        assertThatThrownBy(() -> service.addAction(TICKET_ID,
                new ComplaintDtos.ActionCreateRequest("CONTACT", "电话联系患者")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已结案或已取消");
    }

    @Test
    void addRectificationMovesTicketToRectifying() {
        Complaint ticket = ticket("PROCESSING");
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        ComplaintDtos.TicketDetail detail = service.addRectification(TICKET_ID,
                new ComplaintDtos.RectificationCreateRequest("优化候诊叫号流程", null, null));

        assertThat(ticket.getStatus()).isEqualTo("RECTIFYING");
        assertThat(detail.ticket().status()).isEqualTo("RECTIFYING");
    }

    @Test
    void submitForReviewRejectsPendingRectification() {
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket("RECTIFYING")));
        when(actionRepository.countByTicketId(TICKET_ID)).thenReturn(1L);
        when(rectificationRepository.countByTicketIdAndStatus(TICKET_ID, "PENDING")).thenReturn(1L);

        assertThatThrownBy(() -> service.submitForReview(TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未提交的整改任务");
    }

    @Test
    void submitForReviewRequiresHandlingRecords() {
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket("PROCESSING")));
        when(actionRepository.countByTicketId(TICKET_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.submitForReview(TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少处理记录");
    }

    @Test
    void submitForReviewMovesTicketToPendingReview() {
        Complaint ticket = ticket("PROCESSING");
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(actionRepository.countByTicketId(TICKET_ID)).thenReturn(2L);
        when(rectificationRepository.countByTicketIdAndStatus(TICKET_ID, "PENDING")).thenReturn(0L);

        ComplaintDtos.TicketView view = service.submitForReview(TICKET_ID);

        assertThat(view.status()).isEqualTo("PENDING_REVIEW");
        assertThat(ticket.getSubmittedAt()).isNotNull();
    }

    @Test
    void reviewRejectsSameAsAssignee() {
        Complaint ticket = ticket("PENDING_REVIEW");
        ticket.setAssigneeId(REVIEWER_ID);
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.review(TICKET_ID, new ComplaintDtos.ReviewRequest("PASS", "通过")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核人不得为当前处理人");
    }

    @Test
    void reviewPassClosesTicketAndVerifiesRectification() {
        Complaint ticket = ticket("PENDING_REVIEW");
        ticket.setAssigneeId(ASSIGNEE_ID);
        ComplaintRectification rectification = rectification("SUBMITTED");
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(rectificationRepository.findByTicketIdOrderByCreatedAtAsc(TICKET_ID))
                .thenReturn(List.of(rectification));

        ComplaintDtos.TicketDetail detail = service.review(TICKET_ID,
                new ComplaintDtos.ReviewRequest("PASS", "整改到位，同意结案"));

        assertThat(ticket.getStatus()).isEqualTo("CLOSED");
        assertThat(ticket.getClosedBy()).isEqualTo(REVIEWER_ID);
        assertThat(ticket.getClosedAt()).isNotNull();
        assertThat(rectification.getStatus()).isEqualTo("VERIFIED");
        assertThat(rectification.getVerifiedBy()).isEqualTo(REVIEWER_ID);
        assertThat(detail.ticket().status()).isEqualTo("CLOSED");
    }

    @Test
    void reviewRejectReturnsTicketToProcessing() {
        Complaint ticket = ticket("PENDING_REVIEW");
        ticket.setAssigneeId(ASSIGNEE_ID);
        ticket.setSubmittedAt(Instant.now());
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        ComplaintDtos.TicketDetail detail = service.review(TICKET_ID,
                new ComplaintDtos.ReviewRequest("REJECT", "整改措施不具体"));

        assertThat(ticket.getStatus()).isEqualTo("PROCESSING");
        assertThat(ticket.getSubmittedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
        assertThat(detail.ticket().status()).isEqualTo("PROCESSING");
    }

    @Test
    void cancelRejectsTerminalTicket() {
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket("CLOSED")));

        assertThatThrownBy(() -> service.cancel(TICKET_ID, new ComplaintDtos.ReasonRequest("重复工单")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("终态工单不能取消");
    }

    @Test
    void scanOverdueEscalatesOncePerTicket() {
        when(complaintRepository.findOverdueIds(any())).thenReturn(List.of(TICKET_ID));
        when(complaintRepository.markEscalated(eq(TICKET_ID), any(), any())).thenReturn(1);
        when(complaintRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket("PROCESSING")));

        int count = service.scanOverdue();

        assertThat(count).isEqualTo(1);
        verify(actionRepository).save(any(ComplaintAction.class));
        verify(auditService).record(eq("COMPLAINT_ESCALATION_SCAN"), any(), eq("OVERDUE"), any());
    }

    @Test
    void scanOverdueSkipsAlreadyEscalatedTicket() {
        when(complaintRepository.findOverdueIds(any())).thenReturn(List.of(TICKET_ID));
        when(complaintRepository.markEscalated(eq(TICKET_ID), any(), any())).thenReturn(0);

        int count = service.scanOverdue();

        assertThat(count).isZero();
        verify(actionRepository, never()).save(any(ComplaintAction.class));
    }

    @Test
    void listMapsCustomerAndOverdueFlag() {
        Complaint ticket = ticket("PROCESSING");
        ticket.setDueAt(Instant.now().minus(2, ChronoUnit.HOURS));
        Page<Complaint> page = new PageImpl<>(List.of(ticket));
        when(complaintRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<ComplaintDtos.TicketView> response =
                service.list(null, null, null, null, null, null, null, true, null, null, 1, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items().get(0).customerName()).isEqualTo("张三");
        assertThat(response.items().get(0).overdue()).isTrue();
    }

    private Complaint ticket(String status) {
        Complaint ticket = new Complaint();
        ReflectionTestUtils.setField(ticket, "id", TICKET_ID);
        ticket.setTicketNo("CP100001");
        ticket.setCustomerId(CUSTOMER_ID);
        ticket.setTicketType("complaint");
        ticket.setSeverity("normal");
        ticket.setSubject("候诊时间过长");
        ticket.setDescription("患者反映候诊超过两小时");
        ticket.setStatus(status);
        ticket.setSource("manual");
        ReflectionTestUtils.setField(ticket, "createdAt", Instant.now());
        ReflectionTestUtils.setField(ticket, "updatedAt", Instant.now());
        return ticket;
    }

    private ComplaintRectification rectification(String status) {
        ComplaintRectification rectification = new ComplaintRectification();
        ReflectionTestUtils.setField(rectification, "id", UUID.randomUUID());
        rectification.setTicketId(TICKET_ID);
        rectification.setContent("优化候诊叫号流程");
        rectification.setStatus(status);
        ReflectionTestUtils.setField(rectification, "createdAt", Instant.now());
        return rectification;
    }
}
