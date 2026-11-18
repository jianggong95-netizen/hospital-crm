package com.hospital.crm.insurance.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.insurance.api.InsuranceDtos;
import com.hospital.crm.insurance.domain.InsuranceCompany;
import com.hospital.crm.insurance.domain.InsuranceCompanyRepository;
import com.hospital.crm.insurance.domain.InsurancePolicy;
import com.hospital.crm.insurance.domain.InsurancePolicyRepository;
import com.hospital.crm.system.audit.AuditService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * 商保主数据：公司编码唯一、联系人手机号仅哈希与脱敏、保单编码唯一与停用公司限制。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsuranceCompanyServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String PHONE_RAW = "138-0000-1111";
    private static final String PHONE_NORMALIZED = "13800001111";
    private static final String PHONE_HASH = "PHONE_HASH_VALUE";

    @Mock
    private InsuranceCompanyRepository companyRepository;
    @Mock
    private InsurancePolicyRepository policyRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private AuditService auditService;

    private InsuranceCompanyService service;

    @BeforeEach
    void setUp() {
        service = new InsuranceCompanyService(companyRepository, policyRepository,
                customerService, hasher, auditService);
        when(hasher.hash(PHONE_NORMALIZED)).thenReturn(PHONE_HASH);
        when(companyRepository.save(any(InsuranceCompany.class))).thenAnswer(invocation -> {
            InsuranceCompany company = invocation.getArgument(0);
            if (company.getId() == null) {
                ReflectionTestUtils.setField(company, "id", COMPANY_ID);
            }
            if (company.getCreatedAt() == null) {
                ReflectionTestUtils.setField(company, "createdAt", Instant.now());
            }
            ReflectionTestUtils.setField(company, "updatedAt", Instant.now());
            return company;
        });
        when(policyRepository.save(any(InsurancePolicy.class))).thenAnswer(invocation -> {
            InsurancePolicy policy = invocation.getArgument(0);
            if (policy.getId() == null) {
                ReflectionTestUtils.setField(policy, "id", POLICY_ID);
            }
            if (policy.getCreatedAt() == null) {
                ReflectionTestUtils.setField(policy, "createdAt", Instant.now());
            }
            ReflectionTestUtils.setField(policy, "updatedAt", Instant.now());
            return policy;
        });
        when(customerService.briefs(any())).thenReturn(Map.of(CUSTOMER_ID,
                new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C1001", "张三", "138****1111", "active")));
        when(customerService.brief(CUSTOMER_ID)).thenReturn(new CustomerDtos.CustomerBrief(
                CUSTOMER_ID, "C1001", "张三", "138****1111", "active"));
    }

    @Test
    void createCompanyRejectsDuplicateCode() {
        when(companyRepository.existsByCompanyCode("A100")).thenReturn(true);

        assertThatThrownBy(() -> service.createCompany(new InsuranceDtos.CreateCompanyRequest(
                "A100", "平安健康险", null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保险公司编码已存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_CONFLICT);
    }

    @Test
    void createCompanyStoresOnlyPhoneHashAndMaskedValue() {
        when(companyRepository.existsByCompanyCode("A100")).thenReturn(false);

        InsuranceDtos.CompanyView view = service.createCompany(new InsuranceDtos.CreateCompanyRequest(
                "A100", "平安健康险", "王经理", PHONE_RAW, "contact@example.com", "email", "重点合作"));

        ArgumentCaptor<InsuranceCompany> captor = ArgumentCaptor.forClass(InsuranceCompany.class);
        verify(companyRepository).save(captor.capture());
        InsuranceCompany saved = captor.getValue();
        assertThat(saved.getContactPhoneHash()).isEqualTo(PHONE_HASH);
        assertThat(saved.getContactPhoneMasked()).isEqualTo("138****1111");
        assertThat(saved.getSettlementType()).isEqualTo("email");
        assertThat(saved.getStatus()).isEqualTo("active");

        assertThat(view.contactPhoneMasked()).isEqualTo("138****1111");
        assertThat(view.toString()).doesNotContain(PHONE_NORMALIZED).doesNotContain(PHONE_RAW);
        verify(auditService).record(eq("INSURANCE_COMPANY_CREATE"), anyString(), anyString(), any());
    }

    @Test
    void createPolicyRejectsDuplicatePolicyNo() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(activeCompany()));
        when(policyRepository.existsByPolicyNo("P200")).thenReturn(true);

        assertThatThrownBy(() -> service.createPolicy(new InsuranceDtos.CreatePolicyRequest(
                "P200", COMPANY_ID, CUSTOMER_ID, null, null, "self",
                LocalDate.now(), LocalDate.now().plusYears(1), BigDecimal.TEN, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保单号已存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_CONFLICT);
    }

    @Test
    void createPolicyRejectsDisabledCompany() {
        InsuranceCompany disabled = activeCompany();
        disabled.setStatus("disabled");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.createPolicy(new InsuranceDtos.CreatePolicyRequest(
                "P200", COMPANY_ID, CUSTOMER_ID, null, null, "self",
                LocalDate.now(), LocalDate.now().plusYears(1), BigDecimal.TEN, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保险公司已停用")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void createPolicyRejectsInvalidValidityRange() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(activeCompany()));
        when(policyRepository.existsByPolicyNo("P200")).thenReturn(false);

        assertThatThrownBy(() -> service.createPolicy(new InsuranceDtos.CreatePolicyRequest(
                "P200", COMPANY_ID, CUSTOMER_ID, null, null, "self",
                LocalDate.now().plusDays(10), LocalDate.now(), BigDecimal.TEN, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生效日期不能晚于失效日期")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void createPolicyReturnsCustomerAndCompanyNames() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(activeCompany()));
        when(companyRepository.findAllById(any())).thenReturn(List.of(activeCompany()));
        when(policyRepository.existsByPolicyNo("P200")).thenReturn(false);

        InsuranceDtos.PolicyView view = service.createPolicy(new InsuranceDtos.CreatePolicyRequest(
                "P200", COMPANY_ID, CUSTOMER_ID, "张三", "张三", "self",
                LocalDate.now(), LocalDate.now().plusYears(1), new BigDecimal("5000.00"), "年缴保单"));

        assertThat(view.policyNo()).isEqualTo("P200");
        assertThat(view.companyName()).isEqualTo("平安健康险");
        assertThat(view.customerName()).isEqualTo("张三");
        assertThat(view.customerNo()).isEqualTo("C1001");
        assertThat(view.coverageAmount()).isEqualByComparingTo("5000.00");
        verify(auditService).record(eq("INSURANCE_POLICY_CREATE"), anyString(), anyString(), any());
    }

    private InsuranceCompany activeCompany() {
        InsuranceCompany company = new InsuranceCompany();
        ReflectionTestUtils.setField(company, "id", COMPANY_ID);
        company.setCompanyCode("A100");
        company.setCompanyName("平安健康险");
        company.setStatus("active");
        company.setSettlementType("manual");
        return company;
    }
}
