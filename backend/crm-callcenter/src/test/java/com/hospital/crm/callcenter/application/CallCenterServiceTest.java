package com.hospital.crm.callcenter.application;

import com.hospital.crm.callcenter.adapter.CallCenterAdapter;
import com.hospital.crm.callcenter.api.CallCenterDtos;
import com.hospital.crm.callcenter.domain.CallRecord;
import com.hospital.crm.callcenter.domain.CallRecordRepository;
import com.hospital.crm.callcenter.domain.CallRecording;
import com.hospital.crm.callcenter.domain.CallRecordingRepository;
import com.hospital.crm.chat.domain.ChatSessionRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.complaint.domain.ComplaintRepository;
import com.hospital.crm.customer.api.CustomerDtos;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
 * 呼叫中心：来电弹屏匹配、厂商幂等入库、号码脱敏落库、关联校验与录音访问留痕。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallCenterServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID CALL_ID = UUID.randomUUID();
    private static final UUID RECORDING_ID = UUID.randomUUID();
    private static final String PHONE_RAW = "138-0000-1111";
    private static final String PHONE_NORMALIZED = "13800001111";
    private static final String CALLER_HASH = "CALLER_HASH_VALUE";

    @Mock
    private CallRecordRepository callRecordRepository;
    @Mock
    private CallRecordingRepository recordingRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private CallCenterAdapter callCenterAdapter;
    @Mock
    private AuditService auditService;

    private CallCenterService service;

    @BeforeEach
    void setUp() {
        service = new CallCenterService(callRecordRepository, recordingRepository, customerService,
                userRepository, complaintRepository, chatSessionRepository, hasher,
                callCenterAdapter, auditService);
        when(hasher.hash(PHONE_NORMALIZED)).thenReturn(CALLER_HASH);
        when(callRecordRepository.nextCallNo()).thenReturn(900001L);
        when(callRecordRepository.save(any(CallRecord.class))).thenAnswer(invocation -> {
            CallRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                ReflectionTestUtils.setField(record, "id", CALL_ID);
            }
            if (record.getCreatedAt() == null) {
                ReflectionTestUtils.setField(record, "createdAt", Instant.now());
            }
            return record;
        });
        when(recordingRepository.save(any(CallRecording.class))).thenAnswer(invocation -> {
            CallRecording recording = invocation.getArgument(0);
            if (recording.getId() == null) {
                ReflectionTestUtils.setField(recording, "id", RECORDING_ID);
            }
            if (recording.getCreatedAt() == null) {
                ReflectionTestUtils.setField(recording, "createdAt", Instant.now());
            }
            return recording;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void popupMatchesCustomerAndCountsHistory() {
        CustomerDtos.CustomerBrief brief = new CustomerDtos.CustomerBrief(
                CUSTOMER_ID, "C1001", "张三", "138****1111", "active");
        when(customerService.matchByPhone(PHONE_NORMALIZED)).thenReturn(Optional.of(brief));
        when(customerService.detail(CUSTOMER_ID)).thenReturn(new CustomerDtos.CustomerDetail(
                CUSTOMER_ID, "C1001", "张三", "female", java.time.LocalDate.of(1980, 5, 1),
                "138****1111", null, null, null, false, "wechat", "active", null,
                List.of(), Instant.now(), Instant.now()));
        when(callRecordRepository.countByCallerHash(CALLER_HASH)).thenReturn(3L);
        CallRecord last = record("CC900000", null);
        when(callRecordRepository.findFirstByCallerHashOrderByStartedAtDesc(CALLER_HASH))
                .thenReturn(Optional.of(last));

        CallCenterDtos.PopupView view = service.incomingPopup(PHONE_RAW);

        assertThat(view.matched()).isTrue();
        assertThat(view.phoneMasked()).isEqualTo("138****1111");
        assertThat(view.customer().name()).isEqualTo("张三");
        assertThat(view.customer().gender()).isEqualTo("female");
        assertThat(view.callHistoryCount()).isEqualTo(3L);
        assertThat(view.lastCallAt()).isNotNull();

        ArgumentCaptor<Map<String, Object>> audit = auditCaptor();
        verify(auditService).record(eq("CALL_POPUP"), anyString(), anyString(), audit.capture());
        assertThat(audit.getValue()).containsEntry("matched", true)
                .containsEntry("phoneMasked", "138****1111")
                .doesNotContainValue(PHONE_NORMALIZED);
    }

    @Test
    void popupWithoutMatchStillReturnsMaskedNumber() {
        when(customerService.matchByPhone(PHONE_NORMALIZED)).thenReturn(Optional.empty());
        when(callRecordRepository.countByCallerHash(CALLER_HASH)).thenReturn(0L);
        when(callRecordRepository.findFirstByCallerHashOrderByStartedAtDesc(CALLER_HASH))
                .thenReturn(Optional.empty());

        CallCenterDtos.PopupView view = service.incomingPopup(PHONE_RAW);

        assertThat(view.matched()).isFalse();
        assertThat(view.customer()).isNull();
        assertThat(view.callHistoryCount()).isZero();
    }

    @Test
    void createCallIsIdempotentByVendorCallId() {
        CallRecord existing = record("CC900009", "V-100");
        when(callRecordRepository.findByVendorCallId("V-100")).thenReturn(Optional.of(existing));

        CallCenterDtos.CallRecordDetailView view = service.createCall(
                new CallCenterDtos.CreateCallRequest("V-100", "inbound", PHONE_RAW, null,
                        null, null, null, null, null, Instant.now(), null, null,
                        "connected", null, null));

        assertThat(view.call().callNo()).isEqualTo("CC900009");
        verify(callRecordRepository, never()).save(any(CallRecord.class));
    }

    @Test
    void createCallStoresHashAndMaskedOnly() {
        when(hasher.hash("13900002222")).thenReturn("CALLEE_HASH_VALUE");
        Instant started = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant ended = started.plusSeconds(60);

        CallCenterDtos.CallRecordDetailView view = service.createCall(
                new CallCenterDtos.CreateCallRequest(null, "outbound", PHONE_RAW, "139-0000-2222",
                        null, null, null, "普通队列", null, started, ended, null,
                        "connected", null, null));

        ArgumentCaptor<CallRecord> captor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordRepository).save(captor.capture());
        CallRecord saved = captor.getValue();
        assertThat(saved.getCallerHash()).isEqualTo(CALLER_HASH);
        assertThat(saved.getCallerMasked()).isEqualTo("138****1111");
        assertThat(saved.getCalleeHash()).isEqualTo("CALLEE_HASH_VALUE");
        assertThat(saved.getCallNo()).isEqualTo("CC900001");
        assertThat(saved.getSource()).isEqualTo("manual");
        assertThat(saved.getDurationSeconds()).isEqualTo(60);

        // 视图只输出脱敏号码，不含明文
        assertThat(view.call().callerMasked()).isEqualTo("138****1111");
        assertThat(view.call().calleeMasked()).isEqualTo("139****2222");
        assertThat(view.toString()).doesNotContain(PHONE_NORMALIZED);
        verify(auditService).record(eq("CALL_RECORD_CREATE"), anyString(), anyString(), any());
    }

    @Test
    void createCallWithCustomerLinksCustomerNo() {
        when(customerService.brief(CUSTOMER_ID)).thenReturn(new CustomerDtos.CustomerBrief(
                CUSTOMER_ID, "C1001", "张三", "138****1111", "active"));
        when(customerService.briefs(any())).thenReturn(Map.of(CUSTOMER_ID,
                new CustomerDtos.CustomerBrief(CUSTOMER_ID, "C1001", "张三", "138****1111", "active")));

        CallCenterDtos.CallRecordDetailView view = service.createCall(
                new CallCenterDtos.CreateCallRequest(null, "inbound", PHONE_RAW, null,
                        CUSTOMER_ID, null, null, null, null, Instant.now(), null, null,
                        "connected", null, null));

        assertThat(view.call().linkType()).isEqualTo("customer");
        assertThat(view.call().linkNo()).isEqualTo("C1001");
        assertThat(view.call().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(view.call().customerName()).isEqualTo("张三");
    }

    @Test
    void linkRejectsMissingComplaintTicket() {
        when(callRecordRepository.findById(CALL_ID)).thenReturn(Optional.of(record("CC900001", null)));
        UUID complaintId = UUID.randomUUID();
        when(complaintRepository.findById(complaintId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(CALL_ID,
                new CallCenterDtos.LinkRequest("complaint", complaintId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投诉工单不存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void recordingAccessRequiresRecording() {
        when(callRecordRepository.findById(CALL_ID)).thenReturn(Optional.of(record("CC900001", null)));

        assertThatThrownBy(() -> service.recordingAccess(CALL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该通话没有录音记录")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void recordingAccessResolvesUrlAndAudits() {
        CallRecord withRecording = record("CC900001", null);
        withRecording.setRecordingId(RECORDING_ID);
        when(callRecordRepository.findById(CALL_ID)).thenReturn(Optional.of(withRecording));
        CallRecording recording = new CallRecording();
        ReflectionTestUtils.setField(recording, "id", RECORDING_ID);
        String recordingKey = "rec-2026-11-18.wav";
        recording.setRecordingKey(recordingKey);
        recording.setStorageType("vendor");
        when(recordingRepository.findById(RECORDING_ID)).thenReturn(Optional.of(recording));
        when(callCenterAdapter.resolveRecordingUrl(recordingKey))
                .thenReturn("https://vendor.example/rec/signed");

        CallCenterDtos.RecordingAccessView view = service.recordingAccess(CALL_ID);

        assertThat(view.accessUrl()).isEqualTo("https://vendor.example/rec/signed");
        assertThat(view.callNo()).isEqualTo("CC900001");
        verify(recordingRepository).incrementAccess(RECORDING_ID);
        ArgumentCaptor<Map<String, Object>> audit = auditCaptor();
        verify(auditService).record(eq("CALL_RECORDING_ACCESS"), anyString(), anyString(), audit.capture());
        assertThat(audit.getValue()).containsEntry("callNo", "CC900001");
    }

    @Test
    void recordingAccessFailsWhenAdapterUpstreamError() {
        CallRecord withRecording = record("CC900001", null);
        withRecording.setRecordingId(RECORDING_ID);
        when(callRecordRepository.findById(CALL_ID)).thenReturn(Optional.of(withRecording));
        CallRecording recording = new CallRecording();
        ReflectionTestUtils.setField(recording, "id", RECORDING_ID);
        recording.setRecordingKey("rec-2026-11-18.wav");
        when(recordingRepository.findById(RECORDING_ID)).thenReturn(Optional.of(recording));
        when(callCenterAdapter.resolveRecordingUrl(anyString())).thenThrow(
                new BusinessException(ErrorCode.UPSTREAM_ERROR, "呼叫中心尚未接入，无法获取录音"));

        assertThatThrownBy(() -> service.recordingAccess(CALL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("呼叫中心尚未接入");
        verify(recordingRepository, never()).incrementAccess(any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> auditCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private CallRecord record(String callNo, String vendorCallId) {
        CallRecord record = new CallRecord();
        ReflectionTestUtils.setField(record, "id", CALL_ID);
        record.setCallNo(callNo);
        record.setVendorCallId(vendorCallId);
        record.setDirection("inbound");
        record.setStatus("connected");
        record.setCallerHash(CALLER_HASH);
        record.setCallerMasked("138****1111");
        record.setLinkType("none");
        record.setStartedAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        record.setSource("manual");
        ReflectionTestUtils.setField(record, "createdAt", Instant.now());
        return record;
    }
}
