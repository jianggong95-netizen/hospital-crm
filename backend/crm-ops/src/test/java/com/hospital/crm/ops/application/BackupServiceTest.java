package com.hospital.crm.ops.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.ops.api.OpsDtos;
import com.hospital.crm.ops.domain.OpsBackupRecord;
import com.hospital.crm.ops.domain.OpsBackupRecordRepository;
import com.hospital.crm.system.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 备份记录上报与恢复演练登记：编号唯一、时间区间校验、演练结果可追溯。
 * 验收「备份恢复演练通过」需能查到 restore_verified 与演练说明。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackupServiceTest {

    @Mock
    private OpsBackupRecordRepository backupRepository;
    @Mock
    private AuditService auditService;

    private BackupService service;

    @BeforeEach
    void setUp() {
        service = new BackupService(backupRepository, auditService, 24);
        when(backupRepository.save(any(OpsBackupRecord.class))).thenAnswer(invocation -> {
            OpsBackupRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
            }
            return record;
        });
    }

    @Test
    void reportRejectsDuplicateBackupCode() {
        when(backupRepository.existsByBackupCode("crm_full_20261118_0100")).thenReturn(true);

        OpsDtos.BackupReportRequest request = new OpsDtos.BackupReportRequest(
                "crm_full_20261118_0100", "FULL", "SUCCESS", 1024L, null, "crm",
                Instant.parse("2026-11-18T01:00:00Z"), Instant.parse("2026-11-18T01:01:00Z"), null);

        assertThatThrownBy(() -> service.report(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份编号已存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_CONFLICT);
    }

    @Test
    void reportRejectsReversedTimeRange() {
        OpsDtos.BackupReportRequest request = new OpsDtos.BackupReportRequest(
                "crm_full_20261118_0100", "FULL", "SUCCESS", 1024L, null, "crm",
                Instant.parse("2026-11-18T01:01:00Z"), Instant.parse("2026-11-18T01:00:00Z"), null);

        assertThatThrownBy(() -> service.report(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束时间不能早于开始时间")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void reportStoresNormalizedHashAndDuration() {
        String hash = "a".repeat(64);
        OpsDtos.BackupReportRequest request = new OpsDtos.BackupReportRequest(
                " crm_full_20261118_0100 ", "FULL", "SUCCESS", 2048L, hash.toUpperCase(), " crm ",
                Instant.parse("2026-11-18T01:00:00Z"), Instant.parse("2026-11-18T01:00:42Z"), null);

        OpsDtos.BackupView view = service.report(request);

        assertThat(view.backupCode()).isEqualTo("crm_full_20261118_0100");
        assertThat(view.fileHash()).isEqualTo(hash);
        assertThat(view.durationSeconds()).isEqualTo(42);
        assertThat(view.restoreVerified()).isFalse();
        verify(auditService).record(eq("OPS_BACKUP_REPORT"), anyString(), anyString(), any(Map.class));
    }

    @Test
    void verifyMarksRestoreDrillResult() {
        UUID backupId = UUID.randomUUID();
        OpsBackupRecord record = new OpsBackupRecord();
        record.setBackupCode("crm_full_20261118_0100");
        record.setStatus("SUCCESS");
        record.setBackupType("FULL");
        record.setStartedAt(Instant.now().minusSeconds(60));
        record.setFinishedAt(Instant.now());
        record.setSizeBytes(1024L);
        when(backupRepository.findById(backupId)).thenReturn(Optional.of(record));
        when(backupRepository.save(any(OpsBackupRecord.class))).thenAnswer(invocation -> {
            OpsBackupRecord saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", backupId);
            }
            return saved;
        });

        OpsDtos.BackupView view = service.verify(backupId,
                new OpsDtos.BackupVerifyRequest(true, "演练库行数一致，flyway V20"));

        assertThat(view.restoreVerified()).isTrue();
        assertThat(view.restoreVerifiedAt()).isNotNull();
        assertThat(view.restoreMessage()).contains("flyway V20");
        verify(auditService).record(eq("OPS_BACKUP_VERIFY"), anyString(), anyString(), any(Map.class));
    }

    @Test
    void verifyRejectsUnknownBackup() {
        UUID backupId = UUID.randomUUID();
        when(backupRepository.findById(backupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(backupId, new OpsDtos.BackupVerifyRequest(true, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份记录不存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void summaryReportsAgeOfLatestSuccessfulBackup() {
        OpsBackupRecord success = new OpsBackupRecord();
        success.setBackupCode("crm_full_20261118_0100");
        success.setStatus("SUCCESS");
        success.setBackupType("FULL");
        success.setStartedAt(Instant.now().minusSeconds(3 * 3600 + 60));
        success.setFinishedAt(Instant.now().minusSeconds(3 * 3600));
        success.setSizeBytes(1024L);
        when(backupRepository.findTop1ByOrderByStartedAtDesc()).thenReturn(Optional.of(success));
        when(backupRepository.findTop1ByStatusOrderByStartedAtDesc("SUCCESS")).thenReturn(Optional.of(success));
        when(backupRepository.findTop1ByRestoreVerifiedTrueOrderByRestoreVerifiedAtDesc())
                .thenReturn(Optional.empty());

        OpsDtos.BackupSummary summary = service.summary();

        assertThat(summary.lastBackupCode()).isEqualTo("crm_full_20261118_0100");
        assertThat(summary.ageHours()).isBetween(2.9, 3.1);
        assertThat(summary.restoreVerified()).isFalse();
    }

    @Test
    void summaryWithoutBackupReturnsEmptyState() {
        when(backupRepository.findTop1ByOrderByStartedAtDesc()).thenReturn(Optional.empty());
        when(backupRepository.findTop1ByStatusOrderByStartedAtDesc("SUCCESS")).thenReturn(Optional.empty());
        when(backupRepository.findTop1ByRestoreVerifiedTrueOrderByRestoreVerifiedAtDesc())
                .thenReturn(Optional.empty());

        OpsDtos.BackupSummary summary = service.summary();

        assertThat(summary.lastSuccessAt()).isNull();
        assertThat(summary.ageHours()).isNull();
        assertThat(summary.lastStatus()).isNull();
    }

    @Test
    void listNormalizesStatusFilter() {
        org.springframework.data.domain.Page<OpsBackupRecord> emptyPage =
                new org.springframework.data.domain.PageImpl<>(List.of());
        when(backupRepository.findByStatus(eq("FAILED"), any())).thenReturn(emptyPage);

        var response = service.list("failed", 1, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
    }
}
