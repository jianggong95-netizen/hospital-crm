package com.hospital.crm.order.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.application.gateway.PaymentGateway;
import com.hospital.crm.order.domain.Order;
import com.hospital.crm.order.domain.OrderRepository;
import com.hospital.crm.order.domain.Payment;
import com.hospital.crm.order.domain.PaymentRepository;
import com.hospital.crm.order.domain.Refund;
import com.hospital.crm.order.domain.RefundRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
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
 * 退款：可退余额校验、部分退款累计、并发超退拒绝与驳回规则。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundServiceTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID REFUND_ID = UUID.randomUUID();

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private AuditService auditService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(refundRepository, paymentRepository, orderRepository,
                paymentGateway, auditService);
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "finance01", "财务甲",
                UUID.randomUUID(), "ROOT", "/", Set.of("FINANCE"),
                Set.of("crm:payment:manage"), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));

        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> withRefundId(invocation.getArgument(0)));
        when(refundRepository.nextRefundNo()).thenReturn(100000L);
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment(new BigDecimal("200.00"), new BigDecimal("50.00"))));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order()));
        when(paymentGateway.executeRefund(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void applyRejectsNonSuccessPayment() {
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment("PROCESSING", new BigDecimal("200.00"), BigDecimal.ZERO)));

        assertThatThrownBy(() -> refundService.apply(PAYMENT_ID,
                new OrderDtos.RefundApplyRequest(new BigDecimal("10.00"), "客户取消")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅支付成功的单据可退款");
    }

    @Test
    void applyRejectsAmountAboveRefundableBalance() {
        assertThatThrownBy(() -> refundService.apply(PAYMENT_ID,
                new OrderDtos.RefundApplyRequest(new BigDecimal("200.00"), "客户取消")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("退款金额超过可退余额");
    }

    @Test
    void applyCreatesPendingRefundWithinRefundableBalance() {
        OrderDtos.RefundView view = refundService.apply(PAYMENT_ID,
                new OrderDtos.RefundApplyRequest(new BigDecimal("150.00"), "部分退款"));

        assertThat(view.refundNo()).isEqualTo("R100000");
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.amount()).isEqualByComparingTo("150.00");
        assertThat(view.orderId()).isEqualTo(ORDER_ID);
        verify(auditService).record(eq("REFUND_APPLY"), eq("CRM_REFUND"), any(), any());
    }

    @Test
    void approveAccumulatesRefundedAmountForPartialRefund() {
        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund("PENDING", new BigDecimal("50.00"))));
        when(refundRepository.markSucceeded(any(), any(), any())).thenReturn(1);
        when(paymentRepository.accumulateRefunded(any(), any())).thenReturn(1);
        when(orderRepository.accumulateRefunded(any(), any(), any())).thenReturn(1);

        OrderDtos.RefundView view = refundService.approve(REFUND_ID);

        assertThat(view.status()).isEqualTo("SUCCESS");
        assertThat(view.reviewedBy()).isEqualTo("finance01");
        assertThat(view.channelRefundNo()).isEqualTo("RCF-R100000");
        verify(paymentRepository).accumulateRefunded(PAYMENT_ID, new BigDecimal("50.00"));
        verify(orderRepository).accumulateRefunded(eq(ORDER_ID), eq(new BigDecimal("50.00")), any());
        verify(auditService).record(eq("REFUND_SUCCESS"), eq("CRM_REFUND"), any(), any());
    }

    @Test
    void approveRejectsWhenConcurrentRefundExceedsPaymentBalance() {
        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund("PENDING", new BigDecimal("50.00"))));
        when(refundRepository.markSucceeded(any(), any(), any())).thenReturn(1);
        when(paymentRepository.accumulateRefunded(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> refundService.approve(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("并发退款超出支付单可退余额");
        verify(orderRepository, never()).accumulateRefunded(any(), any(), any());
    }

    @Test
    void approveRejectedWhenRefundAlreadyHandled() {
        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund("SUCCESS", new BigDecimal("50.00"))));

        assertThatThrownBy(() -> refundService.approve(REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅待审核的退款申请可以审核");
        verify(paymentGateway, never()).executeRefund(any(), any(), any(), any());
    }

    @Test
    void rejectRequiresPendingAndRecordsReason() {
        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund("PENDING", new BigDecimal("50.00"))));

        OrderDtos.RefundView view = refundService.reject(REFUND_ID,
                new OrderDtos.ReasonRequest("支付渠道不支持原路退回"));

        assertThat(view.status()).isEqualTo("REJECTED");
        assertThat(view.rejectReason()).isEqualTo("支付渠道不支持原路退回");
    }

    @Test
    void rejectRejectsNonPendingRefund() {
        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund("REJECTED", new BigDecimal("50.00"))));

        assertThatThrownBy(() -> refundService.reject(REFUND_ID, new OrderDtos.ReasonRequest("重复驳回")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅待审核的退款申请可以驳回");
    }

    private Refund refund(String status, BigDecimal amount) {
        Refund refund = new Refund();
        refund.setRefundNo("R100000");
        refund.setPaymentId(PAYMENT_ID);
        refund.setOrderId(ORDER_ID);
        refund.setAmount(amount);
        refund.setReason("部分退款");
        refund.setStatus(status);
        return withRefundId(refund);
    }

    private Payment payment(BigDecimal amount, BigDecimal refunded) {
        return payment("SUCCESS", amount, refunded);
    }

    private Payment payment(String status, BigDecimal amount, BigDecimal refunded) {
        Payment payment = new Payment();
        payment.setPaymentNo("P100000");
        payment.setOrderId(ORDER_ID);
        payment.setChannel("wechat");
        payment.setChannelTradeNo("STUB-P100000");
        payment.setAmount(amount);
        payment.setRefundedAmount(refunded);
        payment.setStatus(status);
        payment.setIdempotencyKey("pay-idem-1");
        setField(payment, PAYMENT_ID);
        return payment;
    }

    private Order order() {
        Order order = new Order();
        order.setOrderNo("O100000");
        order.setCustomerId(UUID.randomUUID());
        order.setOrderType("escort");
        order.setStatus("PAID");
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayableAmount(new BigDecimal("200.00"));
        order.setPaidAmount(new BigDecimal("200.00"));
        order.setRefundedAmount(new BigDecimal("50.00"));
        setField(order, ORDER_ID);
        return order;
    }

    private Refund withRefundId(Refund refund) {
        setField(refund, REFUND_ID);
        return refund;
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
