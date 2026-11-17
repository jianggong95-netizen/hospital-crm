package com.hospital.crm.order.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.application.gateway.PaymentGateway;
import com.hospital.crm.order.domain.Order;
import com.hospital.crm.order.domain.OrderRepository;
import com.hospital.crm.order.domain.Payment;
import com.hospital.crm.order.domain.PaymentRepository;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付：金额上限、发起支付、回调验签与重复回调幂等入账。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final String PAYMENT_NO = "P100000";
    private static final String TRADE_NO = "STUB-P100000";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private AuditService auditService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(orderRepository, paymentRepository, paymentGateway, auditService);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(paymentRepository.nextPaymentNo()).thenReturn(100000L);
        when(paymentGateway.initiatePayment(any(), any(), any())).thenReturn(TRADE_NO);
        when(paymentGateway.verifyCallback(any(), any(), anyBoolean(), any(), any())).thenReturn(true);
    }

    @Test
    void initiateRejectsAmountAboveOutstanding() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("CREATED", "200.00", "150.00")));

        assertThatThrownBy(() -> paymentService.initiate(ORDER_ID,
                new OrderDtos.PaymentCreateRequest("wechat", new BigDecimal("60.00"), "pay-idem-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付金额超过未付余额");
    }

    @Test
    void initiateCreatesProcessingPaymentWithinOutstanding() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("CREATED", "200.00", "150.00")));

        OrderDtos.PaymentView view = paymentService.initiate(ORDER_ID,
                new OrderDtos.PaymentCreateRequest("wechat", new BigDecimal("50.00"), "pay-idem-1"));

        assertThat(view.paymentNo()).isEqualTo(PAYMENT_NO);
        assertThat(view.status()).isEqualTo("PROCESSING");
        assertThat(view.channelTradeNo()).isEqualTo(TRADE_NO);
        assertThat(view.orderNo()).isEqualTo("O100000");
        verify(auditService).record(eq("PAYMENT_INITIATE"), eq("CRM_PAYMENT"), any(), any());
    }

    @Test
    void initiateRejectsDuplicateIdempotencyKey() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("CREATED", "200.00", "0")));
        when(paymentRepository.findByIdempotencyKey("dup")).thenReturn(Optional.of(payment("PROCESSING", TRADE_NO)));

        assertThatThrownBy(() -> paymentService.initiate(ORDER_ID,
                new OrderDtos.PaymentCreateRequest("wechat", new BigDecimal("50.00"), "dup")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付幂等键已存在");
    }

    @Test
    void callbackRejectsBadSignature() {
        when(paymentGateway.verifyCallback(any(), any(), anyBoolean(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> paymentService.handleCallback("wechat", callback("100.00", true, "bad-sign")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回调验签失败");
    }

    @Test
    void callbackRejectsAmountMismatch() {
        when(paymentRepository.findByPaymentNo(PAYMENT_NO)).thenReturn(Optional.of(payment("PROCESSING", TRADE_NO)));

        assertThatThrownBy(() -> paymentService.handleCallback("wechat", callback("120.00", true, "sig")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回调金额与支付单不符");
    }

    @Test
    void callbackPostsPaidAmountOnceAndReplaysDuplicate() {
        Payment payment = payment("PROCESSING", TRADE_NO);
        when(paymentRepository.findByPaymentNo(PAYMENT_NO)).thenReturn(Optional.of(payment));
        when(paymentRepository.markSuccess(any(), any(), any(), any())).thenReturn(1);
        when(orderRepository.accumulatePaid(any(), any(), any())).thenReturn(1);

        OrderDtos.CallbackResult first = paymentService.handleCallback("wechat", callback("100.00", true, "sig"));
        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(first.replayed()).isFalse();
        verify(paymentRepository, times(1)).markSuccess(eq(PAYMENT_ID), eq(TRADE_NO), any(), any());
        verify(orderRepository, times(1)).accumulatePaid(eq(ORDER_ID), any(), any());

        // 模拟首次回调已把支付单置为 SUCCESS，重复回调只回放不重复入账
        payment.setStatus("SUCCESS");
        OrderDtos.CallbackResult second = paymentService.handleCallback("wechat", callback("100.00", true, "sig"));
        assertThat(second.replayed()).isTrue();
        verify(paymentRepository, times(1)).markSuccess(eq(PAYMENT_ID), eq(TRADE_NO), any(), any());
        verify(orderRepository, times(1)).accumulatePaid(eq(ORDER_ID), any(), any());
    }

    @Test
    void concurrentDuplicateCallbackDoesNotPostAgainWhenMarkReturnsZero() {
        when(paymentRepository.findByPaymentNo(PAYMENT_NO)).thenReturn(Optional.of(payment("PROCESSING", TRADE_NO)));
        when(paymentRepository.markSuccess(any(), any(), any(), any())).thenReturn(0);

        OrderDtos.CallbackResult result = paymentService.handleCallback("wechat", callback("100.00", true, "sig"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(orderRepository, never()).accumulatePaid(any(), any(), any());
    }

    @Test
    void failedCallbackMarksPaymentFailedWithoutPosting() {
        when(paymentRepository.findByPaymentNo(PAYMENT_NO)).thenReturn(Optional.of(payment("PROCESSING", TRADE_NO)));
        when(paymentRepository.markFailed(PAYMENT_ID)).thenReturn(1);

        OrderDtos.CallbackResult result = paymentService.handleCallback("wechat", callback("100.00", false, "sig"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.replayed()).isFalse();
        verify(orderRepository, never()).accumulatePaid(any(), any(), any());
    }

    private OrderDtos.CallbackRequest callback(String amount, boolean success, String signature) {
        return new OrderDtos.CallbackRequest(PAYMENT_NO, TRADE_NO, new BigDecimal(amount), success, signature);
    }

    private Payment payment(String status, String tradeNo) {
        Payment payment = new Payment();
        payment.setPaymentNo(PAYMENT_NO);
        payment.setOrderId(ORDER_ID);
        payment.setChannel("wechat");
        payment.setChannelTradeNo(tradeNo);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setStatus(status);
        payment.setIdempotencyKey("pay-idem-1");
        payment.setPaidAt(status.equals("SUCCESS") ? Instant.now() : null);
        return withId(payment);
    }

    private Order order(String status, String payable, String paid) {
        Order order = new Order();
        order.setOrderNo("O100000");
        order.setCustomerId(UUID.randomUUID());
        order.setOrderType("escort");
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal(payable));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayableAmount(new BigDecimal(payable));
        order.setPaidAmount(new BigDecimal(paid));
        order.setRefundedAmount(BigDecimal.ZERO);
        return withId(order);
    }

    private <T> T withId(T entity) {
        setField(entity, entity instanceof Payment ? PAYMENT_ID : ORDER_ID);
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
