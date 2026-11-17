package com.hospital.crm.member.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.member.api.MemberDtos;
import com.hospital.crm.member.domain.MemberAccount;
import com.hospital.crm.member.domain.MemberAccountRepository;
import com.hospital.crm.member.domain.MemberCard;
import com.hospital.crm.member.domain.MemberCardRepository;
import com.hospital.crm.member.domain.MemberLedger;
import com.hospital.crm.member.domain.MemberLedgerRepository;
import com.hospital.crm.member.domain.MemberLevel;
import com.hospital.crm.member.domain.MemberLevelRepository;
import com.hospital.crm.member.domain.MemberShare;
import com.hospital.crm.member.domain.MemberShareRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员账户：并发不超扣、每次变动有账本、等级自动升级与退款返还权益。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberAccountServiceTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OWNER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OWNER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Mock
    private MemberAccountRepository accountRepository;
    @Mock
    private MemberLedgerRepository ledgerRepository;
    @Mock
    private MemberLevelRepository levelRepository;
    @Mock
    private MemberCardRepository cardRepository;
    @Mock
    private MemberShareRepository shareRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private AuditService auditService;

    private MemberAccountService service;

    @BeforeEach
    void setUp() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "member01", "会员专员",
                UUID.randomUUID(), "ROOT", "/", java.util.Set.of("MEMBER"),
                java.util.Set.of("crm:member:write"), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", principal.getAuthorities()));

        service = new MemberAccountService(accountRepository, ledgerRepository, levelRepository,
                cardRepository, shareRepository, customerService, auditService);
        when(accountRepository.save(any(MemberAccount.class)))
                .thenAnswer(invocation -> withAccountId(invocation.getArgument(0)));
        when(accountRepository.nextAccountNo()).thenReturn(100000L);
        when(customerService.brief(any(UUID.class)))
                .thenReturn(new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C001", "张三", null, null));
        when(cardRepository.findBySourceOrderId(ORDER_ID)).thenReturn(List.of());
        when(levelRepository.findById(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openCreatesAccountWithStandardLevel() {
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        MemberDtos.AccountView view = service.open(new MemberDtos.CreateAccountRequest(CUSTOMER_ID));

        assertThat(view.accountNo()).isEqualTo("M100000");
        assertThat(view.memberLevel()).isEqualTo("standard");
        assertThat(view.balance()).isEqualByComparingTo("0");
        assertThat(view.points()).isZero();
        verify(auditService).record(eq("MEMBER_ACCOUNT_OPEN"), any(), any(), any());
    }

    @Test
    void openRejectsDuplicateAccount() {
        when(accountRepository.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(account(ACCOUNT_ID, CUSTOMER_ID, "0", 0)));

        assertThatThrownBy(() -> service.open(new MemberDtos.CreateAccountRequest(CUSTOMER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该客户已开通会员账户");
    }

    @Test
    void rechargeAddsBalanceAndWritesLedger() {
        MemberAccount account = account(ACCOUNT_ID, CUSTOMER_ID, "0", 0);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.addBalance(eq(ACCOUNT_ID), any(), any(Instant.class))).thenAnswer(invocation -> {
            account.setBalance(account.getBalance().add(invocation.getArgument(1)));
            return 1;
        });

        MemberDtos.AccountView view = service.recharge(new MemberDtos.RechargeRequest(
                ACCOUNT_ID, new BigDecimal("200.00"), ORDER_ID, "会员充值"));

        assertThat(view.balance()).isEqualByComparingTo("200.00");
        ArgumentCaptor<MemberLedger> captor = ArgumentCaptor.forClass(MemberLedger.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getLedgerType()).isEqualTo("RECHARGE");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("200.00");
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("200.00");
        assertThat(captor.getValue().getRelatedOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void consumeRejectsWhenConcurrentBalanceInsufficient() {
        when(accountRepository.findByCustomerId(CUSTOMER_ID))
                .thenReturn(Optional.of(account(ACCOUNT_ID, CUSTOMER_ID, "10.00", 0)));
        when(accountRepository.deductBalance(eq(ACCOUNT_ID), any(), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.consume(new MemberDtos.ConsumeRequest(
                CUSTOMER_ID, new BigDecimal("50.00"), "escort", "E001", false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账户余额不足");
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void consumeUsesSharedOwnerBalanceWhenAllowed() {
        MemberAccount own = account(ACCOUNT_ID, CUSTOMER_ID, "10.00", 0);
        MemberAccount owner = account(OWNER_ACCOUNT_ID, OWNER_CUSTOMER_ID, "100.00", 0);
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(own));
        when(accountRepository.findByCustomerId(OWNER_CUSTOMER_ID)).thenReturn(Optional.of(owner));
        when(accountRepository.findById(OWNER_ACCOUNT_ID)).thenReturn(Optional.of(owner));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(own));
        when(shareRepository.findByBeneficiaryCustomerIdAndResourceTypeAndStatusAndCanConsumeTrueOrderByCreatedAtAsc(
                CUSTOMER_ID, "BALANCE", "ACTIVE")).thenReturn(List.of(activeShare()));
        when(accountRepository.deductBalance(eq(OWNER_ACCOUNT_ID), any(), any(Instant.class))).thenAnswer(invocation -> {
            owner.setBalance(owner.getBalance().subtract(invocation.getArgument(1)));
            return 1;
        });

        MemberDtos.AccountView view = service.consume(new MemberDtos.ConsumeRequest(
                CUSTOMER_ID, new BigDecimal("50.00"), "escort", "E002", true));

        assertThat(view.balance()).isEqualByComparingTo("50.00");
        verify(accountRepository).deductBalance(eq(OWNER_ACCOUNT_ID), eq(new BigDecimal("50.00")), any(Instant.class));
        verify(accountRepository, never()).deductBalance(eq(ACCOUNT_ID), any(), any(Instant.class));
        assertThat(own.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void adjustPointsUpgradesMemberLevel() {
        MemberAccount account = account(ACCOUNT_ID, CUSTOMER_ID, "0", 0);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.addPoints(eq(ACCOUNT_ID), anyLong(), any(Instant.class))).thenAnswer(invocation -> {
            long delta = invocation.getArgument(1, Long.class);
            account.setPoints(account.getPoints() + delta);
            return 1;
        });
        MemberLevel gold = new MemberLevel();
        gold.setLevelCode("gold");
        gold.setLevelName("金卡会员");
        when(levelRepository.findFirstByStatusAndMinPointsLessThanEqualOrderByMinPointsDesc(eq("active"), anyLong()))
                .thenReturn(Optional.of(gold));

        service.adjustPoints(new MemberDtos.PointsAdjustRequest(ACCOUNT_ID, 5000L, "消费奖励"));

        verify(accountRepository).updateLevel(eq(ACCOUNT_ID), eq("gold"), any(Instant.class));
        ArgumentCaptor<MemberLedger> captor = ArgumentCaptor.forClass(MemberLedger.class);
        verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getLedgerType()).isEqualTo("POINTS_ADJUST");
        assertThat(captor.getValue().getPointsDelta()).isEqualTo(5000L);
    }

    @Test
    void refundReversalDeductsRechargedBalance() {
        MemberAccount account = account(ACCOUNT_ID, CUSTOMER_ID, "200.00", 0);
        MemberLedger recharge = new MemberLedger();
        recharge.setAccountId(ACCOUNT_ID);
        recharge.setLedgerType("RECHARGE");
        recharge.setAmount(new BigDecimal("200.00"));
        recharge.setPointsDelta(0L);
        recharge.setRelatedOrderId(ORDER_ID);
        when(ledgerRepository.findByRelatedOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of(recharge));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.deductBalance(eq(ACCOUNT_ID), any(), any(Instant.class))).thenAnswer(invocation -> {
            account.setBalance(account.getBalance().subtract(invocation.getArgument(1)));
            return 1;
        });

        MemberDtos.RefundReversalResult result = service.reverseOnRefund(
                new MemberDtos.RefundReversalRequest(ORDER_ID, new BigDecimal("120.00")));

        assertThat(result.balanceReversed()).isEqualByComparingTo("120.00");
        assertThat(result.cardsVoided()).isZero();
        assertThat(account.getBalance()).isEqualByComparingTo("80.00");
        verify(auditService).record(eq("MEMBER_REFUND_REVERSAL"), any(), any(), any());
    }

    @Test
    void refundReversalVoidsUnusedTimesCard() {
        MemberAccount account = account(ACCOUNT_ID, CUSTOMER_ID, "0", 0);
        MemberLedger purchase = new MemberLedger();
        purchase.setAccountId(ACCOUNT_ID);
        purchase.setLedgerType("PACKAGE_PURCHASE");
        purchase.setAmount(new BigDecimal("1800.00"));
        purchase.setPointsDelta(0L);
        purchase.setRelatedOrderId(ORDER_ID);
        MemberCard card = new MemberCard();
        card.setCardNo("C100000");
        card.setCustomerId(CUSTOMER_ID);
        card.setRemainingTimes(3);
        card.setTotalTimes(10);
        when(ledgerRepository.findByRelatedOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of(purchase));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(cardRepository.findBySourceOrderId(ORDER_ID)).thenReturn(List.of(card));
        when(cardRepository.markVoid(any(), any(Instant.class))).thenReturn(1);
        when(accountRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(account));

        MemberDtos.RefundReversalResult result = service.reverseOnRefund(
                new MemberDtos.RefundReversalRequest(ORDER_ID, new BigDecimal("1800.00")));

        assertThat(result.cardsVoided()).isEqualTo(1);
        assertThat(result.cardTimesReturned()).isEqualTo(3);
        verify(cardRepository).markVoid(eq(card.getId()), any(Instant.class));
    }

    @Test
    void refundReversalRejectsOrderWithoutMemberBenefit() {
        when(ledgerRepository.findByRelatedOrderIdOrderByCreatedAtAsc(ORDER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.reverseOnRefund(
                new MemberDtos.RefundReversalRequest(ORDER_ID, new BigDecimal("10.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该订单没有可返还的会员权益");
    }

    @Test
    void rechargeRejectsMissingAccount() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recharge(new MemberDtos.RechargeRequest(
                ACCOUNT_ID, BigDecimal.ONE, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("会员账户不存在");
    }

    private MemberAccount account(UUID id, UUID customerId, String balance, long points) {
        MemberAccount account = new MemberAccount();
        account.setAccountNo("M100000");
        account.setCustomerId(customerId);
        account.setMemberLevel("standard");
        account.setBalance(new BigDecimal(balance));
        account.setPoints(points);
        account.setStatus("active");
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());
        setField(account, id);
        return account;
    }

    private MemberShare activeShare() {
        MemberShare share = new MemberShare();
        share.setOwnerCustomerId(OWNER_CUSTOMER_ID);
        share.setBeneficiaryCustomerId(CUSTOMER_ID);
        share.setResourceType("BALANCE");
        share.setCanConsume(true);
        share.setStatus("ACTIVE");
        share.setValidFrom(Instant.now().minusSeconds(60));
        share.setValidTo(Instant.now().plusSeconds(30L * 86400));
        return share;
    }

    private MemberAccount withAccountId(MemberAccount account) {
        setField(account, ACCOUNT_ID);
        return account;
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
