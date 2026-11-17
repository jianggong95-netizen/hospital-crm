package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.api.AppointmentDtos;
import com.hospital.crm.appointment.domain.Appointment;
import com.hospital.crm.appointment.domain.AppointmentReminder;
import com.hospital.crm.appointment.domain.AppointmentReminderRepository;
import com.hospital.crm.appointment.domain.AppointmentRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提醒重试规则测试：成功置 SENT、失败退避重试、达到上限停止自动重试、人工重试可继续。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderServiceTest {

    private static final UUID APPT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REMINDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AppointmentReminderRepository reminderRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentNotifier notifier;
    @Mock
    private AuditService auditService;

    private ReminderService service;

    @BeforeEach
    void setUp() {
        service = new ReminderService(reminderRepository, appointmentRepository, notifier, auditService);
        when(reminderRepository.save(any(AppointmentReminder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(appointment()));
    }

    @Test
    void dispatchMarksSentOnSuccess() {
        AppointmentReminder reminder = reminder("PENDING", 0, 3);
        when(reminderRepository.findDue(any(), any())).thenReturn(List.of(reminder));

        AppointmentDtos.DispatchResult result = service.dispatchDue();

        assertEquals(1, result.scanned());
        assertEquals(1, result.sent());
        assertEquals("SENT", reminder.getStatus());
        assertNotNull(reminder.getSentAt());
        verify(auditService).record(org.mockito.ArgumentMatchers.eq("REMINDER_DISPATCH"),
                org.mockito.ArgumentMatchers.eq("APPT_REMINDER"), any(), any());
    }

    @Test
    void dispatchRecordsBackoffRetryOnFailure() {
        AppointmentReminder reminder = reminder("PENDING", 0, 3);
        when(reminderRepository.findDue(any(), any())).thenReturn(List.of(reminder));
        doThrow(new RuntimeException("短信网关超时")).when(notifier).send(any(), any());

        AppointmentDtos.DispatchResult result = service.dispatchDue();

        assertEquals(1, result.failed());
        assertEquals("FAILED", reminder.getStatus());
        assertEquals(1, reminder.getRetryCount());
        assertNotNull(reminder.getNextRetryAt());
        assertTrue(reminder.getLastError().contains("短信网关超时"));
    }

    @Test
    void dispatchStopsAutoRetryAtMaxRetries() {
        AppointmentReminder reminder = reminder("FAILED", 2, 3);
        when(reminderRepository.findDue(any(), any())).thenReturn(List.of(reminder));
        doThrow(new RuntimeException("通道不可用")).when(notifier).send(any(), any());

        service.dispatchDue();

        assertEquals(3, reminder.getRetryCount());
        assertNull(reminder.getNextRetryAt());
    }

    @Test
    void dispatchCancelsReminderForTerminalAppointment() {
        AppointmentReminder reminder = reminder("PENDING", 0, 3);
        Appointment cancelled = appointment();
        cancelled.setStatus("CANCELLED");
        when(reminderRepository.findDue(any(), any())).thenReturn(List.of(reminder));
        when(appointmentRepository.findById(APPT_ID)).thenReturn(Optional.of(cancelled));

        AppointmentDtos.DispatchResult result = service.dispatchDue();

        assertEquals("CANCELLED", reminder.getStatus());
        assertEquals(0, result.sent());
        verify(notifier, never()).send(any(), any());
    }

    @Test
    void manualRetrySucceedsAfterAutoRetriesExhausted() {
        AppointmentReminder reminder = reminder("FAILED", 3, 3);
        when(reminderRepository.findById(REMINDER_ID)).thenReturn(Optional.of(reminder));

        AppointmentDtos.ReminderView view = service.retry(REMINDER_ID);

        assertEquals("SENT", view.status());
        assertEquals(3, reminder.getRetryCount());
        assertNull(reminder.getNextRetryAt());
    }

    @Test
    void retryRejectsAlreadySentReminder() {
        AppointmentReminder reminder = reminder("SENT", 0, 3);
        when(reminderRepository.findById(REMINDER_ID)).thenReturn(Optional.of(reminder));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.retry(REMINDER_ID));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void cancelPendingCancelsUnsentReminders() {
        AppointmentReminder pending = reminder("PENDING", 0, 3);
        when(reminderRepository.findByAppointmentIdAndStatusIn(APPT_ID, List.of("PENDING")))
                .thenReturn(List.of(pending));

        service.cancelPending(APPT_ID);

        assertEquals("CANCELLED", pending.getStatus());
    }

    private Appointment appointment() {
        Appointment appointment = new Appointment();
        appointment.setAppointmentNo("A100001");
        appointment.setCustomerId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        appointment.setTypeCode("OUTPATIENT");
        appointment.setSlotId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        appointment.setStatus("BOOKED");
        withId(appointment, APPT_ID);
        return appointment;
    }

    private AppointmentReminder reminder(String status, int retryCount, int maxRetries) {
        AppointmentReminder reminder = new AppointmentReminder();
        reminder.setAppointmentId(APPT_ID);
        reminder.setChannel("sms");
        reminder.setPlannedAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        reminder.setStatus(status);
        reminder.setRetryCount(retryCount);
        reminder.setMaxRetries(maxRetries);
        withId(reminder, REMINDER_ID);
        return reminder;
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
