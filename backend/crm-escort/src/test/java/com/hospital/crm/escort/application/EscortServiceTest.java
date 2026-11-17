package com.hospital.crm.escort.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.escort.api.EscortDtos;
import com.hospital.crm.escort.domain.Escort;
import com.hospital.crm.escort.domain.EscortQualification;
import com.hospital.crm.escort.domain.EscortQualificationRepository;
import com.hospital.crm.escort.domain.EscortRepository;
import com.hospital.crm.escort.domain.EscortReviewRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 陪诊师档案与资质规则测试：手机号三列加密存储、资质登记与停用、状态启停审计。
 * 使用虚构陪诊师数据，不包含真实个人信息。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscortServiceTest {

    private static final UUID ESCORT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private EscortRepository escortRepository;
    @Mock
    private EscortQualificationRepository qualificationRepository;
    @Mock
    private EscortReviewRepository reviewRepository;
    @Mock
    private SensitiveDataCipher cipher;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private AuditService auditService;

    private EscortService service;

    /** 模拟持久化后的陪诊师，save/findById 共用同一实例以便断言写回效果。 */
    private Escort storedEscort;

    @BeforeEach
    void setUp() {
        service = new EscortService(escortRepository, qualificationRepository, reviewRepository,
                cipher, hasher, auditService);
        storedEscort = activeEscort();
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(hasher.hash(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(escortRepository.nextEscortNo()).thenReturn(100000L);
        when(escortRepository.save(any(Escort.class))).thenAnswer(invocation -> {
            Escort saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                withId(saved, ESCORT_ID);
                storedEscort = saved;
            }
            return saved;
        });
        when(escortRepository.findById(ESCORT_ID)).thenAnswer(invocation ->
                Optional.of(storedEscort));
        when(qualificationRepository.save(any(EscortQualification.class))).thenAnswer(invocation -> {
            EscortQualification saved = invocation.getArgument(0);
            return saved.getId() == null ? withId(saved, QUAL_ID) : saved;
        });
        when(qualificationRepository.findByEscortIdOrderByIssuedAtDesc(ESCORT_ID)).thenReturn(List.of());
    }

    @Test
    void createEncryptsPhoneAndGeneratesEscortNo() {
        EscortDtos.EscortDetail detail = service.create(new EscortDtos.EscortCreateRequest(
                "张护士", "female", "13900000009", List.of("OUTPATIENT", "INPATIENT"), "五年前陪诊经验"));

        assertEquals("ES100000", detail.summary().escortNo());
        assertEquals("139****0009", detail.summary().phoneMasked());
        assertEquals(List.of("OUTPATIENT", "INPATIENT"), detail.summary().serviceAreas());

        ArgumentCaptor<Escort> captor = ArgumentCaptor.forClass(Escort.class);
        verify(escortRepository).save(captor.capture());
        assertEquals("enc:13900000009", captor.getValue().getPhoneEncrypted());
        assertEquals("hash:13900000009", captor.getValue().getPhoneHash());
        assertEquals("139****0009", captor.getValue().getPhoneMasked());
        verify(auditService).record(eq("ESCORT_CREATE"), eq("ESCORT"), anyString(), any());
    }

    @Test
    void createWithoutPhoneLeavesSensitiveColumnsNull() {
        service.create(new EscortDtos.EscortCreateRequest("李陪护", null, null, null, null));

        ArgumentCaptor<Escort> captor = ArgumentCaptor.forClass(Escort.class);
        verify(escortRepository).save(captor.capture());
        assertNull(captor.getValue().getPhoneEncrypted());
        assertNull(captor.getValue().getPhoneHash());
        assertNull(captor.getValue().getPhoneMasked());
        assertEquals("unknown", captor.getValue().getGender());
    }

    @Test
    void updateClearsPhoneWhenBlankProvided() {
        EscortDtos.EscortDetail detail = service.update(ESCORT_ID,
                new EscortDtos.EscortUpdateRequest("张护士改", null, "", null, null));

        assertEquals("张护士改", detail.summary().name());
        assertNull(detail.summary().phoneMasked());
        verify(auditService).record(eq("ESCORT_UPDATE"), eq("ESCORT"), anyString(), any());
    }

    @Test
    void changeStatusPersistsAndAudits() {
        EscortDtos.EscortDetail detail = service.changeStatus(ESCORT_ID,
                new EscortDtos.EscortStatusRequest("disabled"));

        assertEquals("disabled", detail.summary().status());
        verify(auditService).record(eq("ESCORT_STATUS"), eq("ESCORT"), anyString(), any());
    }

    @Test
    void addQualificationPersistsAndAudits() {
        EscortDtos.EscortQualificationView view = service.addQualification(ESCORT_ID,
                new EscortDtos.QualificationCreateRequest("nurse", "N2020-888",
                        LocalDate.now().minusYears(3), LocalDate.now().plusYears(2)));

        assertEquals("active", view.status());
        assertFalse(view.expired());
        assertEquals(QUAL_ID, view.id());
        verify(auditService).record(eq("ESCORTQUAL_CREATE"), eq("ESCORT_QUALIFICATION"), anyString(), any());
    }

    @Test
    void addQualificationRejectsDuplicate() {
        when(qualificationRepository.existsByEscortIdAndQualTypeAndCertNo(ESCORT_ID, "nurse", "N2020-888"))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.addQualification(ESCORT_ID,
                new EscortDtos.QualificationCreateRequest("nurse", "N2020-888", LocalDate.now(), null)));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
        verify(qualificationRepository, never()).save(any(EscortQualification.class));
    }

    @Test
    void addQualificationRejectsExpiryBeforeIssue() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.addQualification(ESCORT_ID,
                new EscortDtos.QualificationCreateRequest("nurse", "N2020-999",
                        LocalDate.now(), LocalDate.now().minusDays(1))));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void revokeQualificationSetsRevokedAndAudits() {
        when(qualificationRepository.findByIdAndEscortId(QUAL_ID, ESCORT_ID))
                .thenReturn(Optional.of(qualification("active", null)));

        EscortDtos.EscortQualificationView view = service.revokeQualification(ESCORT_ID, QUAL_ID);

        assertEquals("revoked", view.status());
        verify(auditService).record(eq("ESCORTQUAL_REVOKE"), eq("ESCORT_QUALIFICATION"), anyString(), any());
    }

    @Test
    void revokeAlreadyRevokedQualificationRejected() {
        when(qualificationRepository.findByIdAndEscortId(QUAL_ID, ESCORT_ID))
                .thenReturn(Optional.of(qualification("revoked", null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.revokeQualification(ESCORT_ID, QUAL_ID));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void revokeUnknownQualificationReturnsNotFound() {
        when(qualificationRepository.findByIdAndEscortId(QUAL_ID, ESCORT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.revokeQualification(ESCORT_ID, QUAL_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void detailMarksExpiredQualificationAndAggregatesRating() {
        EscortQualification expired = qualification("active", LocalDate.now().minusDays(1));
        when(qualificationRepository.findByEscortIdOrderByIssuedAtDesc(ESCORT_ID))
                .thenReturn(List.of(expired));
        when(reviewRepository.ratingSummary(any())).thenReturn(
                List.<Object[]>of(new Object[]{ESCORT_ID, 4.333333, 6L}));

        EscortDtos.EscortDetail detail = service.detail(ESCORT_ID);

        assertTrue(detail.qualifications().get(0).expired());
        assertEquals(4.33, detail.summary().avgRating());
        assertEquals(6L, detail.summary().reviewCount());
    }

    private Escort activeEscort() {
        Escort escort = new Escort();
        escort.setEscortNo("ES100000");
        escort.setName("张护士");
        escort.setGender("female");
        escort.setPhoneEncrypted("enc:13900000009");
        escort.setPhoneHash("hash:13900000009");
        escort.setPhoneMasked("139****0009");
        escort.setServiceAreas("OUTPATIENT,INPATIENT");
        escort.setStatus("active");
        return withId(escort, ESCORT_ID);
    }

    private EscortQualification qualification(String status, LocalDate expiresAt) {
        EscortQualification qualification = new EscortQualification();
        qualification.setEscortId(ESCORT_ID);
        qualification.setQualType("nurse");
        qualification.setCertNo("N2020-888");
        qualification.setIssuedAt(LocalDate.now().minusYears(3));
        qualification.setExpiresAt(expiresAt);
        qualification.setStatus(status);
        return withId(qualification, QUAL_ID);
    }

    private static <T> T withId(T entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }
}
