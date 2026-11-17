package com.hospital.crm.order.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.domain.Order;
import com.hospital.crm.order.domain.OrderItemRepository;
import com.hospital.crm.order.domain.OrderRepository;
import com.hospital.crm.order.domain.PaymentRepository;
import com.hospital.crm.order.domain.RefundRepository;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一订单：服务端重算金额、优惠上限、幂等键与状态机。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private AuditService auditService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository, paymentRepository,
                refundRepository, customerService, auditService);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(orderRepository.nextOrderNo()).thenReturn(100000L);
        when(customerService.brief(CUSTOMER_ID)).thenReturn(
                new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", null, null));
    }

    @Test
    void createRecomputesAmountsFromItems() {
        OrderDtos.OrderSummary view = orderService.create(new OrderDtos.OrderCreateRequest(
                CUSTOMER_ID, "escort",
                List.of(new OrderDtos.ItemLine("ESCORT_STD", "陪诊标准", new BigDecimal("2"), new BigDecimal("49.995")),
                        new OrderDtos.ItemLine("TRAVEL", "交通费", BigDecimal.ONE, new BigDecimal("100.00"))),
                new BigDecimal("9.99"), "idem-order-1", "admin-web"));

        assertThat(view.orderNo()).isEqualTo("O100000");
        assertThat(view.status()).isEqualTo("CREATED");
        assertThat(view.totalAmount()).isEqualByComparingTo("199.99");
        assertThat(view.discountAmount()).isEqualByComparingTo("9.99");
        assertThat(view.payableAmount()).isEqualByComparingTo("190.00");
        assertThat(view.paidAmount()).isEqualByComparingTo("0");
        assertThat(view.refundedAmount()).isEqualByComparingTo("0");
        assertThat(view.customerName()).isEqualTo("张三");
        verify(auditService).record(eq("ORDER_CREATE"), eq("CRM_ORDER"), any(), any());
    }

    @Test
    void createRejectsQuantityNotPositive() {
        assertThatThrownBy(() -> orderService.create(new OrderDtos.OrderCreateRequest(
                CUSTOMER_ID, "escort",
                List.of(new OrderDtos.ItemLine("A", "明细", BigDecimal.ZERO, new BigDecimal("10.00"))),
                null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("明细数量必须大于 0");
    }

    @Test
    void createRejectsDiscountExceedingTotal() {
        assertThatThrownBy(() -> orderService.create(new OrderDtos.OrderCreateRequest(
                CUSTOMER_ID, "escort",
                List.of(new OrderDtos.ItemLine("A", "明细", BigDecimal.ONE, new BigDecimal("100.00"))),
                new BigDecimal("100.01"), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优惠金额不能超过订单总额");
    }

    @Test
    void createRejectsDuplicateIdempotencyKey() {
        when(orderRepository.findByIdempotencyKey("dup-key")).thenReturn(Optional.of(order("CREATED")));

        assertThatThrownBy(() -> orderService.create(new OrderDtos.OrderCreateRequest(
                CUSTOMER_ID, "escort",
                List.of(new OrderDtos.ItemLine("A", "明细", BigDecimal.ONE, new BigDecimal("10.00"))),
                null, "dup-key", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单幂等键已存在");
    }

    @Test
    void cancelUnpaidOrderSucceeds() {
        Order created = order("CREATED");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(created));

        OrderDtos.OrderSummary view = orderService.cancel(ORDER_ID, new OrderDtos.ReasonRequest("客户放弃"));

        assertThat(view.status()).isEqualTo("CANCELLED");
        verify(auditService).record(eq("ORDER_CANCEL"), eq("CRM_ORDER"), eq(ORDER_ID.toString()), any());
    }

    @Test
    void cancelPaidOrderRequiresRefund() {
        Order paid = order("PAID");
        paid.setPaidAmount(new BigDecimal("100.00"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> orderService.cancel(ORDER_ID, new OrderDtos.ReasonRequest("客户取消")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单已有支付，请走退款流程");
    }

    @Test
    void completeRequiresPaidStatus() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("CREATED")));

        assertThatThrownBy(() -> orderService.complete(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能完成");
    }

    @Test
    void completePaidOrderRecordsCompletedAt() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PAID")));

        OrderDtos.OrderSummary view = orderService.complete(ORDER_ID);

        assertThat(view.status()).isEqualTo("COMPLETED");
        assertThat(view.completedAt()).isNotNull();
    }

    private Order order(String status) {
        Order order = new Order();
        order.setOrderNo("O100000");
        order.setCustomerId(CUSTOMER_ID);
        order.setOrderType("escort");
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayableAmount(new BigDecimal("100.00"));
        order.setPaidAmount(BigDecimal.ZERO);
        order.setRefundedAmount(BigDecimal.ZERO);
        return withId(order);
    }

    private <T> T withId(T entity) {
        setField(entity, ORDER_ID);
        return entity;
    }

    private void setField(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
