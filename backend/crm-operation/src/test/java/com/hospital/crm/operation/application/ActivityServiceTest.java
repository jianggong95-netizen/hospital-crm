package com.hospital.crm.operation.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataCipher;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.operation.api.OperationDtos;
import com.hospital.crm.operation.domain.ActivityParticipant;
import com.hospital.crm.operation.domain.ActivityParticipantRepository;
import com.hospital.crm.operation.domain.LeadRepository;
import com.hospital.crm.operation.domain.OperationActivity;
import com.hospital.crm.operation.domain.OperationActivityRepository;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityServiceTest {

    private static final UUID ACTIVITY_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PARTICIPANT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private OperationActivityRepository activityRepository;
    @Mock
    private ActivityParticipantRepository participantRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SensitiveDataCipher cipher;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private AuditService auditService;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityRepository, participantRepository, leadRepository,
                customerService, cipher, hasher, auditService);
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(hasher.hash(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(participantRepository.save(any(ActivityParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsRegistrationBeforePublish() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity("DRAFT", 10)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(ACTIVITY_ID, participantRequest()));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void rejectsRegistrationWhenCapacityIsFull() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity("PUBLISHED", 2)));
        when(participantRepository.countByActivityIdAndStatusNot(ACTIVITY_ID, "CANCELLED")).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(ACTIVITY_ID, participantRequest()));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
        assertEquals("活动名额已满", ex.getMessage());
    }

    @Test
    void rejectsDuplicateRegistrationByPhone() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity("PUBLISHED", 10)));
        when(participantRepository.countByActivityIdAndStatusNot(ACTIVITY_ID, "CANCELLED")).thenReturn(0L);
        when(participantRepository.findFirstByActivityIdAndPhoneHash(ACTIVITY_ID, "hash:13800000001"))
                .thenReturn(Optional.of(new ActivityParticipant()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.register(ACTIVITY_ID, participantRequest()));

        assertEquals(ErrorCode.BUSINESS_CONFLICT, ex.getErrorCode());
    }

    @Test
    void registersParticipantWithMaskedPhone() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity("PUBLISHED", 10)));
        when(participantRepository.countByActivityIdAndStatusNot(ACTIVITY_ID, "CANCELLED")).thenReturn(0L);
        when(participantRepository.findFirstByActivityIdAndPhoneHash(ACTIVITY_ID, "hash:13800000001"))
                .thenReturn(Optional.empty());

        OperationDtos.ParticipantView view = service.register(ACTIVITY_ID, participantRequest());

        assertEquals("REGISTERED", view.status());
        assertEquals("138****0001", view.phoneMasked());
    }

    @Test
    void markingAttendedRecordsCheckInTime() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity("PUBLISHED", 10)));
        ActivityParticipant participant = participant("REGISTERED");
        when(participantRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(participant));

        OperationDtos.ParticipantView view = service.changeParticipantStatus(ACTIVITY_ID, PARTICIPANT_ID,
                new OperationDtos.ParticipantStatusRequest("ATTENDED", "现场签到"));

        assertEquals("ATTENDED", view.status());
        assertNotNull(participant.getCheckedInAt());
    }

    @Test
    void rejectsIllegalParticipantTransition() {
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity("PUBLISHED", 10)));
        when(participantRepository.findById(PARTICIPANT_ID))
                .thenReturn(Optional.of(participant("ATTENDED")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changeParticipantStatus(ACTIVITY_ID, PARTICIPANT_ID,
                        new OperationDtos.ParticipantStatusRequest("CANCELLED", null)));

        assertEquals(ErrorCode.BUSINESS_RULE_FAILED, ex.getErrorCode());
    }

    private OperationActivity activity(String status, int capacity) {
        OperationActivity activity = new OperationActivity();
        activity.setActivityNo("A100001");
        activity.setActivityName("健康筛查活动");
        activity.setActivityType("screening");
        activity.setStartAt(Instant.now().plusSeconds(3600));
        activity.setEndAt(Instant.now().plusSeconds(7200));
        activity.setCapacity(capacity);
        activity.setStatus(status);
        setActivityId(activity);
        return activity;
    }

    private void setActivityId(OperationActivity activity) {
        try {
            var field = OperationActivity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(activity, ACTIVITY_ID);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ActivityParticipant participant(String status) {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setActivityId(ACTIVITY_ID);
        participant.setParticipantName("参与人");
        participant.setPhoneMasked("138****0001");
        participant.setStatus(status);
        try {
            var field = ActivityParticipant.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(participant, PARTICIPANT_ID);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return participant;
    }

    private OperationDtos.ParticipantCreateRequest participantRequest() {
        return new OperationDtos.ParticipantCreateRequest(
                null, null, "参与人", "13800000001", null);
    }
}
