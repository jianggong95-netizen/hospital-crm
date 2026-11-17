package com.hospital.crm.appointment.application;

import com.hospital.crm.appointment.api.AppointmentDtos;
import com.hospital.crm.appointment.domain.AppointmentType;
import com.hospital.crm.appointment.domain.AppointmentTypeRepository;
import com.hospital.crm.appointment.domain.ServiceSlot;
import com.hospital.crm.appointment.domain.ServiceSlotRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务时段规则测试：唯一时段、日期与容量校验、存量预约保护。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceSlotServiceTest {

    private static final UUID SLOT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private ServiceSlotRepository slotRepository;
    @Mock
    private AppointmentTypeRepository typeRepository;
    @Mock
    private AuditService auditService;

    private ServiceSlotService service;

    @BeforeEach
    void setUp() {
        service = new ServiceSlotService(slotRepository, typeRepository, auditService);
        when(typeRepository.findByTypeCode("OUTPATIENT")).thenReturn(Optional.of(activeType()));
        when(typeRepository.findAllByOrderByTypeCodeAsc()).thenReturn(List.of(activeType()));
        when(slotRepository.save(any(ServiceSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRejectsDuplicateSlotDateTime() {
        when(slotRepository.existsByTypeCodeAndSlotDateAndStartTime("OUTPATIENT",
                LocalDate.now().plusDays(1), LocalTime.of(9, 0))).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new AppointmentDtos.SlotCreateRequest("OUTPATIENT", LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(9, 30), 5, null)));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
        verify(slotRepository, never()).save(any(ServiceSlot.class));
    }

    @Test
    void createRejectsPastDate() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new AppointmentDtos.SlotCreateRequest("OUTPATIENT", LocalDate.now().minusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(9, 30), 5, null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void updateRejectsTimeChangeWhenBooked() {
        ServiceSlot slot = new ServiceSlot();
        slot.setTypeCode("OUTPATIENT");
        slot.setSlotDate(LocalDate.now().plusDays(2));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setCapacity(5);
        slot.setBookedCount(2);
        slot.setStatus("OPEN");
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(SLOT_ID,
                new AppointmentDtos.SlotUpdateRequest(LocalDate.now().plusDays(2),
                        LocalTime.of(10, 0), LocalTime.of(10, 30), 5, null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void updateRejectsCapacityBelowBooked() {
        ServiceSlot slot = new ServiceSlot();
        slot.setTypeCode("OUTPATIENT");
        slot.setSlotDate(LocalDate.now().plusDays(2));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setCapacity(5);
        slot.setBookedCount(3);
        slot.setStatus("OPEN");
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(SLOT_ID,
                new AppointmentDtos.SlotUpdateRequest(LocalDate.now().plusDays(2),
                        LocalTime.of(9, 0), LocalTime.of(9, 30), 2, null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void changeStatusPersistsAndAudits() {
        ServiceSlot slot = new ServiceSlot();
        withId(slot, SLOT_ID);
        slot.setTypeCode("OUTPATIENT");
        slot.setSlotDate(LocalDate.now().plusDays(2));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setCapacity(5);
        slot.setStatus("OPEN");
        when(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));

        AppointmentDtos.SlotView view = service.changeStatus(SLOT_ID, "CLOSED");

        assertEquals("CLOSED", view.status());
        verify(auditService).record(anyString(), anyString(), anyString(), any());
    }

    private static <T> T withId(T entity, UUID id) {
        try {
            java.lang.reflect.Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return entity;
    }

    private AppointmentType activeType() {
        AppointmentType type = new AppointmentType();
        type.setTypeCode("OUTPATIENT");
        type.setTypeName("门诊预约");
        type.setDurationMinutes(30);
        type.setCancelCutoffHours(2);
        type.setStatus("active");
        return type;
    }
}
