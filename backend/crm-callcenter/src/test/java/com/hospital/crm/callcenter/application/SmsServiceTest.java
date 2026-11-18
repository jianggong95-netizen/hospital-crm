package com.hospital.crm.callcenter.application;

import com.hospital.crm.callcenter.adapter.SmsProvider;
import com.hospital.crm.callcenter.api.CallCenterDtos;
import com.hospital.crm.callcenter.domain.MessageOutbox;
import com.hospital.crm.callcenter.domain.MessageOutboxRepository;
import com.hospital.crm.callcenter.domain.SmsTemplate;
import com.hospital.crm.callcenter.domain.SmsTemplateRepository;
import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.customer.application.CustomerService;
import com.hospital.crm.customer.crypto.SensitiveDataHasher;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信模板与消息队列：变量提取、模板校验、biz_key 幂等、退避重试与人工重试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmsServiceTest {

    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final String RECIPIENT_HASH = "RECIPIENT_HASH_VALUE";

    @Mock
    private MessageOutboxRepository messageRepository;
    @Mock
    private SmsTemplateRepository templateRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private SensitiveDataHasher hasher;
    @Mock
    private SmsProvider smsProvider;
    @Mock
    private AuditService auditService;

    private SmsService service;

    @BeforeEach
    void setUp() {
        service = new SmsService(messageRepository, templateRepository, customerService,
                hasher, smsProvider, auditService, "simulated");
        when(hasher.hash("13800001111")).thenReturn(RECIPIENT_HASH);
        when(messageRepository.nextMsgNo()).thenReturn(910001L);
        when(messageRepository.save(any(MessageOutbox.class))).thenAnswer(invocation -> {
            MessageOutbox message = invocation.getArgument(0);
            if (message.getId() == null) {
                ReflectionTestUtils.setField(message, "id", MESSAGE_ID);
            }
            if (message.getCreatedAt() == null) {
                ReflectionTestUtils.setField(message, "createdAt", Instant.now());
            }
            return message;
        });
        when(templateRepository.save(any(SmsTemplate.class))).thenAnswer(invocation -> {
            SmsTemplate template = invocation.getArgument(0);
            if (template.getId() == null) {
                ReflectionTestUtils.setField(template, "id", TEMPLATE_ID);
            }
            if (template.getCreatedAt() == null) {
                ReflectionTestUtils.setField(template, "createdAt", Instant.now());
            }
            return template;
        });
    }

    // ---------- 模板 ----------

    @Test
    void templateCreateExtractsVariablesDeduplicatedInOrder() {
        when(templateRepository.existsByCode("T1")).thenReturn(false);

        CallCenterDtos.SmsTemplateView view = service.createTemplate(new CallCenterDtos.TemplateCreateRequest(
                "T1", "测试模板", "尊敬的{{name}}，验证码{{code}}，{{name}}请查收", null));

        ArgumentCaptor<SmsTemplate> captor = ArgumentCaptor.forClass(SmsTemplate.class);
        verify(templateRepository).save(captor.capture());
        assertThat(captor.getValue().getVariables()).containsExactly("name", "code");
        assertThat(view.variables()).containsExactly("name", "code");
    }

    @Test
    void templateCreateRejectsDuplicateCode() {
        when(templateRepository.existsByCode("T1")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(new CallCenterDtos.TemplateCreateRequest(
                "T1", "测试模板", "内容", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板编码已存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_CONFLICT);
    }

    // ---------- 入队 ----------

    @Test
    void enqueueRejectsDisabledTemplate() {
        when(templateRepository.findByCode("T1")).thenReturn(Optional.of(template("disabled")));

        assertThatThrownBy(() -> service.enqueue(new CallCenterDtos.MessageSendRequest(
                "sms", null, "T1", Map.of("name", "张三"), null, "13800001111", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板已停用")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void enqueueListsMissingVariables() {
        SmsTemplate twoVars = template("active");
        twoVars.setContent("{{name}}您好，验证码{{code}}");
        twoVars.setVariables(List.of("name", "code"));
        when(templateRepository.findByCode("T1")).thenReturn(Optional.of(twoVars));

        assertThatThrownBy(() -> service.enqueue(new CallCenterDtos.MessageSendRequest(
                "sms", null, "T1", Map.of("name", "张三"), null, "13800001111", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void enqueueRejectsBothOrNeitherTemplateAndContent() {
        assertThatThrownBy(() -> service.enqueue(new CallCenterDtos.MessageSendRequest(
                "sms", null, null, null, null, "13800001111", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    void enqueueIsIdempotentByBizKey() {
        MessageOutbox existing = message("MS910000", "SENT", 0, 3);
        when(messageRepository.findByBizKey("BIZ-1")).thenReturn(Optional.of(existing));

        CallCenterDtos.MessageView view = service.enqueue(new CallCenterDtos.MessageSendRequest(
                "sms", null, null, null, "内容", "13800001111", null, "BIZ-1"));

        assertThat(view.msgNo()).isEqualTo("MS910000");
        verify(messageRepository, never()).save(any(MessageOutbox.class));
    }

    @Test
    void enqueueRendersTemplateAndStoresMaskedOnly() {
        when(templateRepository.findByCode("T1")).thenReturn(Optional.of(template("active")));

        CallCenterDtos.MessageView view = service.enqueue(new CallCenterDtos.MessageSendRequest(
                "sms", "appt_remind", "T1", Map.of("name", "张三"), null, "138-0000-1111", null, null));

        ArgumentCaptor<MessageOutbox> captor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(messageRepository).save(captor.capture());
        MessageOutbox saved = captor.getValue();
        assertThat(saved.getRecipientHash()).isEqualTo(RECIPIENT_HASH);
        assertThat(saved.getRecipientMasked()).isEqualTo("138****1111");
        assertThat(saved.getContent()).isEqualTo("张三您好，请按时就诊");
        assertThat(saved.getMsgNo()).isEqualTo("MS910001");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getNextRetryAt()).isNotNull();
        assertThat(saved.getProvider()).isEqualTo("simulated");
        assertThat(view.recipientMasked()).isEqualTo("138****1111");
        verify(auditService).record(eq("MESSAGE_ENQUEUE"), anyString(), anyString(), any());
    }

    // ---------- 调度 ----------

    @Test
    void dispatchMarksSentOnSuccess() {
        MessageOutbox pending = message("MS910001", "PENDING", 0, 3);
        when(messageRepository.findDue(any(), any(Pageable.class))).thenReturn(List.of(pending));
        when(messageRepository.markSending(eq(MESSAGE_ID), any())).thenReturn(1);

        CallCenterDtos.DispatchResult result = service.dispatchDue();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(smsProvider).send(eq("138****1111"), anyString(), any(MessageOutbox.class));
        verify(messageRepository).markSent(eq(MESSAGE_ID), any());
    }

    @Test
    void dispatchFailureSchedulesBackoffRetry() {
        MessageOutbox pending = message("MS910001", "PENDING", 0, 3);
        when(messageRepository.findDue(any(), any(Pageable.class))).thenReturn(List.of(pending));
        when(messageRepository.markSending(eq(MESSAGE_ID), any())).thenReturn(1);
        doThrow(new RuntimeException("通道超时")).when(smsProvider).send(any(), any(), any());

        Instant before = Instant.now();
        CallCenterDtos.DispatchResult result = service.dispatchDue();

        assertThat(result.failed()).isEqualTo(1);
        ArgumentCaptor<Instant> nextRetry = ArgumentCaptor.forClass(Instant.class);
        verify(messageRepository).markFailed(eq(MESSAGE_ID), any(), eq("通道超时"), nextRetry.capture());
        // 第一次失败退避 300 秒
        assertThat(nextRetry.getValue()).isCloseTo(before.plusSeconds(300), within(10, ChronoUnit.SECONDS));
    }

    @Test
    void dispatchExhaustedRetriesStopsAutoRetry() {
        MessageOutbox lastAttempt = message("MS910001", "PENDING", 2, 3);
        when(messageRepository.findDue(any(), any(Pageable.class))).thenReturn(List.of(lastAttempt));
        when(messageRepository.markSending(eq(MESSAGE_ID), any())).thenReturn(1);
        doThrow(new RuntimeException("通道异常")).when(smsProvider).send(any(), any(), any());

        service.dispatchDue();

        ArgumentCaptor<Instant> nextRetry = ArgumentCaptor.forClass(Instant.class);
        verify(messageRepository).markFailed(eq(MESSAGE_ID), any(), anyString(), nextRetry.capture());
        assertThat(nextRetry.getValue()).isNull();
    }

    @Test
    void dispatchSkipsWhenClaimLostToConcurrentInstance() {
        MessageOutbox pending = message("MS910001", "PENDING", 0, 3);
        when(messageRepository.findDue(any(), any(Pageable.class))).thenReturn(List.of(pending));
        when(messageRepository.markSending(eq(MESSAGE_ID), any())).thenReturn(0);

        CallCenterDtos.DispatchResult result = service.dispatchDue();

        assertThat(result.sent()).isZero();
        assertThat(result.failed()).isZero();
        verify(smsProvider, never()).send(any(), any(), any());
    }

    // ---------- 人工重试 ----------

    @Test
    void manualRetryIgnoresMaxRetriesLimit() {
        MessageOutbox exhausted = message("MS910001", "FAILED", 5, 3);
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(exhausted));
        when(messageRepository.markSendingManual(eq(MESSAGE_ID), any())).thenReturn(1);

        CallCenterDtos.MessageView view = service.retry(MESSAGE_ID);

        assertThat(view.msgNo()).isEqualTo("MS910001");
        verify(messageRepository).markSendingManual(eq(MESSAGE_ID), any());
        verify(smsProvider).send(any(), any(), any());
        verify(messageRepository).markSent(eq(MESSAGE_ID), any());
        verify(auditService).record(eq("MESSAGE_RETRY"), anyString(), anyString(), any());
    }

    @Test
    void retryRejectsSentMessage() {
        MessageOutbox sent = message("MS910001", "SENT", 0, 3);
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> service.retry(MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅待发送或发送失败的消息可以重试")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    // ---------- 辅助 ----------

    private SmsTemplate template(String status) {
        SmsTemplate template = new SmsTemplate();
        ReflectionTestUtils.setField(template, "id", TEMPLATE_ID);
        template.setCode("T1");
        template.setName("预约提醒");
        template.setContent("{{name}}您好，请按时就诊");
        template.setVariables(List.of("name"));
        template.setStatus(status);
        ReflectionTestUtils.setField(template, "createdAt", Instant.now());
        ReflectionTestUtils.setField(template, "updatedAt", Instant.now());
        return template;
    }

    private MessageOutbox message(String msgNo, String status, int retryCount, int maxRetries) {
        MessageOutbox message = new MessageOutbox();
        ReflectionTestUtils.setField(message, "id", MESSAGE_ID);
        message.setMsgNo(msgNo);
        message.setChannel("sms");
        message.setScene("appt_remind");
        message.setRecipientHash(RECIPIENT_HASH);
        message.setRecipientMasked("138****1111");
        message.setContent("测试内容");
        message.setStatus(status);
        message.setRetryCount(retryCount);
        message.setMaxRetries(maxRetries);
        message.setNextRetryAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        ReflectionTestUtils.setField(message, "createdAt", Instant.now());
        return message;
    }
}
