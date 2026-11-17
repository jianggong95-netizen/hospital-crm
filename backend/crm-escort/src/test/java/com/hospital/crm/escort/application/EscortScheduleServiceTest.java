package com.hospital.crm.escort.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.escort.api.EscortDtos;
import com.hospital.crm.escort.domain.Escort;
import com.hospital.crm.escort.domain.EscortRepository;
import com.hospital.crm.escort.domain.EscortSchedule;
import com.hospital.crm.escort.domain.EscortScheduleRepository;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 陪诊师排班规则测试：重叠校验、更新时排除自身、过去日期与删除限制。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscortScheduleServiceTest {

    private static final UUID ESCORT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SCHEDULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private EscortScheduleRepository scheduleRepository;
    @Mock
    private EscortRepository escortRepository;
    @Mock
    private AuditService auditService;

    private EscortScheduleService service;
    private Escort storedEscort;

    @BeforeEach
    void setUp() {
        service = new EscortScheduleService(scheduleRepository, escortRepository, auditService);
        storedEscort = escort();
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.of(storedEscort));
        when(scheduleRepository.save(any(EscortSchedule.class))).thenAnswer(invocation -> {
            EscortSchedule saved = invocation.getArgument(0);
            return saved.getId() == null ? withId(saved, SCHEDULE_ID) : saved;
        });
    }

    @Test
    void createRejectsOverlappingSchedule() {
        when(scheduleRepository.findByEscortIdAndWorkDate(ESCORT_ID, LocalDate.now()))
                .thenReturn(List.of(existingSchedule(ESCORT_ID, LocalTime.of(9, 0), LocalTime.of(12, 0))));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new EscortDtos.ScheduleCreateRequest(ESCORT_ID, LocalDate.now(),
                        LocalTime.of(11, 0), LocalTime.of(13, 0))));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
    }

    @Test
    void createAllowsAdjacentShift() {
        when(scheduleRepository.findByEscortIdAndWorkDate(ESCORT_ID, LocalDate.now()))
                .thenReturn(List.of(existingSchedule(ESCORT_ID, LocalTime.of(9, 0), LocalTime.of(12, 0))));

        EscortDtos.ScheduleView view = service.create(new EscortDtos.ScheduleCreateRequest(
                ESCORT_ID, LocalDate.now(), LocalTime.of(12, 0), LocalTime.of(14, 0)));

        assertEquals(SCHEDULE_ID, view.id());
        verify(auditService).record(eq("SCHEDULE_CREATE"), eq("ESCORT_SCHEDULE"), anyString(), any());
    }

    @Test
    void createRejectsPastWorkDate() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new EscortDtos.ScheduleCreateRequest(ESCORT_ID, LocalDate.now().minusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(11, 0))));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void createUnknownEscortReturnsNotFound() {
        when(escortRepository.findById(ESCORT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(
                new EscortDtos.ScheduleCreateRequest(ESCORT_ID, LocalDate.now(),
                        LocalTime.of(9, 0), LocalTime.of(11, 0))));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void updateExcludesSelfWhenCheckingOverlap() {
        EscortSchedule existing = existingSchedule(ESCORT_ID, LocalTime.of(9, 0), LocalTime.of(12, 0));
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(existing));
        when(scheduleRepository.findByEscortIdAndWorkDate(ESCORT_ID, LocalDate.now()))
                .thenReturn(List.of(existing));

        EscortDtos.ScheduleView view = service.update(SCHEDULE_ID,
                new EscortDtos.ScheduleUpdateRequest(LocalDate.now(),
                        LocalTime.of(9, 0), LocalTime.of(11, 0)));

        assertEquals(SCHEDULE_ID, view.id());
        verify(auditService).record(eq("SCHEDULE_UPDATE"), eq("ESCORT_SCHEDULE"), anyString(), any());
    }

    @Test
    void updateRejectsOverlapWithOther() {
        EscortSchedule existing = existingSchedule(ESCORT_ID, LocalTime.of(9, 0), LocalTime.of(12, 0));
        EscortSchedule other = withId(existingSchedule(ESCORT_ID, LocalTime.of(10, 0), LocalTime.of(12, 0)), OTHER_ID);
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(existing));
        when(scheduleRepository.findByEscortIdAndWorkDate(ESCORT_ID, LocalDate.now()))
                .thenReturn(List.of(existing, other));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(SCHEDULE_ID,
                new EscortDtos.ScheduleUpdateRequest(LocalDate.now(),
                        LocalTime.of(9, 0), LocalTime.of(11, 0))));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
    }

    @Test
    void deleteRejectsTodayOrPastSchedule() {
        when(scheduleRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(existingSchedule(ESCORT_ID, LocalTime.of(9, 0), LocalTime.of(11, 0))));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(SCHEDULE_ID));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    @Test
    void deleteFutureScheduleSucceedsAndAudits() {
        EscortSchedule future = existingSchedule(ESCORT_ID, LocalTime.of(9, 0), LocalTime.of(11, 0));
        future.setWorkDate(LocalDate.now().plusDays(1));
        when(scheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(future));

        service.delete(SCHEDULE_ID);

        verify(scheduleRepository).deleteById(SCHEDULE_ID);
        verify(auditService).record(eq("SCHEDULE_DELETE"), eq("ESCORT_SCHEDULE"), anyString(), any());
    }

    private Escort escort() {
        Escort escort = new Escort();
        escort.setEscortNo("ES100000");
        escort.setName("张护士");
        return withId(escort, ESCORT_ID);
    }

    private EscortSchedule existingSchedule(UUID escortId, LocalTime start, LocalTime end) {
        EscortSchedule schedule = new EscortSchedule();
        schedule.setEscortId(escortId);
        schedule.setWorkDate(LocalDate.now());
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        return withId(schedule, SCHEDULE_ID);
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
