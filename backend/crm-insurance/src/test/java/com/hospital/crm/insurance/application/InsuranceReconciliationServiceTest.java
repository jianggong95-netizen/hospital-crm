package com.hospital.crm.insurance.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.insurance.api.InsuranceDtos;
import com.hospital.crm.insurance.domain.InsuranceCaseRepository;
import com.hospital.crm.insurance.domain.InsuranceCompany;
import com.hospital.crm.insurance.domain.InsuranceCompanyRepository;
import com.hospital.crm.insurance.domain.InsuranceReconciliation;
import com.hospital.crm.insurance.domain.InsuranceReconciliationRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商保对账：账期汇总金额、matched/mismatch 判定、Upsert 与重复确认限制。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsuranceReconciliationServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID RECONCILIATION_ID = UUID.randomUUID();
    private static final String PERIOD = "2026-11";

    @Mock
    private InsuranceReconciliationRepository reconciliationRepository;
    @Mock
    private InsuranceCompanyRepository companyRepository;
    @Mock
    private InsuranceCaseRepository caseRepository;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private InsuranceReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new InsuranceReconciliationService(reconciliationRepository, companyRepository,
                caseRepository, userRepository, auditService);
        InsuranceCompany company = new InsuranceCompany();
        ReflectionTestUtils.setField(company, "id", COMPANY_ID);
        company.setCompanyCode("A100");
        company.setCompanyName("平安健康险");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(companyRepository.findAllById(any())).thenReturn(List.of(company));
        when(reconciliationRepository.save(any(InsuranceReconciliation.class))).thenAnswer(invocation -> {
            InsuranceReconciliation reconciliation = invocation.getArgument(0);
            if (reconciliation.getId() == null) {
                ReflectionTestUtils.setField(reconciliation, "id", RECONCILIATION_ID);
            }
            if (reconciliation.getCreatedAt() == null) {
                ReflectionTestUtils.setField(reconciliation, "createdAt", Instant.now());
            }
            ReflectionTestUtils.setField(reconciliation, "updatedAt", Instant.now());
            return reconciliation;
        });
    }

    @Test
    void generateAggregatesDecidedCasesAndMatchesAmounts() {
        when(caseRepository.countByCompanyIdAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(
                eq(COMPANY_ID), any(), any())).thenReturn(2L);
        when(caseRepository.sumReceivable(eq(COMPANY_ID), any(), any()))
                .thenReturn(new BigDecimal("1000.00"));
        when(caseRepository.sumReceived(eq(COMPANY_ID), any(), any()))
                .thenReturn(new BigDecimal("1000.00"));

        InsuranceDtos.ReconciliationView view = service.generate(
                new InsuranceDtos.ReconciliationCreateRequest(COMPANY_ID, PERIOD, null));

        assertThat(view.period()).isEqualTo(PERIOD);
        assertThat(view.caseCount()).isEqualTo(2);
        assertThat(view.receivableAmount()).isEqualByComparingTo("1000.00");
        assertThat(view.receivedAmount()).isEqualByComparingTo("1000.00");
        assertThat(view.differenceAmount()).isEqualByComparingTo("0.00");
        assertThat(view.status()).isEqualTo("matched");
        assertThat(view.companyName()).isEqualTo("平安健康险");
        verify(auditService).record(eq("INSURANCE_RECONCILE_GENERATE"), anyString(), anyString(), any());
    }

    @Test
    void generateMarksMismatchWhenDifferenceExceedsCent() {
        when(caseRepository.countByCompanyIdAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(
                eq(COMPANY_ID), any(), any())).thenReturn(3L);
        when(caseRepository.sumReceivable(eq(COMPANY_ID), any(), any()))
                .thenReturn(new BigDecimal("1000.00"));
        when(caseRepository.sumReceived(eq(COMPANY_ID), any(), any()))
                .thenReturn(new BigDecimal("900.00"));

        InsuranceDtos.ReconciliationView view = service.generate(
                new InsuranceDtos.ReconciliationCreateRequest(COMPANY_ID, PERIOD, null));

        assertThat(view.differenceAmount()).isEqualByComparingTo("-100.00");
        assertThat(view.status()).isEqualTo("mismatch");
        assertThat(view.caseCount()).isEqualTo(3);
    }

    @Test
    void generateUpdatesExistingBatchForSameCompanyAndPeriod() {
        InsuranceReconciliation existing = new InsuranceReconciliation();
        ReflectionTestUtils.setField(existing, "id", RECONCILIATION_ID);
        ReflectionTestUtils.setField(existing, "createdAt", Instant.now());
        existing.setCompanyId(COMPANY_ID);
        existing.setPeriod(PERIOD);
        when(reconciliationRepository.findByCompanyIdAndPeriod(COMPANY_ID, PERIOD))
                .thenReturn(Optional.of(existing));
        when(caseRepository.countByCompanyIdAndDecidedAtGreaterThanEqualAndDecidedAtLessThan(
                eq(COMPANY_ID), any(), any())).thenReturn(1L);
        when(caseRepository.sumReceivable(eq(COMPANY_ID), any(), any()))
                .thenReturn(new BigDecimal("200.00"));
        when(caseRepository.sumReceived(eq(COMPANY_ID), any(), any()))
                .thenReturn(new BigDecimal("200.00"));

        InsuranceDtos.ReconciliationView view = service.generate(
                new InsuranceDtos.ReconciliationCreateRequest(COMPANY_ID, PERIOD, null));

        ArgumentCaptor<InsuranceReconciliation> captor = ArgumentCaptor.forClass(InsuranceReconciliation.class);
        verify(reconciliationRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(RECONCILIATION_ID);
        assertThat(view.id()).isEqualTo(RECONCILIATION_ID);
        assertThat(view.status()).isEqualTo("matched");
    }

    @Test
    void generateRejectsMalformedPeriod() {
        assertThatThrownBy(() -> service.generate(
                new InsuranceDtos.ReconciliationCreateRequest(COMPANY_ID, "2026-13", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账账期格式必须为 YYYY-MM")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void generateRejectsMissingCompany() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(
                new InsuranceDtos.ReconciliationCreateRequest(COMPANY_ID, PERIOD, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保险公司不存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void confirmRejectsAlreadyConfirmedBatch() {
        InsuranceReconciliation confirmed = new InsuranceReconciliation();
        ReflectionTestUtils.setField(confirmed, "id", RECONCILIATION_ID);
        confirmed.setCompanyId(COMPANY_ID);
        confirmed.setPeriod(PERIOD);
        confirmed.setConfirmedAt(Instant.now());
        confirmed.setConfirmedBy(UUID.randomUUID());
        when(reconciliationRepository.findById(RECONCILIATION_ID)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.confirm(RECONCILIATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复确认")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }
}
