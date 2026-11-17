package com.hospital.crm.member.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.member.api.MemberDtos;
import com.hospital.crm.member.domain.MemberAccount;
import com.hospital.crm.member.domain.MemberAccountRepository;
import com.hospital.crm.member.domain.MemberCard;
import com.hospital.crm.member.domain.MemberCardRepository;
import com.hospital.crm.member.domain.MemberCardUsage;
import com.hospital.crm.member.domain.MemberCardUsageRepository;
import com.hospital.crm.member.domain.MemberLedger;
import com.hospital.crm.member.domain.MemberLedgerRepository;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 次卡：并发扣次不超扣、每次扣次写记录与账本、返还与重复返还校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberCardServiceTest {

    private static final UUID CARD_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID USAGE_ID = UUID.randomUUID();

    @Mock
    private MemberCardRepository cardRepository;
    @Mock
    private MemberCardUsageRepository usageRepository;
    @Mock
    private MemberAccountRepository accountRepository;
    @Mock
    private MemberLedgerRepository ledgerRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private AuditService auditService;

    private MemberCardService service;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "member01", "会员专员",
                UUID.randomUUID(), "ROOT", "/", java.util.Set.of("MEMBER"),
                java.util.Set.of("crm:member:write"), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));

        service = new MemberCardService(cardRepository, usageRepository, accountRepository,
                ledgerRepository, customerService, auditService);
        when(usageRepository.save(any(MemberCardUsage.class)))
                .thenAnswer(invocation -> withUsageId(invocation.getArgument(0)));
        when(customerService.brief(any(UUID.class)))
                .thenReturn(new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", null, null));
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(account()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consumeDeductsTimesAndWritesUsageAndLedger() {
        MemberCard card = card(10, 0, 10, LocalDate.now().plusDays(30));
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(cardRepository.consumeTimes(eq(CARD_ID), eq(2), any(Instant.class))).thenAnswer(invocation -> {
            card.setUsedTimes(card.getUsedTimes() + 2);
            card.setRemainingTimes(card.getRemainingTimes() - 2);
            return 1;
        });

        MemberDtos.CardView view = service.consume(CARD_ID,
                new MemberDtos.CardConsumeRequest(2, "escort", "E001", "陪诊扣次"));

        assertThat(view.remainingTimes()).isEqualTo(8);
        assertThat(view.usedTimes()).isEqualTo(2);

        ArgumentCaptor<MemberCardUsage> usageCaptor = ArgumentCaptor.forClass(MemberCardUsage.class);
        verify(usageRepository).save(usageCaptor.capture());
        assertThat(usageCaptor.getValue().getTimes()).isEqualTo(2);
        assertThat(usageCaptor.getValue().getBizNo()).isEqualTo("E001");
        assertThat(usageCaptor.getValue().getStatus()).isEqualTo("CONSUMED");

        ArgumentCaptor<MemberLedger> ledgerCaptor = ArgumentCaptor.forClass(MemberLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getLedgerType()).isEqualTo("CARD_CONSUME");
        verify(auditService).record(eq("MEMBER_CARD_CONSUME"), any(), any(), any());
    }

    @Test
    void consumeRejectsWhenRemainingTimesInsufficient() {
        when(cardRepository.findById(CARD_ID))
                .thenReturn(Optional.of(card(10, 8, 2, LocalDate.now().plusDays(30))));
        when(cardRepository.consumeTimes(eq(CARD_ID), eq(3), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.consume(CARD_ID,
                new MemberDtos.CardConsumeRequest(3, "escort", "E002", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("次卡剩余次数不足");
        verify(usageRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void consumeRejectsExpiredCard() {
        when(cardRepository.findById(CARD_ID))
                .thenReturn(Optional.of(card(10, 9, 1, LocalDate.now().minusDays(1))));

        assertThatThrownBy(() -> service.consume(CARD_ID,
                new MemberDtos.CardConsumeRequest(1, "escort", "E003", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("次卡已过有效期");
    }

    @Test
    void consumeMarksCardExhaustedWhenRemainingBecomesZero() {
        MemberCard card = card(10, 9, 1, LocalDate.now().plusDays(30));
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(cardRepository.consumeTimes(eq(CARD_ID), eq(1), any(Instant.class))).thenAnswer(invocation -> {
            card.setUsedTimes(10);
            card.setRemainingTimes(0);
            return 1;
        });
        when(cardRepository.markExhausted(eq(CARD_ID), any(Instant.class))).thenAnswer(invocation -> {
            card.setStatus("EXHAUSTED");
            return 1;
        });
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));

        MemberDtos.CardView view = service.consume(CARD_ID,
                new MemberDtos.CardConsumeRequest(1, "escort", "E004", null));

        assertThat(view.status()).isEqualTo("EXHAUSTED");
        verify(cardRepository).markExhausted(eq(CARD_ID), any(Instant.class));
    }

    @Test
    void refundRestoresTimesAndMarksUsageRefunded() {
        MemberCard card = card(10, 2, 8, LocalDate.now().plusDays(30));
        MemberCardUsage usage = new MemberCardUsage();
        usage.setCardId(CARD_ID);
        usage.setTimes(2);
        usage.setStatus("CONSUMED");
        setField(usage, USAGE_ID);
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        when(usageRepository.findById(USAGE_ID)).thenReturn(Optional.of(usage));
        when(usageRepository.markRefunded(eq(USAGE_ID), any(Instant.class))).thenReturn(1);
        when(cardRepository.refundTimes(eq(CARD_ID), eq(2), any(Instant.class))).thenAnswer(invocation -> {
            card.setUsedTimes(0);
            card.setRemainingTimes(10);
            return 1;
        });

        MemberDtos.CardView view = service.refund(CARD_ID,
                new MemberDtos.CardRefundRequest(2, USAGE_ID, "服务取消"));

        assertThat(view.remainingTimes()).isEqualTo(10);
        verify(usageRepository).markRefunded(eq(USAGE_ID), any(Instant.class));
        ArgumentCaptor<MemberLedger> captor = ArgumentCaptor.forClass(MemberLedger.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getLedgerType()).isEqualTo("CARD_REFUND");
    }

    @Test
    void refundRejectsDuplicateUsageRefund() {
        when(cardRepository.findById(CARD_ID))
                .thenReturn(Optional.of(card(10, 2, 8, LocalDate.now().plusDays(30))));
        MemberCardUsage usage = new MemberCardUsage();
        usage.setCardId(CARD_ID);
        usage.setTimes(2);
        usage.setStatus("REFUNDED");
        setField(usage, USAGE_ID);
        when(usageRepository.findById(USAGE_ID)).thenReturn(Optional.of(usage));
        when(usageRepository.markRefunded(eq(USAGE_ID), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.refund(CARD_ID,
                new MemberDtos.CardRefundRequest(2, USAGE_ID, "重复返还")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该扣次记录已返还，请勿重复操作");
        verify(cardRepository, never()).refundTimes(any(), org.mockito.ArgumentMatchers.anyInt(), any(Instant.class));
    }

    @Test
    void refundRejectsVoidCard() {
        MemberCard card = card(10, 2, 8, LocalDate.now().plusDays(30));
        card.setStatus("VOID");
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> service.refund(CARD_ID,
                new MemberDtos.CardRefundRequest(1, null, "作废卡返还")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("次卡已作废，不能返还");
    }

    private MemberCard card(int total, int used, int remaining, LocalDate validTo) {
        MemberCard card = new MemberCard();
        card.setCardNo("C100000");
        card.setCustomerId(CUSTOMER_ID);
        card.setPackageCode("PKG_TIMES_10");
        card.setPackageName("陪诊次卡 10 次");
        card.setTotalTimes(total);
        card.setUsedTimes(used);
        card.setRemainingTimes(remaining);
        card.setValidFrom(LocalDate.now().minusDays(1));
        card.setValidTo(validTo);
        card.setStatus("ACTIVE");
        setField(card, CARD_ID);
        return card;
    }

    private MemberAccount account() {
        MemberAccount account = new MemberAccount();
        account.setAccountNo("M100000");
        account.setCustomerId(CUSTOMER_ID);
        account.setMemberLevel("standard");
        account.setBalance(BigDecimal.ZERO);
        account.setPoints(0L);
        account.setStatus("active");
        setField(account, ACCOUNT_ID);
        return account;
    }

    private MemberCardUsage withUsageId(MemberCardUsage usage) {
        setField(usage, USAGE_ID);
        return usage;
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
