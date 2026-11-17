package com.hospital.crm.order.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.order.api.OrderDtos;
import com.hospital.crm.order.application.gateway.ChannelStatementProvider;
import com.hospital.crm.order.domain.Payment;
import com.hospital.crm.order.domain.PaymentRepository;
import com.hospital.crm.order.domain.ReconciliationBatch;
import com.hospital.crm.order.domain.ReconciliationBatchRepository;
import com.hospital.crm.order.domain.ReconciliationDiffRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 渠道对账：本地与渠道账单逐笔比对，覆盖仅本地、仅渠道与金额不等三类差异。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationServiceTest {

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 11, 16);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReconciliationBatchRepository batchRepository;

    @Mock
    private ReconciliationDiffRepository diffRepository;

    @Mock
    private ChannelStatementProvider statementProvider;

    @Mock
    private AuditService auditService;

    private ReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(paymentRepository, batchRepository,
                diffRepository, statementProvider, auditService);
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "finance01", "财务甲",
                UUID.randomUUID(), "ROOT", "/", Set.of("FINANCE"),
                Set.of("crm:payment:manage"), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));

        when(batchRepository.nextBatchNo()).thenReturn(100000L);
        when(batchRepository.save(any(ReconciliationBatch.class)))
                .thenAnswer(invocation -> withBatchId(invocation.getArgument(0)));
    }

    @Test
    void runMarksMatchedWhenStatementsAlign() {
        when(paymentRepository.findSuccessInPeriod(eq("wechat"), any(), any()))
                .thenReturn(List.of(payment("P100000", "T1", "100.00")));
        when(statementProvider.fetch("wechat", DATE))
                .thenReturn(List.of(entry("T1", "P100000", "100.00")));

        OrderDtos.ReconciliationDetail detail = reconciliationService.run(
                new OrderDtos.ReconRunRequest("wechat", DATE));

        assertThat(detail.batch().status()).isEqualTo("MATCHED");
        assertThat(detail.batch().localCount()).isEqualTo(1);
        assertThat(detail.batch().channelCount()).isEqualTo(1);
        assertThat(detail.batch().localAmount()).isEqualByComparingTo("100.00");
        assertThat(detail.diffs()).isEmpty();
        verify(auditService).record(eq("RECON_RUN"), eq("CRM_RECONCILIATION"), any(), any());
    }

    @Test
    void runDetectsChannelOnlyAndAmountMismatch() {
        when(paymentRepository.findSuccessInPeriod(eq("wechat"), any(), any()))
                .thenReturn(List.of(payment("P100000", "T1", "100.00")));
        when(statementProvider.fetch("wechat", DATE)).thenReturn(List.of(
                entry("T1", "P100000", "120.00"),
                entry("T2", null, "80.00")));

        OrderDtos.ReconciliationDetail detail = reconciliationService.run(
                new OrderDtos.ReconRunRequest("wechat", DATE));

        assertThat(detail.batch().status()).isEqualTo("DIFF");
        assertThat(detail.batch().diffCount()).isEqualTo(2);
        assertThat(detail.diffs()).extracting(OrderDtos.ReconciliationDiffView::diffType)
                .containsExactlyInAnyOrder("AMOUNT_MISMATCH", "CHANNEL_ONLY");
        assertThat(detail.batch().channelAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void runDetectsLocalOnlyWhenChannelStatementMissing() {
        when(paymentRepository.findSuccessInPeriod(eq("alipay"), any(), any()))
                .thenReturn(List.of(payment("P100001", "T9", "66.00")));
        when(statementProvider.fetch("alipay", DATE)).thenReturn(List.of());

        OrderDtos.ReconciliationDetail detail = reconciliationService.run(
                new OrderDtos.ReconRunRequest("alipay", DATE));

        assertThat(detail.batch().status()).isEqualTo("DIFF");
        assertThat(detail.diffs()).hasSize(1);
        assertThat(detail.diffs().get(0).diffType()).isEqualTo("LOCAL_ONLY");
        assertThat(detail.diffs().get(0).localAmount()).isEqualByComparingTo("66.00");
    }

    @Test
    void runRejectsUnknownChannel() {
        assertThatThrownBy(() -> reconciliationService.run(
                new OrderDtos.ReconRunRequest("crypto", DATE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("对账渠道取值不合法");
    }

    private Payment payment(String paymentNo, String tradeNo, String amount) {
        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo);
        payment.setChannelTradeNo(tradeNo);
        payment.setAmount(new BigDecimal(amount));
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setStatus("SUCCESS");
        payment.setChannel("wechat");
        setField(payment, UUID.randomUUID());
        return payment;
    }

    private ChannelStatementProvider.ChannelStatementEntry entry(String tradeNo, String paymentNo, String amount) {
        return new ChannelStatementProvider.ChannelStatementEntry(tradeNo, paymentNo, new BigDecimal(amount));
    }

    private ReconciliationBatch withBatchId(ReconciliationBatch batch) {
        setField(batch, BATCH_ID);
        return batch;
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
