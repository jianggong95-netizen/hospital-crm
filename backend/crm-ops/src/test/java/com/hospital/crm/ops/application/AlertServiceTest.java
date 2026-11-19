package com.hospital.crm.ops.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.ops.api.OpsDtos;
import com.hospital.crm.ops.domain.OpsAlert;
import com.hospital.crm.ops.domain.OpsAlertRepository;
import com.hospital.crm.ops.domain.OpsBackupRecord;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警规则评估：内置规则建单、条件恢复自动关闭、已恢复告警不可确认、
 * 外部告警按 dedup_key 去重。验收「关键接口告警有效」。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertServiceTest {

    @Mock
    private OpsAlertRepository alertRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Mock
    private JobStatusService jobStatusService;
    @Mock
    private BackupService backupService;
    @Mock
    private ApiMetricsRegistry metricsRegistry;
    @Mock
    private AuditService auditService;

    private AlertService service;

    @BeforeEach
    void setUp() {
        service = new AlertService(alertRepository, jdbcTemplate, namedJdbcTemplate,
                jobStatusService, backupService, metricsRegistry, auditService,
                true, 300, 20, 0.05, 2000, 20, 24);
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(jobStatusService.list()).thenReturn(List.of());
        when(jobStatusService.failureThreshold()).thenReturn(3);
        when(metricsRegistry.summarize(anyInt()))
                .thenReturn(new ApiMetricsRegistry.WindowSummary(0, 0, 0, 0, 0));
        when(alertRepository.findFirstByDedupKeyAndStatusIn(anyString(), anyCollection()))
                .thenReturn(Optional.empty());
        when(alertRepository.saveAndFlush(any(OpsAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(alertRepository.save(any(OpsAlert.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void evaluateRaisesBackupStaleAndFailedWhenNoSuccessfulBackup() {
        OpsBackupRecord failed = backup("FAILED", Instant.now().minusSeconds(60));
        when(backupService.latest()).thenReturn(Optional.of(failed));
        when(backupService.latestSuccess()).thenReturn(Optional.empty());

        service.evaluate();

        ArgumentCaptor<OpsAlert> captor = ArgumentCaptor.forClass(OpsAlert.class);
        verify(alertRepository, atLeast(2)).saveAndFlush(captor.capture());
        List<String> codes = captor.getAllValues().stream().map(OpsAlert::getAlertCode).toList();
        assertThat(codes).contains("BACKUP_STALE", "BACKUP_FAILED");
        assertThat(captor.getAllValues()).allSatisfy(alert -> {
            assertThat(alert.getStatus()).isEqualTo("OPEN");
            assertThat(alert.getDedupKey()).isNotBlank();
            assertThat(alert.getFirstSeenAt()).isNotNull();
        });
    }

    @Test
    void evaluateResolvesOpenAlertWhenConditionRecovered() {
        OpsBackupRecord success = backup("SUCCESS", Instant.now().minusSeconds(300));
        when(backupService.latest()).thenReturn(Optional.of(success));
        when(backupService.latestSuccess()).thenReturn(Optional.of(success));

        OpsAlert existing = new OpsAlert();
        existing.setAlertCode("BACKUP_STALE");
        existing.setStatus("OPEN");
        existing.setDedupKey("BACKUP_STALE");
        when(alertRepository.findFirstByDedupKeyAndStatusIn(eq("BACKUP_STALE"), anyCollection()))
                .thenReturn(Optional.of(existing));

        service.evaluate();

        assertThat(existing.getStatus()).isEqualTo("RESOLVED");
        assertThat(existing.getResolvedAt()).isNotNull();
    }

    @Test
    void ackMarksAlertAsAcknowledged() {
        UUID alertId = UUID.randomUUID();
        OpsAlert alert = new OpsAlert();
        alert.setAlertCode("API_ERROR_RATE_HIGH");
        alert.setSeverity("HIGH");
        alert.setStatus("OPEN");
        alert.setDedupKey("API_ERROR_RATE_HIGH");
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        OpsDtos.AlertView view = service.ack(alertId);

        assertThat(view.status()).isEqualTo("ACKED");
        assertThat(view.ackedAt()).isNotNull();
        verify(auditService).record(eq("OPS_ALERT_ACK"), anyString(), anyString(), any());
    }

    @Test
    void ackRejectsResolvedAlert() {
        UUID alertId = UUID.randomUUID();
        OpsAlert alert = new OpsAlert();
        alert.setStatus("RESOLVED");
        alert.setDedupKey("X");
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> service.ack(alertId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("告警已恢复")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_FAILED);
    }

    @Test
    void raiseExternalUsesAlertCodeAsDefaultDedupKey() {
        when(alertRepository.findFirstByDedupKeyAndStatusIn(eq("LOG_PATTERN"), anyCollection()))
                .thenAnswer(invocation -> {
                    OpsAlert alert = new OpsAlert();
                    alert.setAlertCode("LOG_PATTERN");
                    alert.setSeverity("HIGH");
                    alert.setStatus("OPEN");
                    alert.setDedupKey("LOG_PATTERN");
                    alert.setSource("LOG");
                    alert.setFirstSeenAt(Instant.now());
                    alert.setLastSeenAt(Instant.now());
                    org.springframework.test.util.ReflectionTestUtils.setField(alert, "id", UUID.randomUUID());
                    return Optional.of(alert);
                });

        OpsDtos.AlertView view = service.raiseExternal(new OpsDtos.RaiseAlertRequest(
                "LOG_PATTERN", "HIGH", "日志出现 OutOfMemoryError", "detail", null, "LOG"));

        assertThat(view.dedupKey()).isEqualTo("LOG_PATTERN");
        assertThat(view.status()).isEqualTo("OPEN");
        verify(auditService).record(eq("OPS_ALERT_RAISE"), anyString(), anyString(), any());
    }

    @Test
    void rulesExposeEightBuiltInRules() {
        List<OpsDtos.AlertRuleView> rules = service.rules();

        assertThat(rules).hasSize(8);
        assertThat(rules).extracting(OpsDtos.AlertRuleView::alertCode)
                .contains("DB_UNAVAILABLE", "JOB_STALE", "JOB_FAILED", "BACKUP_STALE",
                        "BACKUP_FAILED", "API_ERROR_RATE_HIGH", "API_LATENCY_HIGH", "AUTH_FAILURE_BURST");
        assertThat(rules).allSatisfy(rule -> assertThat(rule.enabled()).isTrue());
    }

    private OpsBackupRecord backup(String status, Instant finishedAt) {
        OpsBackupRecord record = new OpsBackupRecord();
        record.setBackupCode("crm_full_test");
        record.setBackupType("FULL");
        record.setStatus(status);
        record.setStartedAt(finishedAt.minusSeconds(30));
        record.setFinishedAt(finishedAt);
        record.setSizeBytes(1024L);
        return record;
    }
}
