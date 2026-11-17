package com.hospital.crm.member.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.member.api.MemberDtos;
import com.hospital.crm.member.domain.MemberAccount;
import com.hospital.crm.member.domain.MemberCard;
import com.hospital.crm.member.domain.MemberCardRepository;
import com.hospital.crm.member.domain.MemberPackage;
import com.hospital.crm.member.domain.MemberPackageRepository;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.math.BigDecimal;
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
 * 会员套餐：未启用不可购买、TIMES 生成次卡、BALANCE/POINTS 入账。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberPackageServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CARD_ID = UUID.randomUUID();

    @Mock
    private MemberPackageRepository packageRepository;
    @Mock
    private MemberCardRepository cardRepository;
    @Mock
    private MemberAccountService accountService;
    @Mock
    private AuditService auditService;

    private MemberPackageService service;

    @BeforeEach
    void setUp() {
        service = new MemberPackageService(packageRepository, cardRepository, accountService, auditService);
        when(cardRepository.save(any(MemberCard.class))).thenAnswer(invocation -> withCardId(invocation.getArgument(0)));
        when(cardRepository.nextCardNo()).thenReturn(100000L);
        when(accountService.ensureAccount(CUSTOMER_ID)).thenReturn(account());
        when(accountService.addPackageBenefit(any(), any(), anyLong(), any(), any()))
                .thenReturn(accountView());
    }

    @Test
    void purchaseTimesPackageCreatesCardWithValidity() {
        when(packageRepository.findById("PKG_TIMES_10"))
                .thenReturn(Optional.of(packageEntity("PKG_TIMES_10", "TIMES", 10, null, null, "active")));

        MemberDtos.PurchaseResult result = service.purchase("PKG_TIMES_10",
                new MemberDtos.PurchaseRequest(CUSTOMER_ID, ORDER_ID));

        assertThat(result.card()).isNotNull();
        assertThat(result.card().cardNo()).isEqualTo("C100000");
        assertThat(result.card().totalTimes()).isEqualTo(10);
        assertThat(result.card().remainingTimes()).isEqualTo(10);
        assertThat(result.card().status()).isEqualTo("ACTIVE");
        assertThat(result.balanceAdded()).isEqualByComparingTo("0");

        ArgumentCaptor<MemberCard> captor = ArgumentCaptor.forClass(MemberCard.class);
        verify(cardRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getValidTo()).isAfter(captor.getValue().getValidFrom());
        verify(auditService).record(eq("MEMBER_PACKAGE_PURCHASE"), any(), any(), any());
    }

    @Test
    void purchaseBalancePackageAddsBalance() {
        when(packageRepository.findById("PKG_BALANCE_2000"))
                .thenReturn(Optional.of(packageEntity("PKG_BALANCE_2000", "BALANCE", null, new BigDecimal("2000.00"), null, "active")));

        MemberDtos.PurchaseResult result = service.purchase("PKG_BALANCE_2000",
                new MemberDtos.PurchaseRequest(CUSTOMER_ID, ORDER_ID));

        assertThat(result.balanceAdded()).isEqualByComparingTo("2000.00");
        assertThat(result.card()).isNull();
        verify(accountService).addPackageBenefit(any(), eq(new BigDecimal("2000.00")), eq(0L), eq(ORDER_ID), any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    void purchasePointsPackageAddsPoints() {
        when(packageRepository.findById("PKG_POINTS_5000"))
                .thenReturn(Optional.of(packageEntity("PKG_POINTS_5000", "POINTS", null, null, 5000L, "active")));

        MemberDtos.PurchaseResult result = service.purchase("PKG_POINTS_5000",
                new MemberDtos.PurchaseRequest(CUSTOMER_ID, ORDER_ID));

        assertThat(result.pointsAdded()).isEqualTo(5000L);
        verify(accountService).addPackageBenefit(any(), eq(BigDecimal.ZERO), eq(5000L), eq(ORDER_ID), any());
    }

    @Test
    void purchaseRejectsDisabledPackage() {
        when(packageRepository.findById("PKG_TIMES_10"))
                .thenReturn(Optional.of(packageEntity("PKG_TIMES_10", "TIMES", 10, null, null, "disabled")));

        assertThatThrownBy(() -> service.purchase("PKG_TIMES_10",
                new MemberDtos.PurchaseRequest(CUSTOMER_ID, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("套餐未启用，不能购买");
        verify(cardRepository, never()).save(any());
    }

    @Test
    void purchaseRejectsUnknownPackage() {
        when(packageRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchase("NOPE",
                new MemberDtos.PurchaseRequest(CUSTOMER_ID, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("套餐不存在");
    }

    @Test
    void createRejectsTimesPackageWithoutTimes() {
        assertThatThrownBy(() -> service.create(new MemberDtos.PackageCreateRequest(
                "PKG_BAD", "缺少次数", "TIMES", BigDecimal.TEN, null, null, null, 365, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("次卡套餐必须配置大于 0 的次数");
        verify(packageRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicatePackageCode() {
        when(packageRepository.existsById("PKG_TIMES_10")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new MemberDtos.PackageCreateRequest(
                "PKG_TIMES_10", "重复编码", "TIMES", BigDecimal.TEN, 10, null, null, 365, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("套餐编码已存在");
    }

    @Test
    void changeStatusDisablesPackage() {
        MemberPackage entity = packageEntity("PKG_TIMES_10", "TIMES", 10, null, null, "active");
        when(packageRepository.findById("PKG_TIMES_10")).thenReturn(Optional.of(entity));
        when(packageRepository.save(entity)).thenReturn(entity);

        MemberDtos.PackageView view = service.changeStatus("PKG_TIMES_10", "disabled");

        assertThat(view.status()).isEqualTo("disabled");
        verify(auditService).record(eq("MEMBER_PACKAGE_STATUS"), any(), any(), any());
    }

    private MemberPackage packageEntity(String code, String type, Integer times,
                                        BigDecimal balance, Long points, String status) {
        MemberPackage entity = new MemberPackage();
        entity.setPackageCode(code);
        entity.setPackageName("测试套餐");
        entity.setPackageType(type);
        entity.setPrice(new BigDecimal("1800.00"));
        entity.setTimes(times);
        entity.setBalanceAmount(balance);
        entity.setPointsAmount(points);
        entity.setValidDays(365);
        entity.setStatus(status);
        return entity;
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

    private MemberDtos.AccountView accountView() {
        return new MemberDtos.AccountView(ACCOUNT_ID, "M100000", CUSTOMER_ID, "张三", "standard", "普通会员",
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, 0L, "active", null, null);
    }

    private MemberCard withCardId(MemberCard card) {
        setField(card, CARD_ID);
        return card;
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
