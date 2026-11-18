package com.hospital.crm.insurance.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.insurance.api.InsuranceDtos;
import com.hospital.crm.insurance.domain.InsuranceCase;
import com.hospital.crm.insurance.domain.InsuranceCaseRepository;
import com.hospital.crm.insurance.domain.InsuranceCompanyRepository;
import com.hospital.crm.insurance.domain.InsuranceEmail;
import com.hospital.crm.insurance.domain.InsuranceEmailAttachment;
import com.hospital.crm.insurance.domain.InsuranceEmailAttachmentRepository;
import com.hospital.crm.insurance.domain.InsuranceEmailRepository;
import com.hospital.crm.insurance.domain.InsuranceEvent;
import com.hospital.crm.insurance.domain.InsuranceEventRepository;
import com.hospital.crm.insurance.domain.InsuranceMaterial;
import com.hospital.crm.insurance.domain.InsuranceMaterialRepository;
import com.hospital.crm.insurance.domain.InsurancePolicy;
import com.hospital.crm.insurance.domain.InsurancePolicyRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商保案件：状态机合法性与必填校验、预计赔付与实际到账互不覆盖、事件留痕、
 * 材料哈希校验与入站邮件幂等。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsuranceCaseServiceTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final UUID POLICY_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID EMAIL_ID = UUID.randomUUID();
    private static final UUID MATERIAL_ID = UUID.randomUUID();
    private static final String VALID_HASH = "a".repeat(64);

    @Mock
    private InsuranceCaseRepository caseRepository;
    @Mock
    private InsurancePolicyRepository policyRepository;
    @Mock
    private InsuranceCompanyRepository companyRepository;
    @Mock
    private InsuranceMaterialRepository materialRepository;
    @Mock
    private InsuranceEmailRepository emailRepository;
    @Mock
    private InsuranceEmailAttachmentRepository attachmentRepository;
    @Mock
    private InsuranceEventRepository eventRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private InsuranceCaseService service;

    @BeforeEach
    void setUp() {
        service = new InsuranceCaseService(caseRepository, policyRepository, companyRepository,
                materialRepository, emailRepository, attachmentRepository, eventRepository,
                customerService, userRepository, auditService);
        when(caseRepository.nextCaseNo()).thenReturn(800001L);
        when(caseRepository.save(any(InsuranceCase.class))).thenAnswer(invocation -> {
            InsuranceCase insuranceCase = invocation.getArgument(0);
            if (insuranceCase.getId() == null) {
                ReflectionTestUtils.setField(insuranceCase, "id", CASE_ID);
            }
            if (insuranceCase.getCreatedAt() == null) {
                ReflectionTestUtils.setField(insuranceCase, "createdAt", Instant.now());
            }
            ReflectionTestUtils.setField(insuranceCase, "updatedAt", Instant.now());
            return insuranceCase;
        });
        when(materialRepository.save(any(InsuranceMaterial.class))).thenAnswer(invocation -> {
            InsuranceMaterial material = invocation.getArgument(0);
            if (material.getId() == null) {
                ReflectionTestUtils.setField(material, "id", MATERIAL_ID);
            }
            if (material.getCreatedAt() == null) {
                ReflectionTestUtils.setField(material, "createdAt", Instant.now());
            }
            return material;
        });
        when(emailRepository.save(any(InsuranceEmail.class))).thenAnswer(invocation -> {
            InsuranceEmail email = invocation.getArgument(0);
            if (email.getId() == null) {
                ReflectionTestUtils.setField(email, "id", EMAIL_ID);
            }
            if (email.getCreatedAt() == null) {
                ReflectionTestUtils.setField(email, "createdAt", Instant.now());
            }
            return email;
        });
        when(attachmentRepository.save(any(InsuranceEmailAttachment.class))).thenAnswer(invocation -> {
            InsuranceEmailAttachment attachment = invocation.getArgument(0);
            if (attachment.getId() == null) {
                ReflectionTestUtils.setField(attachment, "id", UUID.randomUUID());
            }
            if (attachment.getCreatedAt() == null) {
                ReflectionTestUtils.setField(attachment, "createdAt", Instant.now());
            }
            return attachment;
        });
        when(eventRepository.save(any(InsuranceEvent.class))).thenAnswer(invocation -> {
            InsuranceEvent event = invocation.getArgument(0);
            if (event.getId() == null) {
                ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
            }
            if (event.getCreatedAt() == null) {
                ReflectionTestUtils.setField(event, "createdAt", Instant.now());
            }
            return event;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(companyRepository.findAllById(any())).thenReturn(List.of());
        when(policyRepository.findAllById(any())).thenReturn(List.of());
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("registered")));
    }

    @Test
    void transitionRejectsIllegalTargetState() {
        assertThatThrownBy(() -> service.transition(CASE_ID,
                new InsuranceDtos.TransitionRequest("quotation", null, new BigDecimal("100"),
                        null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许从 registered 流转到 quotation")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void transitionToQuotationRequiresAmount() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("eligibility_check")));

        assertThatThrownBy(() -> service.transition(CASE_ID,
                new InsuranceDtos.TransitionRequest("quotation", null, null,
                        null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("进入询价状态必须填写询价金额")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void approvedAmountCannotExceedHospitalReceivable() {
        InsuranceCase insuranceCase = caseEntity("under_review");
        insuranceCase.setHospitalReceivable(new BigDecimal("1000.00"));
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(insuranceCase));

        assertThatThrownBy(() -> service.transition(CASE_ID,
                new InsuranceDtos.TransitionRequest("approved", null, null, null, null, null,
                        null, null, new BigDecimal("1200.00"), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("核定赔付金额不能大于医院应收金额")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void rejectedRequiresReason() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("under_review")));

        assertThatThrownBy(() -> service.transition(CASE_ID,
                new InsuranceDtos.TransitionRequest("rejected", null, null, null, null, null,
                        null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回必须填写原因")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void reconciledRequiresActualReceived() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("approved")));

        assertThatThrownBy(() -> service.transition(CASE_ID,
                new InsuranceDtos.TransitionRequest("reconciled", null, null, null, null, null,
                        null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对账必须填写实际到账金额")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void estimatedPayoutAndActualReceivedNeverOverwriteEachOther() {
        InsuranceCase insuranceCase = caseEntity("under_review");
        insuranceCase.setHospitalReceivable(new BigDecimal("1000.00"));
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(insuranceCase));

        service.transition(CASE_ID, new InsuranceDtos.TransitionRequest("approved", null, null,
                null, null, null, null, null, new BigDecimal("800.00"), null));

        assertThat(insuranceCase.getClaimDecision()).isEqualTo("approved");
        assertThat(insuranceCase.getEstimatedPayout()).isEqualByComparingTo("800.00");
        assertThat(insuranceCase.getActualReceived()).isEqualByComparingTo("0");
        assertThat(insuranceCase.getDecidedAt()).isNotNull();

        service.transition(CASE_ID, new InsuranceDtos.TransitionRequest("reconciled", null, null,
                null, null, null, null, null, null, new BigDecimal("700.00")));

        assertThat(insuranceCase.getState()).isEqualTo("reconciled");
        assertThat(insuranceCase.getActualReceived()).isEqualByComparingTo("700.00");
        assertThat(insuranceCase.getEstimatedPayout()).isEqualByComparingTo("800.00");
        assertThat(insuranceCase.getReconciliationDiff()).isEqualByComparingTo("-100.00");
        assertThat(insuranceCase.getReconciledAt()).isNotNull();
    }

    @Test
    void everyTransitionWritesEventWithFromAndToState() {
        InsuranceCase insuranceCase = caseEntity("eligibility_check");
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(insuranceCase));

        service.transition(CASE_ID, new InsuranceDtos.TransitionRequest("quotation", "已向保司询价",
                new BigDecimal("300.00"), null, null, null, null, null, null, null));

        ArgumentCaptor<InsuranceEvent> captor = ArgumentCaptor.forClass(InsuranceEvent.class);
        verify(eventRepository).save(captor.capture());
        InsuranceEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("STATE_CHANGE");
        assertThat(event.getFromState()).isEqualTo("eligibility_check");
        assertThat(event.getToState()).isEqualTo("quotation");
        assertThat(event.getDetail()).containsEntry("quotationAmount", new BigDecimal("300.00"));

        ArgumentCaptor<java.util.Map<String, Object>> audit = auditCaptor();
        verify(auditService).record(eq("INSURANCE_CASE_TRANSITION"), anyString(), anyString(), audit.capture());
        assertThat(audit.getValue()).containsEntry("from", "eligibility_check")
                .containsEntry("to", "quotation");
    }

    @Test
    void createCaseRejectsExpiredPolicy() {
        InsurancePolicy policy = policyEntity();
        policy.setValidTo(LocalDate.now().minusDays(1));
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy));

        assertThatThrownBy(() -> service.create(new InsuranceDtos.CreateCaseRequest(POLICY_ID,
                "outpatient", "门诊商保直付", new BigDecimal("200.00"), BigDecimal.ZERO, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保单已过期")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void createCaseStartsAtRegisteredAndWritesRegisterEvent() {
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policyEntity()));

        InsuranceDtos.CaseDetailView detail = service.create(new InsuranceDtos.CreateCaseRequest(
                POLICY_ID, "inpatient", "住院手术", new BigDecimal("12000.00"),
                new BigDecimal("2000.00"), "加快审核"));

        assertThat(detail.caseView().caseNo()).isEqualTo("IC800001");
        assertThat(detail.caseView().state()).isEqualTo("registered");
        assertThat(detail.caseView().companyId()).isEqualTo(COMPANY_ID);
        assertThat(detail.caseView().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(detail.caseView().hospitalReceivable()).isEqualByComparingTo("12000.00");

        ArgumentCaptor<InsuranceEvent> captor = ArgumentCaptor.forClass(InsuranceEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("REGISTER");
        assertThat(captor.getValue().getFromState()).isNull();
        assertThat(captor.getValue().getToState()).isEqualTo("registered");
    }

    @Test
    void materialRequiresHexFileHash() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("registered")));

        assertThatThrownBy(() -> service.addMaterial(CASE_ID, new InsuranceDtos.MaterialCreateRequest(
                "invoice", "发票.jpg", "cases/1/invoice.jpg", "not-a-hash", 1024L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件哈希必须为 64 位十六进制")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void materialCannotBeAddedToReconciledCase() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("reconciled")));

        assertThatThrownBy(() -> service.addMaterial(CASE_ID, new InsuranceDtos.MaterialCreateRequest(
                "invoice", "发票.jpg", "cases/1/invoice.jpg", VALID_HASH, 1024L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已对账案件不能新增材料")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void emailAttachmentRequiresHexFileHash() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("registered")));

        // 附件哈希缺失
        assertThatThrownBy(() -> service.addEmail(CASE_ID, new InsuranceDtos.EmailCreateRequest(
                "outbound", null, "商保预授权申请", "crm@hospital.com", "insurer@example.com",
                null, Instant.now(), null,
                List.of(new InsuranceDtos.AttachmentCreateRequest("清单.pdf", null, 1024L)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件哈希必须为 64 位十六进制")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        // 附件哈希非 64 位十六进制
        assertThatThrownBy(() -> service.addEmail(CASE_ID, new InsuranceDtos.EmailCreateRequest(
                "outbound", null, "商保预授权申请", "crm@hospital.com", "insurer@example.com",
                null, Instant.now(), null,
                List.of(new InsuranceDtos.AttachmentCreateRequest("清单.pdf", "xyz", 1024L)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件哈希必须为 64 位十六进制")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void inboundEmailIsIdempotentByMessageId() {
        InsuranceEmail existing = new InsuranceEmail();
        ReflectionTestUtils.setField(existing, "id", EMAIL_ID);
        ReflectionTestUtils.setField(existing, "createdAt", Instant.now());
        existing.setCaseId(CASE_ID);
        existing.setDirection("inbound");
        existing.setMessageId("msg-001");
        existing.setSubject("理赔结论通知");
        existing.setStatus("received");
        when(emailRepository.findByMessageId("msg-001")).thenReturn(Optional.of(existing));

        InsuranceDtos.EmailView view = service.addEmail(CASE_ID, new InsuranceDtos.EmailCreateRequest(
                "inbound", "msg-001", "理赔结论通知", "insurer@example.com", "crm@hospital.com",
                "核定通过", null, Instant.now(), List.of()));

        assertThat(view.id()).isEqualTo(EMAIL_ID);
        assertThat(view.status()).isEqualTo("received");
        assertThat(view.attachmentCount()).isZero();
        verify(emailRepository, never()).save(any(InsuranceEmail.class));
        verify(eventRepository, never()).save(any(InsuranceEvent.class));
    }

    @Test
    void outboundEmailRecordsAttachmentCountAndEvent() {
        when(caseRepository.findById(CASE_ID)).thenReturn(Optional.of(caseEntity("registered")));

        InsuranceDtos.EmailView view = service.addEmail(CASE_ID, new InsuranceDtos.EmailCreateRequest(
                "outbound", "out-001", "预授权申请", "crm@hospital.com", "insurer@example.com",
                "请审批", Instant.now(), null,
                List.of(new InsuranceDtos.AttachmentCreateRequest("清单.pdf", VALID_HASH, 2048L))));

        assertThat(view.status()).isEqualTo("sent");
        assertThat(view.attachmentCount()).isEqualTo(1);
        assertThat(view.attachments()).hasSize(1);
        assertThat(view.attachments().get(0).fileHash()).isEqualTo(VALID_HASH);

        ArgumentCaptor<InsuranceEvent> captor = ArgumentCaptor.forClass(InsuranceEvent.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("EMAIL_OUTBOUND");
        assertThat(captor.getValue().getDetail()).containsEntry("messageId", "out-001")
                .containsEntry("attachmentCount", 1);
        verify(auditService).record(eq("INSURANCE_EMAIL_RECORD"), anyString(), anyString(), any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<java.util.Map<String, Object>> auditCaptor() {
        return ArgumentCaptor.forClass(java.util.Map.class);
    }

    private InsuranceCase caseEntity(String state) {
        InsuranceCase insuranceCase = new InsuranceCase();
        ReflectionTestUtils.setField(insuranceCase, "id", CASE_ID);
        ReflectionTestUtils.setField(insuranceCase, "createdAt", Instant.now());
        ReflectionTestUtils.setField(insuranceCase, "updatedAt", Instant.now());
        insuranceCase.setCaseNo("IC800001");
        insuranceCase.setCompanyId(COMPANY_ID);
        insuranceCase.setPolicyId(POLICY_ID);
        insuranceCase.setCustomerId(CUSTOMER_ID);
        insuranceCase.setCaseType("outpatient");
        insuranceCase.setState(state);
        return insuranceCase;
    }

    private InsurancePolicy policyEntity() {
        InsurancePolicy policy = new InsurancePolicy();
        ReflectionTestUtils.setField(policy, "id", POLICY_ID);
        policy.setPolicyNo("P200");
        policy.setCompanyId(COMPANY_ID);
        policy.setCustomerId(CUSTOMER_ID);
        policy.setStatus("active");
        policy.setRelation("self");
        policy.setValidFrom(LocalDate.now().minusMonths(1));
        policy.setValidTo(LocalDate.now().plusYears(1));
        return policy;
    }
}
