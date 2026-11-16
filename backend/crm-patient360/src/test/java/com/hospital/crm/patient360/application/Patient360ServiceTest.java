package com.hospital.crm.patient360.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.api.CustomerIdentityDtos;
import com.hospital.crm.customer.application.CustomerIdentityService;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.family.api.FamilyDtos;
import com.hospital.crm.family.application.FamilyService;
import com.hospital.crm.his.api.HisDtos;
import com.hospital.crm.his.application.HisPatientService;
import com.hospital.crm.his.support.HisProperties;
import com.hospital.crm.patient360.api.Patient360Dtos;
import com.hospital.crm.patient360.support.Patient360Cache;
import com.hospital.crm.patient360.support.Patient360Properties;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 患者360聚合服务测试：角色字段裁剪、HIS 未绑定/不可用降级、分页与短时缓存。
 * 所有患者数据均为虚构测试数据，不连接真实 HIS。
 */
@ExtendWith(MockitoExtension.class)
class Patient360ServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EXTERNAL_PATIENT_ID = "P-TEST-360-001";
    private static final Instant FETCHED_AT = Instant.parse("2026-11-17T02:00:00Z");

    @Mock
    private CustomerService customerService;
    @Mock
    private FamilyService familyService;
    @Mock
    private CustomerIdentityService customerIdentityService;
    @Mock
    private HisPatientService hisPatientService;
    @Mock
    private AuditService auditService;

    private Patient360Properties properties;
    private Patient360Service service;

    @BeforeEach
    void setUp() {
        properties = new Patient360Properties();
        properties.setDefaultPageSize(2);
        properties.setMaxPageSize(2);
        service = new Patient360Service(
                customerService,
                familyService,
                customerIdentityService,
                hisPatientService,
                new HisProperties(),
                new Patient360Cache(properties),
                properties,
                auditService);
        when(customerService.detail(CUSTOMER_ID)).thenReturn(customerDetail(false));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restrictsFamilyAndHisSectionsWithoutSectionPermissions() {
        authenticate("crm:patient360:read");

        Patient360Dtos.Patient360View view = view("", 1, 10, 1, 10, false);

        assertEquals("RESTRICTED", view.family().dataStatus());
        assertNull(view.family().family());
        assertEquals("RESTRICTED", view.his().dataStatus());
        assertNull(view.his().visits());
        assertNull(view.his().admissions());
        assertFalse(view.sensitiveVisible());
        verify(familyService, never()).membershipOf(any());
        verify(customerIdentityService, never()).resolve(any(), any(), any());
        verify(hisPatientService, never()).patient360(any());
    }

    @Test
    void reportsNotLinkedWhenCustomerHasNoHisIdentity() {
        authenticate("crm:patient360:read", "crm:family:read", "his:patient:read");
        when(familyService.membershipOf(CUSTOMER_ID)).thenReturn(java.util.Optional.empty());
        when(customerIdentityService.resolve(CUSTOMER_ID, "HIS", "his_patient_id"))
                .thenReturn(java.util.Optional.empty());

        Patient360Dtos.Patient360View view = view("", 1, 10, 1, 10, false);

        assertEquals("NOT_FOUND", view.family().dataStatus());
        assertEquals("NOT_LINKED", view.his().dataStatus());
        assertNull(view.his().visits());
        assertFalse(view.his().requiresManualVerification());
        verify(hisPatientService, never()).patient360(any());
    }

    @Test
    void reportsUnavailableWithoutFabricatingClinicalData() {
        authenticate("crm:patient360:read", "crm:family:read", "his:patient:read");
        when(familyService.membershipOf(CUSTOMER_ID))
                .thenReturn(java.util.Optional.of(membership()));
        when(customerIdentityService.resolve(CUSTOMER_ID, "HIS", "his_patient_id"))
                .thenReturn(java.util.Optional.of(identity()));
        when(hisPatientService.patient360(EXTERNAL_PATIENT_ID))
                .thenThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "HIS 集成未启用"));

        Patient360Dtos.Patient360View view = view("", 1, 10, 1, 10, false);

        assertEquals("UNAVAILABLE", view.his().dataStatus());
        assertEquals("SERVICE_UNAVAILABLE", view.his().errorCode());
        assertNull(view.his().patientNameMasked());
        assertNull(view.his().visits());
        assertNull(view.his().admissions());
        assertEquals("UNAVAILABLE", view.dataSources().stream()
                .filter(source -> "his".equals(source.section()))
                .findFirst()
                .orElseThrow()
                .dataStatus());
    }

    @Test
    void passesThroughStaleSnapshotAndPaginatesVisits() {
        authenticate("crm:patient360:read", "crm:family:read", "his:patient:read");
        when(familyService.membershipOf(CUSTOMER_ID))
                .thenReturn(java.util.Optional.of(membership()));
        when(customerIdentityService.resolve(CUSTOMER_ID, "HIS", "his_patient_id"))
                .thenReturn(java.util.Optional.of(identity()));
        when(hisPatientService.patient360(EXTERNAL_PATIENT_ID)).thenReturn(his360("STALE", true, 3, 2));

        Patient360Dtos.Patient360View view = view("", 2, 2, 1, 2, false);

        assertEquals("STALE", view.his().dataStatus());
        assertTrue(view.his().stale());
        assertFalse(view.his().cached());
        assertNotNull(view.his().visits());
        assertEquals(3, view.his().visits().total());
        assertEquals(1, view.his().visits().items().size());
        assertEquals("V-3", view.his().visits().items().get(0).visitId());
        assertNotNull(view.his().admissions());
        assertEquals(2, view.his().admissions().total());
    }

    @Test
    void reusesShortLivedCacheAndRefreshesOnDemand() {
        authenticate("crm:patient360:read", "crm:family:read", "his:patient:read");
        when(familyService.membershipOf(CUSTOMER_ID))
                .thenReturn(java.util.Optional.of(membership()));
        when(customerIdentityService.resolve(CUSTOMER_ID, "HIS", "his_patient_id"))
                .thenReturn(java.util.Optional.of(identity()));
        when(hisPatientService.patient360(EXTERNAL_PATIENT_ID)).thenReturn(his360("LIVE", false, 1, 0));

        Patient360Dtos.Patient360View first = view("visits", 1, 10, 1, 10, false);
        Patient360Dtos.Patient360View second = view("visits", 1, 10, 1, 10, false);
        Patient360Dtos.Patient360View refreshed = view("visits", 1, 10, 1, 10, true);

        assertFalse(first.his().cached());
        assertTrue(second.his().cached());
        assertFalse(refreshed.his().cached());
        verify(hisPatientService, times(2)).patient360(EXTERNAL_PATIENT_ID);
    }

    @Test
    void rejectsUnknownIncludeSections() {
        authenticate("crm:patient360:read");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> view("visits,unknown", 1, 10, 1, 10, false));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void listsContractSectionsNotDeliveredInThisRelease() {
        authenticate("crm:patient360:read");

        Patient360Dtos.Patient360View view = view("appointments", 1, 10, 1, 10, false);

        assertEquals(List.of("appointments"), view.unavailableSections());
        assertEquals("NOT_REQUESTED", view.his().dataStatus());
    }

    private Patient360Dtos.Patient360View view(String include,
                                               int visitPage,
                                               int visitPageSize,
                                               int admissionPage,
                                               int admissionPageSize,
                                               boolean refresh) {
        return service.view(CUSTOMER_ID, include, visitPage, visitPageSize,
                admissionPage, admissionPageSize, refresh);
    }

    private void authenticate(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tester",
                        "n/a",
                        Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }

    private CustomerDtos.CustomerDetail customerDetail(boolean sensitiveVisible) {
        return new CustomerDtos.CustomerDetail(
                CUSTOMER_ID,
                "C100001",
                "测试患者",
                "female",
                LocalDate.of(1990, 1, 1),
                "138****0001",
                "110101********1234",
                sensitiveVisible ? "13800000001" : null,
                sensitiveVisible ? "110101199001011234" : null,
                sensitiveVisible,
                "manual",
                "active",
                null,
                List.of(),
                FETCHED_AT,
                FETCHED_AT);
    }

    private FamilyDtos.FamilyMembership membership() {
        return new FamilyDtos.FamilyMembership(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "F100001",
                "测试家庭",
                "active",
                "self",
                "本人",
                true,
                2,
                FETCHED_AT);
    }

    private CustomerIdentityDtos.ResolvedIdentity identity() {
        return new CustomerIdentityDtos.ResolvedIdentity(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                CUSTOMER_ID,
                "HIS",
                "his_patient_id",
                EXTERNAL_PATIENT_ID,
                true,
                FETCHED_AT);
    }

    private HisDtos.Patient360 his360(String status, boolean stale, int visitCount, int admissionCount) {
        List<HisDtos.VisitItem> visits = java.util.stream.IntStream.rangeClosed(1, visitCount)
                .mapToObj(index -> new HisDtos.VisitItem(
                        "V-" + index,
                        FETCHED_AT.minusSeconds(index * 3600L),
                        "内科",
                        "测试医生",
                        "测试诊断"))
                .toList();
        List<HisDtos.AdmissionItem> admissions = java.util.stream.IntStream.rangeClosed(1, admissionCount)
                .mapToObj(index -> new HisDtos.AdmissionItem(
                        "A-" + index,
                        FETCHED_AT.minusSeconds(index * 86400L),
                        FETCHED_AT.minusSeconds(index * 43200L),
                        "一病区",
                        "测试诊断"))
                .toList();
        return new HisDtos.Patient360(
                EXTERNAL_PATIENT_ID,
                "MRN-0001",
                "测*",
                "female",
                LocalDate.of(1990, 1, 1),
                "138****0001",
                visits,
                admissions,
                "HIS",
                FETCHED_AT,
                FETCHED_AT.plusSeconds(1800),
                stale,
                status,
                new BigDecimal("0.9900"),
                true);
    }
}
