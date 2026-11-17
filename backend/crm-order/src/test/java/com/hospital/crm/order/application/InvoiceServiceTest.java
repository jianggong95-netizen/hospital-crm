package com.hospital.crm.order.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.application.gateway.InvoiceProvider;
import com.hospital.crm.order.domain.Invoice;
import com.hospital.crm.order.domain.InvoiceRepository;
import com.hospital.crm.order.domain.Order;
import com.hospital.crm.order.domain.OrderRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发票：可开票净额 = 实付 − 已退，累计开票额度不得超限，红冲释放额度。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceServiceTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InvoiceProvider invoiceProvider;

    @Mock
    private AuditService auditService;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, orderRepository, invoiceProvider, auditService);
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "finance01", "财务甲",
                UUID.randomUUID(), "ROOT", "/", Set.of("FINANCE"),
                Set.of("crm:payment:manage"), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> withInvoiceId(invocation.getArgument(0)));
        when(invoiceRepository.nextInvoiceNo()).thenReturn(100000L);
        when(invoiceRepository.sumActiveAmount(ORDER_ID)).thenReturn(BigDecimal.ZERO);
        when(invoiceProvider.issue(any())).thenReturn(new InvoiceProvider.InvoiceIssueResult(true, "stub-invoice", "ok"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("PAID", "200.00", "50.00")));
    }

    @Test
    void applyRejectsOrderNotPaid() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order("CREATED", "200.00", "0")));

        assertThatThrownBy(() -> invoiceService.apply(ORDER_ID,
                new OrderDtos.InvoiceApplyRequest("某某医院", null, new BigDecimal("100.00"), "general")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单当前状态不可开票");
    }

    @Test
    void applyRejectsAmountAboveInvoiceableNet() {
        assertThatThrownBy(() -> invoiceService.apply(ORDER_ID,
                new OrderDtos.InvoiceApplyRequest("某某医院", null, new BigDecimal("200.00"), "general")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开票金额超过可开票金额");
    }

    @Test
    void applyRejectsCumulativeAmountAboveInvoiceableNet() {
        when(invoiceRepository.sumActiveAmount(ORDER_ID)).thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> invoiceService.apply(ORDER_ID,
                new OrderDtos.InvoiceApplyRequest("某某医院", null, new BigDecimal("60.00"), "general")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("开票金额超过可开票金额");
    }

    @Test
    void applyCreatesAppliedInvoiceWithinNet() {
        OrderDtos.InvoiceView view = invoiceService.apply(ORDER_ID,
                new OrderDtos.InvoiceApplyRequest("某某医院", "91310000XXXX", new BigDecimal("150.00"), "special"));

        assertThat(view.invoiceNo()).isEqualTo("INV100000");
        assertThat(view.status()).isEqualTo("APPLIED");
        assertThat(view.amount()).isEqualByComparingTo("150.00");
        assertThat(view.invoiceType()).isEqualTo("special");
        assertThat(view.appliedBy()).isEqualTo("finance01");
        assertThat(view.orderNo()).isEqualTo("O100000");
        verify(auditService).record(eq("INVOICE_APPLY"), eq("CRM_INVOICE"), any(), any());
    }

    @Test
    void issueMarksInvoiceIssuedWithFileRef() {
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice("APPLIED")));

        OrderDtos.InvoiceView view = invoiceService.issue(INVOICE_ID);

        assertThat(view.status()).isEqualTo("ISSUED");
        assertThat(view.fileRef()).isEqualTo("stub-invoice");
        assertThat(view.issuedAt()).isNotNull();
        verify(auditService).record(eq("INVOICE_ISSUE"), eq("CRM_INVOICE"), any(), any());
    }

    @Test
    void issueRejectsAlreadyIssuedInvoice() {
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice("ISSUED")));

        assertThatThrownBy(() -> invoiceService.issue(INVOICE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能开票");
    }

    @Test
    void voidInvoiceReleasesQuotaAndRejectsRepeat() {
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice("ISSUED")));

        OrderDtos.InvoiceView view = invoiceService.voidInvoice(INVOICE_ID,
                new OrderDtos.ReasonRequest("抬头填写错误"));
        assertThat(view.status()).isEqualTo("VOID");
        assertThat(view.voidReason()).isEqualTo("抬头填写错误");

        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice("VOID")));
        assertThatThrownBy(() -> invoiceService.voidInvoice(INVOICE_ID,
                new OrderDtos.ReasonRequest("重复红冲")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该发票已红冲，请勿重复操作");
    }

    private Invoice invoice(String status) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNo("INV100000");
        invoice.setOrderId(ORDER_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setTitle("某某医院");
        invoice.setAmount(new BigDecimal("150.00"));
        invoice.setInvoiceType("general");
        invoice.setStatus(status);
        return withInvoiceId(invoice);
    }

    private Order order(String status, String paid, String refunded) {
        Order order = new Order();
        order.setOrderNo("O100000");
        order.setCustomerId(CUSTOMER_ID);
        order.setOrderType("escort");
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setPayableAmount(new BigDecimal("200.00"));
        order.setPaidAmount(new BigDecimal(paid));
        order.setRefundedAmount(new BigDecimal(refunded));
        setField(order, ORDER_ID);
        return order;
    }

    private Invoice withInvoiceId(Invoice invoice) {
        setField(invoice, INVOICE_ID);
        return invoice;
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
