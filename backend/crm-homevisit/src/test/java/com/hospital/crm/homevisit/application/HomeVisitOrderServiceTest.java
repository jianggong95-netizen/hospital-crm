package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeAddress;
import com.hospital.crm.homevisit.domain.HomeAddressRepository;
import com.hospital.crm.homevisit.domain.HomeService;
import com.hospital.crm.homevisit.domain.HomeServiceRepository;
import com.hospital.crm.homevisit.domain.HomeStaffQualification;
import com.hospital.crm.homevisit.domain.HomeStaffQualificationRepository;
import com.hospital.crm.homevisit.domain.HomeVisitException;
import com.hospital.crm.homevisit.domain.HomeVisitExceptionRepository;
import com.hospital.crm.homevisit.domain.HomeVisitExecution;
import com.hospital.crm.homevisit.domain.HomeVisitExecutionRepository;
import com.hospital.crm.homevisit.domain.HomeVisitOrder;
import com.hospital.crm.homevisit.domain.HomeVisitOrderRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.user.SysUser;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeVisitOrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID EXCEPTION_ID = UUID.randomUUID();

    @Mock
    private HomeVisitOrderRepository orderRepository;

    @Mock
    private HomeServiceRepository serviceRepository;

    @Mock
    private HomeAddressRepository addressRepository;

    @Mock
    private HomeStaffQualificationRepository qualificationRepository;

    @Mock
    private HomeVisitExecutionRepository executionRepository;

    @Mock
    private HomeVisitExceptionRepository exceptionRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private SysUserRepository userRepository;

    @Mock
    private AuditService auditService;

    private HomeVisitOrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new HomeVisitOrderService(orderRepository, serviceRepository, addressRepository,
                qualificationRepository, executionRepository, exceptionRepository,
                customerService, userRepository, auditService);
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "nurse01", "护士甲",
                UUID.randomUUID(), "ROOT", "/", Set.of("USER"),
                Set.of("crm:homevisit:write"), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));

        when(orderRepository.save(any(HomeVisitOrder.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0)));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PENDING")));
        when(orderRepository.nextOrderNo()).thenReturn(100000L);
        when(serviceRepository.findByServiceCode("HOME_INJECTION"))
                .thenReturn(Optional.of(service("HOME_INJECTION", "published", "nurse")));
        when(customerService.brief(CUSTOMER_ID)).thenReturn(brief());
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address()));
        when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff()));
        when(qualificationRepository.findByUserIdAndQualTypeAndStatus(STAFF_ID, "nurse", "active"))
                .thenReturn(List.of(qualification(LocalDate.now().plusYears(1))));
        when(executionRepository.save(any(HomeVisitExecution.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0)));
        when(exceptionRepository.save(any(HomeVisitException.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0)));
        when(exceptionRepository.findById(EXCEPTION_ID))
                .thenReturn(Optional.of(exception(EXCEPTION_ID, "OPEN")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsPendingOrderWithGeneratedNoAndAudits() {
        HomeVisitDtos.OrderSummary summary = orderService.create(new HomeVisitDtos.OrderCreateRequest(
                "HOME_INJECTION", CUSTOMER_ID, ADDRESS_ID, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), "备注"));

        ArgumentCaptor<HomeVisitOrder> captor = ArgumentCaptor.forClass(HomeVisitOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getOrderNo()).isEqualTo("H100000");
        assertThat(captor.getValue().getServiceCode()).isEqualTo("HOME_INJECTION");
        assertThat(summary.orderNo()).isEqualTo("H100000");
        assertThat(summary.serviceName()).isEqualTo("居家注射");
        assertThat(summary.customerName()).isEqualTo("张三");
        verify(auditService).record(eq("HV_ORDER_CREATE"), eq("HOME_VISIT_ORDER"), any(), any());
    }

    @Test
    void createRejectsUnpublishedService() {
        when(serviceRepository.findByServiceCode("HOME_INJECTION"))
                .thenReturn(Optional.of(service("HOME_INJECTION", "draft", "nurse")));

        assertThatThrownBy(() -> orderService.create(new HomeVisitDtos.OrderCreateRequest(
                "HOME_INJECTION", CUSTOMER_ID, ADDRESS_ID, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务不存在或未发布");
    }

    @Test
    void createRejectsAddressOfAnotherCustomer() {
        HomeAddress other = address();
        other.setCustomerId(UUID.randomUUID());
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> orderService.create(new HomeVisitDtos.OrderCreateRequest(
                "HOME_INJECTION", CUSTOMER_ID, ADDRESS_ID, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务地址无效");
    }

    @Test
    void createRejectsDisabledAddress() {
        HomeAddress disabled = address();
        disabled.setStatus("disabled");
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> orderService.create(new HomeVisitDtos.OrderCreateRequest(
                "HOME_INJECTION", CUSTOMER_ID, ADDRESS_ID, LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务地址无效");
    }

    @Test
    void createRejectsPastDate() {
        assertThatThrownBy(() -> orderService.create(new HomeVisitDtos.OrderCreateRequest(
                "HOME_INJECTION", CUSTOMER_ID, ADDRESS_ID, LocalDate.now().minusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("预约日期不能早于今天");
    }

    @Test
    void reviewApprovesPendingOrder() {
        HomeVisitDtos.OrderSummary summary = orderService.review(ORDER_ID,
                new HomeVisitDtos.OrderReviewRequest(true, null));

        assertThat(summary.status()).isEqualTo("APPROVED");
        verify(auditService).record(eq("HV_ORDER_REVIEW"), eq("HOME_VISIT_ORDER"), any(), any());
    }

    @Test
    void reviewRejectRequiresReason() {
        assertThatThrownBy(() -> orderService.review(ORDER_ID,
                new HomeVisitDtos.OrderReviewRequest(false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("驳回订单必须填写原因");

        HomeVisitDtos.OrderSummary summary = orderService.review(ORDER_ID,
                new HomeVisitDtos.OrderReviewRequest(false, "时间冲突"));
        assertThat(summary.status()).isEqualTo("REJECTED");
    }

    @Test
    void reviewNonPendingOrderReturns422() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("APPROVED")));

        assertThatThrownBy(() -> orderService.review(ORDER_ID,
                new HomeVisitDtos.OrderReviewRequest(true, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态 APPROVED 不能审核");
    }

    @Test
    void dispatchRejectsUnqualifiedStaff() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("APPROVED")));
        when(qualificationRepository.findByUserIdAndQualTypeAndStatus(STAFF_ID, "nurse", "active"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> orderService.dispatch(ORDER_ID,
                new HomeVisitDtos.DispatchRequest(STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上门服务人员不具备该项目的有效资质");
    }

    @Test
    void dispatchRejectsExpiredQualification() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("APPROVED")));
        when(qualificationRepository.findByUserIdAndQualTypeAndStatus(STAFF_ID, "nurse", "active"))
                .thenReturn(List.of(qualification(LocalDate.now().minusDays(1))));

        assertThatThrownBy(() -> orderService.dispatch(ORDER_ID,
                new HomeVisitDtos.DispatchRequest(STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上门服务人员不具备该项目的有效资质");
    }

    @Test
    void dispatchSkipsQualificationCheckWhenNotRequired() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("APPROVED")));
        when(serviceRepository.findByServiceCode("HOME_INJECTION"))
                .thenReturn(Optional.of(service("HOME_INJECTION", "published", null)));

        HomeVisitDtos.OrderSummary summary = orderService.dispatch(ORDER_ID,
                new HomeVisitDtos.DispatchRequest(STAFF_ID));

        assertThat(summary.status()).isEqualTo("ASSIGNED");
        assertThat(summary.staffUserId()).isEqualTo(STAFF_ID);
        assertThat(summary.staffName()).isEqualTo("护士甲");
        verify(qualificationRepository, never())
                .findByUserIdAndQualTypeAndStatus(any(), any(), any());
    }

    @Test
    void acceptRechecksQualification() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("ASSIGNED")));
        when(qualificationRepository.findByUserIdAndQualTypeAndStatus(STAFF_ID, "nurse", "active"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> orderService.accept(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("上门服务人员不具备该项目的有效资质");
    }

    @Test
    void acceptMovesAssignedToAccepted() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("ASSIGNED")));

        HomeVisitDtos.OrderSummary summary = orderService.accept(ORDER_ID);

        assertThat(summary.status()).isEqualTo("ACCEPTED");
        verify(auditService).record(eq("HV_ORDER_ACCEPT"), eq("HOME_VISIT_ORDER"), any(), any());
    }

    @Test
    void rejectDispatchReturnsToApprovedAndAppendsRemark() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("ASSIGNED")));

        HomeVisitDtos.OrderSummary summary = orderService.reject(ORDER_ID,
                new HomeVisitDtos.ReasonRequest("当班冲突"));

        assertThat(summary.status()).isEqualTo("APPROVED");
        assertThat(summary.staffUserId()).isNull();
        ArgumentCaptor<HomeVisitOrder> captor = ArgumentCaptor.forClass(HomeVisitOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getRemark()).contains("[拒绝] 当班冲突");
        assertThat(captor.getValue().getAssignedAt()).isNull();
    }

    @Test
    void executionOnlyAllowedWhenInProgress() {
        assertThatThrownBy(() -> orderService.addExecution(ORDER_ID,
                new HomeVisitDtos.ExecutionCreateRequest("已完成注射", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅进行中的订单可登记执行记录");

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("IN_PROGRESS")));
        HomeVisitDtos.ExecutionView view = orderService.addExecution(ORDER_ID,
                new HomeVisitDtos.ExecutionCreateRequest("已完成注射", null));

        assertThat(view.content()).isEqualTo("已完成注射");
        assertThat(view.recordedBy()).isEqualTo("nurse01");
        assertThat(view.visitAt()).isNotNull();
    }

    @Test
    void exceptionLifecycleOpenEscalateResolve() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("IN_PROGRESS")));

        HomeVisitDtos.ExceptionView reported = orderService.reportException(ORDER_ID,
                new HomeVisitDtos.ExceptionCreateRequest("high", "vital_sign", "血压异常"));
        assertThat(reported.status()).isEqualTo("OPEN");

        HomeVisitDtos.ExceptionView escalated = orderService.escalateException(EXCEPTION_ID,
                new HomeVisitDtos.ReasonRequest("需上级医师介入"));
        assertThat(escalated.status()).isEqualTo("ESCALATED");

        HomeVisitDtos.ExceptionView resolved = orderService.resolveException(EXCEPTION_ID,
                new HomeVisitDtos.ResolveRequest("已转诊处理"));
        assertThat(resolved.status()).isEqualTo("RESOLVED");

        assertThatThrownBy(() -> orderService.resolveException(EXCEPTION_ID,
                new HomeVisitDtos.ResolveRequest("重复处置")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该异常已解决，请勿重复操作");
    }

    @Test
    void escalateNonOpenExceptionReturns422() {
        when(exceptionRepository.findById(EXCEPTION_ID))
                .thenReturn(Optional.of(exception(EXCEPTION_ID, "RESOLVED")));

        assertThatThrownBy(() -> orderService.escalateException(EXCEPTION_ID,
                new HomeVisitDtos.ReasonRequest("升级")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅待处理的异常可以升级");
    }

    @Test
    void exceptionReportOnlyAllowedWhenInProgress() {
        assertThatThrownBy(() -> orderService.reportException(ORDER_ID,
                new HomeVisitDtos.ExceptionCreateRequest("low", "other", "设备故障")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅进行中的订单可上报异常");
    }

    @Test
    void finishBlockedByUnresolvedException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("IN_PROGRESS")));
        when(exceptionRepository.countByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection())).thenReturn(1L);

        assertThatThrownBy(() -> orderService.finish(ORDER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("存在未处理的异常上报，请先处置");
    }

    @Test
    void finishAfterAllResolvedComputesDuration() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("IN_PROGRESS")));
        when(exceptionRepository.countByOrderIdAndStatusIn(eq(ORDER_ID), anyCollection())).thenReturn(0L);

        HomeVisitDtos.OrderSummary summary = orderService.finish(ORDER_ID,
                new HomeVisitDtos.FinishRequest("服务顺利"));

        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.durationMinutes()).isEqualTo(2);
        verify(auditService).record(eq("HV_ORDER_FINISH"), eq("HOME_VISIT_ORDER"), any(), any());
    }

    @Test
    void cancelInProgressReturns422() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("IN_PROGRESS")));

        assertThatThrownBy(() -> orderService.cancel(ORDER_ID, new HomeVisitDtos.ReasonRequest("临时取消")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务已开始，请使用完成");
    }

    @Test
    void cancelPendingOrderSucceeds() {
        HomeVisitDtos.OrderSummary summary = orderService.cancel(ORDER_ID,
                new HomeVisitDtos.ReasonRequest("客户改期"));

        assertThat(summary.status()).isEqualTo("CANCELLED");
        ArgumentCaptor<HomeVisitOrder> captor = ArgumentCaptor.forClass(HomeVisitOrder.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getCancelReason()).isEqualTo("客户改期");
    }

    @Test
    void terminalStateLocked() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("COMPLETED")));

        assertThatThrownBy(() -> orderService.accept(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态 COMPLETED 不能接单");

        assertThatThrownBy(() -> orderService.cancel(ORDER_ID, new HomeVisitDtos.ReasonRequest("取消")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态 COMPLETED 不能取消");
    }

    @Test
    void detailReturnsNestedSummaryWithSnapshotAndRecords() {
        HomeVisitOrder order = order("IN_PROGRESS");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(executionRepository.findByOrderIdOrderByVisitAtAsc(ORDER_ID))
                .thenReturn(List.of(withId(new HomeVisitExecution())));
        when(exceptionRepository.findByOrderIdOrderByCreatedAtDesc(ORDER_ID)).thenReturn(List.of());

        HomeVisitDtos.OrderDetail detail = orderService.detail(ORDER_ID);

        assertThat(detail.summary().orderNo()).isEqualTo("HV-TEST");
        assertThat(detail.summary().customerName()).isEqualTo("张三");
        assertThat(detail.address().address()).isEqualTo("上海市浦东新区XX路100号");
        assertThat(detail.address().contactPhoneMasked()).isEqualTo("138****8000");
        assertThat(detail.price()).isEqualTo("80.00");
        assertThat(detail.serviceName()).isEqualTo("居家注射");
        assertThat(detail.requiredQualType()).isEqualTo("nurse");
        assertThat(detail.executions()).hasSize(1);
        assertThat(detail.exceptions()).isEmpty();
    }

    @Test
    void listEnrichesServiceAndCustomerNames() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order("PENDING"))));
        when(customerService.briefs(any())).thenReturn(java.util.Map.of(CUSTOMER_ID, brief()));
        when(serviceRepository.findByServiceCodeIn(any())).thenReturn(
                List.of(service("HOME_INJECTION", "published", "nurse")));

        PageResponse<HomeVisitDtos.OrderSummary> page = orderService.list(
                "HV", "PENDING", "HOME_INJECTION", CUSTOMER_ID, null,
                LocalDate.now(), LocalDate.now().plusDays(7), 1, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).serviceName()).isEqualTo("居家注射");
        assertThat(page.items().get(0).customerName()).isEqualTo("张三");
    }

    @Test
    void listExceptionsEnrichesOrderNo() {
        when(exceptionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exception(EXCEPTION_ID, "OPEN"))));
        when(orderRepository.findAllById(any())).thenReturn(List.of(order("IN_PROGRESS")));

        PageResponse<HomeVisitDtos.ExceptionView> page = orderService.listExceptions("OPEN", "high", 1, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).orderNo()).isEqualTo("HV-TEST");
        assertThat(page.items().get(0).status()).isEqualTo("OPEN");
    }

    private CustomerDtos.CustomerBrief brief() {
        return new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", null, null);
    }

    private HomeVisitOrder order(String status) {
        HomeVisitOrder order = new HomeVisitOrder();
        order.setOrderNo("HV-TEST");
        order.setServiceCode("HOME_INJECTION");
        order.setCustomerId(CUSTOMER_ID);
        order.setAddressId(ADDRESS_ID);
        order.setScheduledDate(LocalDate.now().plusDays(1));
        order.setStartTime(LocalTime.of(9, 0));
        order.setEndTime(LocalTime.of(10, 0));
        order.setStatus(status);
        order.setRemark("原备注");
        if ("ASSIGNED".equals(status) || "ACCEPTED".equals(status)
                || "IN_PROGRESS".equals(status) || "COMPLETED".equals(status)) {
            order.setStaffUserId(STAFF_ID);
            order.setAssignedAt(Instant.now().minusSeconds(600));
        }
        if ("IN_PROGRESS".equals(status) || "COMPLETED".equals(status)) {
            order.setActualStartAt(Instant.now().minusSeconds(120));
        }
        return withId(order, ORDER_ID);
    }

    private HomeService service(String code, String status, String requiredQualType) {
        HomeService service = new HomeService();
        service.setServiceCode(code);
        service.setServiceName("居家注射");
        service.setCategory("medical");
        service.setRequiredQualType(requiredQualType);
        service.setDurationMinutes(30);
        service.setPrice(new BigDecimal("80.00"));
        service.setStatus(status);
        return withId(service);
    }

    private HomeAddress address() {
        HomeAddress address = new HomeAddress();
        address.setCustomerId(CUSTOMER_ID);
        address.setAddress("上海市浦东新区XX路100号");
        address.setContactName("李四");
        address.setContactPhoneMasked("138****8000");
        address.setDefault(true);
        address.setStatus("active");
        return withId(address, ADDRESS_ID);
    }

    private SysUser staff() {
        SysUser user = new SysUser();
        user.setId(STAFF_ID);
        user.setUsername("nurse01");
        user.setDisplayName("护士甲");
        return user;
    }

    private HomeStaffQualification qualification(LocalDate expiresAt) {
        HomeStaffQualification qualification = new HomeStaffQualification();
        qualification.setUserId(STAFF_ID);
        qualification.setQualType("nurse");
        qualification.setCertNo("N-001");
        qualification.setIssuedAt(LocalDate.now().minusYears(1));
        qualification.setExpiresAt(expiresAt);
        qualification.setStatus("active");
        return qualification;
    }

    private HomeVisitException exception(UUID id, String status) {
        HomeVisitException exception = new HomeVisitException();
        exception.setOrderId(ORDER_ID);
        exception.setLevel("high");
        exception.setCategory("vital_sign");
        exception.setDescription("血压异常");
        exception.setStatus(status);
        exception.setReportedBy("nurse01");
        return withId(exception, id);
    }

    private <T> T withId(T entity) {
        return withId(entity, null);
    }

    private <T> T withId(T entity, UUID fixedId) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, fixedId != null ? fixedId : UUID.randomUUID());
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
