package com.hospital.crm.homevisit.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.homevisit.api.HomeVisitDtos;
import com.hospital.crm.homevisit.domain.HomeStaffQualification;
import com.hospital.crm.homevisit.domain.HomeStaffQualificationRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeStaffQualificationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID QUALIFICATION_ID = UUID.randomUUID();

    @Mock
    private HomeStaffQualificationRepository qualificationRepository;

    @Mock
    private SysUserRepository userRepository;

    @Mock
    private AuditService auditService;

    private HomeStaffQualificationService qualificationService;

    @BeforeEach
    void setUp() {
        qualificationService = new HomeStaffQualificationService(
                qualificationRepository, userRepository, auditService);
        when(qualificationRepository.save(any(HomeStaffQualification.class))).thenAnswer(invocation ->
                withId(invocation.getArgument(0)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(qualificationRepository.findById(QUALIFICATION_ID))
                .thenReturn(Optional.of(qualification("active")));
    }

    @Test
    void createPersistsActiveQualificationAndAudits() {
        HomeVisitDtos.StaffQualificationView view = qualificationService.create(
                new HomeVisitDtos.StaffQualificationCreateRequest(
                        USER_ID, "nurse", "N-001", LocalDate.now().minusYears(1),
                        LocalDate.now().plusYears(1)));

        assertThat(view.status()).isEqualTo("active");
        assertThat(view.username()).isEqualTo("nurse01");
        assertThat(view.displayName()).isEqualTo("护士甲");
        assertThat(view.expired()).isFalse();
        ArgumentCaptor<HomeStaffQualification> captor =
                ArgumentCaptor.forClass(HomeStaffQualification.class);
        verify(qualificationRepository).save(captor.capture());
        assertThat(captor.getValue().getCertNo()).isEqualTo("N-001");
        verify(auditService).record(eq("STAFFQUAL_CREATE"), eq("STAFF_QUALIFICATION"), any(), any());
    }

    @Test
    void createRejectsExpiryBeforeIssue() {
        assertThatThrownBy(() -> qualificationService.create(
                new HomeVisitDtos.StaffQualificationCreateRequest(
                        USER_ID, "nurse", "N-001", LocalDate.now(), LocalDate.now().minusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资质到期日期不能早于发证日期");
        verify(qualificationRepository, never()).save(any());
    }

    @Test
    void createUnknownUserReturns404() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qualificationService.create(
                new HomeVisitDtos.StaffQualificationCreateRequest(
                        USER_ID, "nurse", "N-001", LocalDate.now(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("服务人员账号不存在");
    }

    @Test
    void createDuplicateReturns409() {
        when(qualificationRepository.existsByUserIdAndQualTypeAndCertNo(USER_ID, "nurse", "N-001"))
                .thenReturn(true);

        assertThatThrownBy(() -> qualificationService.create(
                new HomeVisitDtos.StaffQualificationCreateRequest(
                        USER_ID, "nurse", "N-001", LocalDate.now(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该人员已登记相同类型与编号的资质");
    }

    @Test
    void revokeSetsStatusRevoked() {
        HomeVisitDtos.StaffQualificationView view = qualificationService.revoke(QUALIFICATION_ID);

        assertThat(view.status()).isEqualTo("revoked");
        verify(auditService).record(eq("STAFFQUAL_REVOKE"), eq("STAFF_QUALIFICATION"), any(), any());
    }

    @Test
    void revokeAlreadyRevokedReturns422() {
        when(qualificationRepository.findById(QUALIFICATION_ID))
                .thenReturn(Optional.of(qualification("revoked")));

        assertThatThrownBy(() -> qualificationService.revoke(QUALIFICATION_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该资质已停用，请勿重复操作");
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("nurse01");
        user.setDisplayName("护士甲");
        return user;
    }

    private HomeStaffQualification qualification(String status) {
        HomeStaffQualification qualification = new HomeStaffQualification();
        qualification.setUserId(USER_ID);
        qualification.setQualType("nurse");
        qualification.setCertNo("N-001");
        qualification.setIssuedAt(LocalDate.now().minusYears(1));
        qualification.setExpiresAt(LocalDate.now().plusYears(1));
        qualification.setStatus(status);
        return withId(qualification);
    }

    private <T> T withId(T entity) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, QUALIFICATION_ID);
            }
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
