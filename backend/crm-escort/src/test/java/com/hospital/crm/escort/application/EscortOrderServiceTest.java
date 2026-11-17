package com.hospital.crm.escort.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.escort.api.EscortDtos;
import com.hospital.crm.escort.domain.Escort;
import com.hospital.crm.escort.domain.EscortOrder;
import com.hospital.crm.escort.domain.EscortOrderRepository;
import com.hospital.crm.escort.domain.EscortQualification;
import com.hospital.crm.escort.domain.EscortQualificationRepository;
import com.hospital.crm.escort.domain.EscortRepository;
import com.hospital.crm.escort.domain.EscortReview;
import com.hospital.crm.escort.domain.EscortReviewRepository;
import com.hospital.crm.escort.domain.EscortSchedule;
import com.hospital.crm.escort.domain.EscortScheduleRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.UserPrincipal;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 陪诊订单状态机与派单校验测试。使用虚构数据，手机号仅断言脱敏与密文列。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscortOrderServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CUSTOMER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ESCORT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REVIEW_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final LocalDate SERVICE_DATE = LocalDate.now().plusDays(1);

    @Mock
    private EscortOrderRepository orderRepository;
    @Mock
    private EscortRepository escortRepository;
    @Mock
    private EscortQualificationRepository qualificationRepository;
    @Mock
    private EscortScheduleRepository scheduleRepository;
    @Mock
    private EscortReviewRepository reviewRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SensitiveDataCipher cipher;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private AuditService auditService;

    private EscortOrderService service;

    @BeforeEach
    void setUp() {
        service = new EscortOrderService(orderRepository, escortRepository, qualificationRepository,
                scheduleRepository, reviewRepository, customerService, cipher, hasher, auditService);
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "tester", "测试员",
                UUID.randomUUID(), "ROOT", "/", Set.of("CRM_AGENT"),
                Set.of("crm:escortorder:manage"), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(hasher.hash(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(orderRepository.nextOrderNo()).thenReturn(100000L);
        when(orderRepository.save(any(EscortOrder.class))).thenAnswer(invocation -> {
            EscortOrder saved = invocation.getArgument(0);
            return saved.getId() == null ? withId(saved, ORDER_ID) : saved;
        });
        when(orderRepository.findConflictingOrders(any(), any(), anyCollection(), any(), any(), any()))
                .thenReturn(List.of());
        when(reviewRepository.save(any(EscortReview.class))).thenAnswer(invocation -> {
            EscortReview saved = invocation.getArgument(0);
            return saved.getId() == null ? withId(saved, REVIEW_ID) : saved;
        });
        when(customerService.brief(CUSTOMER_ID)).thenReturn(new CustomerDtos.CustomerBrief(
                CUSTOMER_ID, "C100000", "王先生", "138****0008", "vip"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createGeneratesOrderNoPendingAndEncryptsPhone() {
        EscortDtos.OrderSummary summary = service.create(new EscortDtos.OrderCreateRequest(
                CUSTOMER_ID, null, SERVICE_DATE, LocalTime.of(9, 0), LocalTime.of(12, 0),
                "OUTPATIENT", "13900000009", "需要轮椅"));

        assertEquals("E100000", summary.orderNo());
        assertEquals("PENDING", summary.status());
        assertEquals("王先生", summary.customerName());

        ArgumentCaptor<EscortOrder> captor = ArgumentCaptor.forClass(EscortOrder.class);
        verify(orderRepository).save(captor.capture());
        assertEquals("enc:13900000009", captor.getValue().getContactPhoneEncrypted());
        assertEquals("hash:13900000009", captor.getValue().getContactPhoneHash());
        assertEquals("139****0009", captor.getValue().getContactPhoneMasked());
        verify(auditService).record(eq("ESCORTORDER_CREATE"), eq("ESCORT_ORDER"), anyString(), any());
    }

    @Test
    void createRejectsPastServiceDate() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new EscortDtos.OrderCreateRequest(CUSTOMER_ID, null, LocalDate.now().minusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(12, 0), "OUTPATIENT", null, null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void createPropagatesUnknownCustomer() {
        when(customerService.brief(CUSTOMER_ID))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "客户不存在"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new EscortDtos.OrderCreateRequest(CUSTOMER_ID, null, SERVICE_DATE,
                        LocalTime.of(9, 0), LocalTime.of(12, 0), "OUTPATIENT", null, null)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void dispatchSuccessSetsAssigned() {
        EscortOrder order = order("PENDING", null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.of(escort("active")));
        when(qualificationRepository.findByEscortIdAndStatus(ESCORT_ID, "active"))
                .thenReturn(List.of(qualification(null)));
        when(scheduleRepository.findByEscortIdAndWorkDate(ESCORT_ID, SERVICE_DATE))
                .thenReturn(List.of(schedule(LocalTime.of(8, 0), LocalTime.of(16, 0))));

        EscortDtos.OrderSummary summary = service.dispatch(ORDER_ID,
                new EscortDtos.DispatchRequest(ESCORT_ID));

        assertEquals("ASSIGNED", summary.status());
        assertEquals(ESCORT_ID, summary.escortId());
        assertNotNull(summary.escortName());
        verify(auditService).record(eq("ESCORTORDER_DISPATCH"), eq("ESCORT_ORDER"), anyString(), any());
    }

    @Test
    void dispatchRejectsNonPendingOrder() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("ACCEPTED", ESCORT_ID)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(ORDER_ID,
                new EscortDtos.DispatchRequest(ESCORT_ID)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void dispatchUnknownEscortReturnsNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PENDING", null)));
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(ORDER_ID,
                new EscortDtos.DispatchRequest(ESCORT_ID)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void dispatchRejectsDisabledEscort() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PENDING", null)));
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.of(escort("disabled")));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(ORDER_ID,
                new EscortDtos.DispatchRequest(ESCORT_ID)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void dispatchRejectsEscortWithoutValidQualification() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PENDING", null)));
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.of(escort("active")));
        when(qualificationRepository.findByEscortIdAndStatus(ESCORT_ID, "active"))
                .thenReturn(List.of(qualification(SERVICE_DATE.minusDays(1))));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(ORDER_ID,
                new EscortDtos.DispatchRequest(ESCORT_ID)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        assertEquals("陪诊师无有效资质", ex.getMessage());
    }

    @Test
    void dispatchRejectsNoCoveringSchedule() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PENDING", null)));
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.of(escort("active")));
        when(qualificationRepository.findByEscortIdAndStatus(ESCORT_ID, "active"))
                .thenReturn(List.of(qualification(null)));
        when(scheduleRepository.findByEscortIdAndWorkDate(ESCORT_ID, SERVICE_DATE))
                .thenReturn(List.of(schedule(LocalTime.of(9, 0), LocalTime.of(11, 0))));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(ORDER_ID,
                new EscortDtos.DispatchRequest(ESCORT_ID)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        assertEquals("陪诊师该服务时段无覆盖排班", ex.getMessage());
    }

    @Test
    void dispatchRejectsConflictingOrder() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PENDING", null)));
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.of(escort("active")));
        when(qualificationRepository.findByEscortIdAndStatus(ESCORT_ID, "active"))
                .thenReturn(List.of(qualification(null)));
        when(scheduleRepository.findByEscortIdAndWorkDate(ESCORT_ID, SERVICE_DATE))
                .thenReturn(List.of(schedule(LocalTime.of(8, 0), LocalTime.of(16, 0))));
        when(orderRepository.findConflictingOrders(any(), any(), anyCollection(), any(), any(), any()))
                .thenReturn(List.of(withId(order("ASSIGNED", ESCORT_ID), UUID.randomUUID())));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.dispatch(ORDER_ID,
                new EscortDtos.DispatchRequest(ESCORT_ID)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        assertEquals("陪诊师该时段已有其他订单", ex.getMessage());
    }

    @Test
    void acceptSetsAcceptedAt() {
        EscortOrder order = order("ASSIGNED", ESCORT_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        EscortDtos.OrderSummary summary = service.accept(ORDER_ID);

        assertEquals("ACCEPTED", summary.status());
        assertNotNull(order.getAcceptedAt());
        verify(auditService).record(eq("ESCORTORDER_ACCEPT"), eq("ESCORT_ORDER"), anyString(), any());
    }

    @Test
    void rejectReturnsToPendingAndKeepsEscort() {
        EscortOrder order = order("ASSIGNED", ESCORT_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        EscortDtos.OrderSummary summary = service.reject(ORDER_ID,
                new EscortDtos.ReasonRequest("临时有培训无法接单"));

        assertEquals("PENDING", summary.status());
        assertEquals(ESCORT_ID, order.getEscortId());
        assertEquals("临时有培训无法接单", order.getRejectReason());
        verify(auditService).record(eq("ESCORTORDER_REJECT"), eq("ESCORT_ORDER"), anyString(), any());
    }

    @Test
    void startSetsActualStartAt() {
        EscortOrder order = order("ACCEPTED", ESCORT_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        EscortDtos.OrderSummary summary = service.start(ORDER_ID);

        assertEquals("IN_PROGRESS", summary.status());
        assertNotNull(order.getActualStartAt());
    }

    @Test
    void finishComputesDurationAndAppendsRemark() {
        EscortOrder order = order("IN_PROGRESS", ESCORT_ID);
        order.setActualStartAt(Instant.now().minusSeconds(90));
        order.setRemark("上午陪诊");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        EscortDtos.OrderSummary summary = service.finish(ORDER_ID,
                new EscortDtos.FinishRequest("客户取药完成"));

        assertEquals("COMPLETED", summary.status());
        assertEquals(2, order.getDurationMinutes());
        assertEquals("上午陪诊；客户取药完成", order.getRemark());
        assertNotNull(order.getActualEndAt());
        verify(auditService).record(eq("ESCORTORDER_FINISH"), eq("ESCORT_ORDER"), anyString(), any());
    }

    @Test
    void cancelInProgressRejectedWithHint() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("IN_PROGRESS", ESCORT_ID)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(ORDER_ID,
                new EscortDtos.ReasonRequest("客户临时改变计划")));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        assertEquals("服务已开始，请使用完成", ex.getMessage());
    }

    @Test
    void cancelPendingSucceedsAndStoresReason() {
        EscortOrder order = order("PENDING", null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        EscortDtos.OrderSummary summary = service.cancel(ORDER_ID,
                new EscortDtos.ReasonRequest("重复下单"));

        assertEquals("CANCELLED", summary.status());
        assertEquals("重复下单", order.getCancelReason());
        verify(auditService).record(eq("ESCORTORDER_CANCEL"), eq("ESCORT_ORDER"), anyString(), any());
    }

    @Test
    void terminalStateChangeRejected() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("COMPLETED", ESCORT_ID)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.accept(ORDER_ID));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        verify(orderRepository, never()).save(any(EscortOrder.class));
    }

    @Test
    void reviewRejectsNonCompletedOrder() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("ACCEPTED", ESCORT_ID)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.review(ORDER_ID,
                new EscortDtos.ReviewRequest(5, null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void reviewRejectsDuplicate() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("COMPLETED", ESCORT_ID)));
        when(reviewRepository.existsByOrderId(ORDER_ID)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.review(ORDER_ID,
                new EscortDtos.ReviewRequest(5, "服务很好")));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
    }

    @Test
    void reviewPersistsWithCurrentUserAndAudits() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("COMPLETED", ESCORT_ID)));

        EscortDtos.OrderReviewView view = service.review(ORDER_ID,
                new EscortDtos.ReviewRequest(4, "服务不错"));

        assertEquals(4, view.rating());
        assertEquals("tester", view.createdBy());
        ArgumentCaptor<EscortReview> captor = ArgumentCaptor.forClass(EscortReview.class);
        verify(reviewRepository).save(captor.capture());
        assertEquals(ESCORT_ID, captor.getValue().getEscortId());
        assertEquals(ORDER_ID, captor.getValue().getOrderId());
        verify(auditService).record(eq("ESCORTORDER_REVIEW"), eq("ESCORT_ORDER"), anyString(), any());
    }

    @Test
    void detailWithoutReviewReturnsNullReview() {
        EscortOrder order = order("PENDING", null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(reviewRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        EscortDtos.OrderDetail detail = service.detail(ORDER_ID);

        assertNull(detail.review());
        assertNull(detail.summary().escortName());
    }

    private EscortOrder order(String status, UUID escortId) {
        EscortOrder order = new EscortOrder();
        order.setOrderNo("E100000");
        order.setCustomerId(CUSTOMER_ID);
        order.setEscortId(escortId);
        order.setServiceDate(SERVICE_DATE);
        order.setStartTime(LocalTime.of(9, 0));
        order.setEndTime(LocalTime.of(12, 0));
        order.setArea("OUTPATIENT");
        order.setStatus(status);
        return withId(order, ORDER_ID);
    }

    private Escort escort(String status) {
        Escort escort = new Escort();
        escort.setEscortNo("ES100000");
        escort.setName("张护士");
        escort.setStatus(status);
        return withId(escort, ESCORT_ID);
    }

    private EscortQualification qualification(LocalDate expiresAt) {
        EscortQualification qualification = new EscortQualification();
        qualification.setEscortId(ESCORT_ID);
        qualification.setQualType("nurse");
        qualification.setCertNo("N2020-888");
        qualification.setIssuedAt(LocalDate.now().minusYears(2));
        qualification.setExpiresAt(expiresAt);
        qualification.setStatus("active");
        return qualification;
    }

    private EscortSchedule schedule(LocalTime start, LocalTime end) {
        EscortSchedule schedule = new EscortSchedule();
        schedule.setEscortId(ESCORT_ID);
        schedule.setWorkDate(SERVICE_DATE);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        return schedule;
    }

    private static <T> T withId(T entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }
}
