package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.api.AppointmentDtos;
import com.hospital.crm.appointment.domain.Appointment;
import com.hospital.crm.appointment.domain.AppointmentRepository;
import com.hospital.crm.appointment.domain.AppointmentType;
import com.hospital.crm.appointment.domain.AppointmentTypeRepository;
import com.hospital.crm.appointment.domain.NoShowRecord;
import com.hospital.crm.appointment.domain.NoShowRecordRepository;
import com.hospital.crm.appointment.domain.ServiceSlot;
import com.hospital.crm.appointment.domain.ServiceSlotRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.RoleScope;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.user.SysUserRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预约核心规则测试：容量占用与释放、取消/改期截止规则、状态机与爽约记录。
 * 使用虚构客户与时段数据，不包含真实患者信息。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ORG_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CUSTOMER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SLOT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID NEW_SLOT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID APPT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private ServiceSlotRepository slotRepository;
    @Mock
    private AppointmentTypeRepository typeRepository;
    @Mock
    private NoShowRecordRepository noShowRepository;
    @Mock
    private ServiceSlotService slotService;
    @Mock
    private AppointmentTypeService typeService;
    @Mock
    private ReminderService reminderService;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private SensitiveDataCipher cipher;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private AuditService auditService;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, slotRepository, typeRepository,
                noShowRepository, slotService, typeService, reminderService, customerService,
                userRepository, cipher, hasher, auditService);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal(), "n/a", principal().getAuthorities()));
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(hasher.hash(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(typeService.load("OUTPATIENT")).thenReturn(type("OUTPATIENT", 2));
        when(typeRepository.findAllByOrderByTypeCodeAsc()).thenReturn(List.of(type("OUTPATIENT", 2)));
        when(customerService.brief(CUSTOMER_ID)).thenReturn(
                new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C100001", "测试客户", "138****0001", "active"));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment saved = invocation.getArgument(0);
            return saved.getId() == null ? withId(saved, APPT_ID) : saved;
        });
        when(appointmentRepository.nextAppointmentNo()).thenReturn(100001L);
        when(noShowRepository.save(any(NoShowRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOccupiesSlotAndSchedulesReminder() {
        when(slotService.load(SLOT_ID)).thenReturn(futureSlot(SLOT_ID, 5));
        when(appointmentRepository.existsByCustomerIdAndSlotIdAndStatusIn(eq(CUSTOMER_ID), eq(SLOT_ID), any()))
                .thenReturn(false);
        when(slotRepository.occupy(eq(SLOT_ID), any())).thenReturn(1);

        AppointmentDtos.AppointmentSummary summary = service.create(new AppointmentDtos.AppointmentCreateRequest(
                CUSTOMER_ID, "OUTPATIENT", SLOT_ID, "13900000009", "首次到院"));

        assertEquals("A100001", summary.appointmentNo());
        assertEquals("BOOKED", summary.status());
        assertEquals("139****0009", summary.contactPhoneMasked());
        verify(slotRepository).occupy(eq(SLOT_ID), any());
        verify(reminderService).scheduleFor(any(Appointment.class), any(Instant.class));
        verify(auditService).record(eq("APPT_CREATE"), eq("APPOINTMENT"), anyString(), any());
    }

    @Test
    void createRejectsWhenSlotIsFull() {
        when(slotService.load(SLOT_ID)).thenReturn(futureSlot(SLOT_ID, 0));
        when(appointmentRepository.existsByCustomerIdAndSlotIdAndStatusIn(eq(CUSTOMER_ID), eq(SLOT_ID), any()))
                .thenReturn(false);
        when(slotRepository.occupy(eq(SLOT_ID), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new AppointmentDtos.AppointmentCreateRequest(CUSTOMER_ID, "OUTPATIENT", SLOT_ID, null, null)));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createRejectsDuplicateActiveAppointment() {
        when(slotService.load(SLOT_ID)).thenReturn(futureSlot(SLOT_ID, 5));
        when(appointmentRepository.existsByCustomerIdAndSlotIdAndStatusIn(eq(CUSTOMER_ID), eq(SLOT_ID), any()))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new AppointmentDtos.AppointmentCreateRequest(CUSTOMER_ID, "OUTPATIENT", SLOT_ID, null, null)));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
        verify(slotRepository, never()).occupy(any(), any());
    }

    @Test
    void cancelReleasesCapacityAndCancelsPendingReminders() {
        Appointment appointment = appointment(APPT_ID, "A100001", "BOOKED", SLOT_ID);
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment));
        when(slotService.load(SLOT_ID)).thenReturn(futureSlot(SLOT_ID, 5));

        AppointmentDtos.AppointmentSummary summary = service.cancel(APPT_ID,
                new AppointmentDtos.CancelRequest("临时有事"));

        assertEquals("CANCELLED", summary.status());
        verify(slotRepository).release(eq(SLOT_ID), any());
        verify(reminderService).cancelPending(APPT_ID);
        verify(auditService).record(eq("APPT_CANCEL"), eq("APPOINTMENT"), anyString(), any());
    }

    @Test
    void cancelRejectsWithinCutoffWindow() {
        Appointment appointment = appointment(APPT_ID, "A100002", "BOOKED", SLOT_ID);
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment));
        when(typeService.load("OUTPATIENT")).thenReturn(type("OUTPATIENT", 24));
        // 1 小时后开始、提前 24 小时截止：必然处于截止窗口内
        var start = java.time.LocalDateTime.now().plusHours(1);
        ServiceSlot soon = slot(SLOT_ID, "OUTPATIENT", start.toLocalDate(), start.toLocalTime(), 5, 1);
        when(slotService.load(SLOT_ID)).thenReturn(soon);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(APPT_ID, new AppointmentDtos.CancelRequest("想取消")));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        verify(slotRepository, never()).release(any(), any());
    }

    @Test
    void rescheduleOccupiesNewSlotAndReleasesOld() {
        Appointment appointment = appointment(APPT_ID, "A100003", "CONFIRMED", SLOT_ID);
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment));
        when(slotService.load(SLOT_ID)).thenReturn(futureSlot(SLOT_ID, 5));
        when(slotService.load(NEW_SLOT_ID)).thenReturn(futureSlot(NEW_SLOT_ID, 3));
        when(appointmentRepository.existsByCustomerIdAndSlotIdAndStatusIn(eq(CUSTOMER_ID), eq(NEW_SLOT_ID), any()))
                .thenReturn(false);
        when(slotRepository.occupy(eq(NEW_SLOT_ID), any())).thenReturn(1);

        AppointmentDtos.AppointmentSummary summary = service.reschedule(APPT_ID,
                new AppointmentDtos.RescheduleRequest(NEW_SLOT_ID, "医生调整"));

        assertEquals(NEW_SLOT_ID, summary.slotId());
        verify(slotRepository).occupy(eq(NEW_SLOT_ID), any());
        verify(slotRepository).release(eq(SLOT_ID), any());
        verify(reminderService).reschedulePlan(eq(APPT_ID), any(Instant.class));
    }

    @Test
    void rescheduleRejectsFullTargetSlot() {
        Appointment appointment = appointment(APPT_ID, "A100004", "BOOKED", SLOT_ID);
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment));
        when(slotService.load(SLOT_ID)).thenReturn(futureSlot(SLOT_ID, 5));
        when(slotService.load(NEW_SLOT_ID)).thenReturn(futureSlot(NEW_SLOT_ID, 2));
        when(appointmentRepository.existsByCustomerIdAndSlotIdAndStatusIn(eq(CUSTOMER_ID), eq(NEW_SLOT_ID), any()))
                .thenReturn(false);
        when(slotRepository.occupy(eq(NEW_SLOT_ID), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.reschedule(APPT_ID,
                new AppointmentDtos.RescheduleRequest(NEW_SLOT_ID, null)));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
        verify(slotRepository, never()).release(any(), any());
    }

    @Test
    void rejectsInvalidStatusTransition() {
        Appointment appointment = appointment(APPT_ID, "A100005", "CANCELLED", SLOT_ID);
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(APPT_ID,
                new AppointmentDtos.StatusChangeRequest("CONFIRMED", null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void noShowAfterStartCreatesRecordAndKeepsCapacity() {
        Appointment appointment = appointment(APPT_ID, "A100006", "CONFIRMED", SLOT_ID);
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment));
        ServiceSlot past = slot(SLOT_ID, "OUTPATIENT", LocalDate.now().minusDays(1), LocalTime.of(9, 0), 5, 3);
        when(slotService.load(SLOT_ID)).thenReturn(past);
        when(noShowRepository.findByAppointmentId(APPT_ID)).thenReturn(Optional.empty());

        AppointmentDtos.AppointmentSummary summary = service.changeStatus(APPT_ID,
                new AppointmentDtos.StatusChangeRequest("NO_SHOW", "未到院"));

        assertEquals("NO_SHOW", summary.status());
        assertNotNull(appointment.getNoShowAt());
        ArgumentCaptor<NoShowRecord> captor = ArgumentCaptor.forClass(NoShowRecord.class);
        verify(noShowRepository).save(captor.capture());
        assertEquals(CUSTOMER_ID, captor.getValue().getCustomerId());
        verify(slotRepository, never()).release(any(), any());
    }

    @Test
    void completeRejectsBeforeSlotStart() {
        Appointment appointment = appointment(APPT_ID, "A100007", "BOOKED", SLOT_ID);
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment));
        when(slotService.load(SLOT_ID)).thenReturn(futureSlot(SLOT_ID, 5));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.changeStatus(APPT_ID,
                new AppointmentDtos.StatusChangeRequest("COMPLETED", null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    private Appointment appointment(UUID id, String no, String status, UUID slotId) {
        Appointment appointment = new Appointment();
        appointment.setAppointmentNo(no);
        appointment.setCustomerId(CUSTOMER_ID);
        appointment.setTypeCode("OUTPATIENT");
        appointment.setSlotId(slotId);
        appointment.setStatus(status);
        appointment.setOrgId(ORG_ID);
        appointment.setCreatedBy(USER_ID);
        return withId(appointment, id);
    }

    private ServiceSlot futureSlot(UUID id, int booked) {
        return slot(id, "OUTPATIENT", LocalDate.now().plusDays(3), LocalTime.of(9, 30), 5, booked);
    }

    private ServiceSlot slot(UUID id, String typeCode, LocalDate date, LocalTime start, int capacity, int booked) {
        ServiceSlot slot = new ServiceSlot();
        slot.setTypeCode(typeCode);
        slot.setSlotDate(date);
        slot.setStartTime(start);
        slot.setEndTime(start.plusMinutes(30));
        slot.setCapacity(capacity);
        slot.setBookedCount(booked);
        slot.setStatus("OPEN");
        return withId(slot, id);
    }

    private AppointmentType type(String code, int cutoffHours) {
        AppointmentType type = new AppointmentType();
        type.setTypeCode(code);
        type.setTypeName("门诊预约");
        type.setDurationMinutes(30);
        type.setCancelCutoffHours(cutoffHours);
        type.setStatus("active");
        return type;
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

    private UserPrincipal principal() {
        return new UserPrincipal(USER_ID, "tester", "测试员工", ORG_ID, "ROOT", "/",
                Set.of("SUPER_ADMIN"), Set.of("crm:appointment:write"), List.<RoleScope>of());
    }
}
