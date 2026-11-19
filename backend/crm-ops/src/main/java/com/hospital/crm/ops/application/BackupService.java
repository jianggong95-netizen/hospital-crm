package com.hospital.crm.ops.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.ops.api.OpsDtos;
import com.hospital.crm.ops.domain.OpsBackupRecord;
import com.hospital.crm.ops.domain.OpsBackupRecordRepository;
import com.hospital.crm.system.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 备份记录服务：接收备份脚本上报（编号唯一、SHA-256 校验、时间区间校验），
 * 单独登记恢复演练结果；所有写操作记录审计，便于审计「备份是否真的演练过」。
 */
@Service
public class BackupService {

    private static final String RESOURCE = "OPS_BACKUP";
    private static final String SUCCESS = "SUCCESS";

    private final OpsBackupRecordRepository backupRepository;
    private final AuditService auditService;
    private final int defaultRetentionCheckHours;

    public BackupService(OpsBackupRecordRepository backupRepository,
                         AuditService auditService,
                         @Value("${crm.ops.alerts.backup-max-age-hours:24}") int backupMaxAgeHours) {
        this.backupRepository = backupRepository;
        this.auditService = auditService;
        this.defaultRetentionCheckHours = Math.max(1, backupMaxAgeHours);
    }

    @Transactional(readOnly = true)
    public PageResponse<OpsDtos.BackupView> list(String status, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(OpsDtos.page(page) - 1, OpsDtos.pageSize(pageSize),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<OpsBackupRecord> result = (status == null || status.isBlank())
                ? backupRepository.findAll(pageRequest)
                : backupRepository.findByStatus(status.trim().toUpperCase(), pageRequest);
        return PageResponse.of(result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(), OpsDtos.page(page), OpsDtos.pageSize(pageSize));
    }

    @Transactional
    public OpsDtos.BackupView report(OpsDtos.BackupReportRequest request) {
        String backupCode = request.backupCode().trim();
        if (backupRepository.existsByBackupCode(backupCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "备份编号已存在：" + backupCode);
        }
        if (request.finishedAt().isBefore(request.startedAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备份结束时间不能早于开始时间");
        }
        OpsBackupRecord record = new OpsBackupRecord();
        record.setBackupCode(backupCode);
        record.setBackupType(request.backupType());
        record.setStatus(request.status());
        record.setSizeBytes(request.sizeBytes());
        record.setFileHash(blankToNull(request.fileHash() == null ? null : request.fileHash().toLowerCase()));
        record.setTarget(blankToNull(request.target()));
        record.setStartedAt(request.startedAt());
        record.setFinishedAt(request.finishedAt());
        record.setDurationSeconds(Math.max(0,
                ChronoUnit.SECONDS.between(request.startedAt(), request.finishedAt())));
        record.setMessage(blankToNull(request.message()));
        record.setReportedBy("API");
        OpsBackupRecord saved = backupRepository.save(record);

        auditService.record("OPS_BACKUP_REPORT", RESOURCE, saved.getId().toString(),
                Map.of("backupCode", saved.getBackupCode(),
                        "backupType", saved.getBackupType(),
                        "status", saved.getStatus(),
                        "sizeBytes", saved.getSizeBytes()));
        return toView(saved);
    }

    @Transactional
    public OpsDtos.BackupView verify(UUID backupId, OpsDtos.BackupVerifyRequest request) {
        OpsBackupRecord record = backupRepository.findById(backupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "备份记录不存在"));
        record.setRestoreVerified(Boolean.TRUE.equals(request.restoreVerified()));
        record.setRestoreVerifiedAt(record.isRestoreVerified() ? Instant.now() : null);
        record.setRestoreMessage(blankToNull(request.restoreMessage()));
        OpsBackupRecord saved = backupRepository.save(record);

        auditService.record("OPS_BACKUP_VERIFY", RESOURCE, backupId.toString(),
                Map.of("backupCode", saved.getBackupCode(),
                        "restoreVerified", saved.isRestoreVerified()));
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public OpsDtos.BackupSummary summary() {
        Optional<OpsBackupRecord> latest = backupRepository.findTop1ByOrderByStartedAtDesc();
        Optional<OpsBackupRecord> latestSuccess = backupRepository.findTop1ByStatusOrderByStartedAtDesc(SUCCESS);
        Optional<OpsBackupRecord> latestVerified =
                backupRepository.findTop1ByRestoreVerifiedTrueOrderByRestoreVerifiedAtDesc();

        Double ageHours = latestSuccess
                .map(record -> Duration.between(record.getFinishedAt(), Instant.now()).toMinutes() / 60.0)
                .orElse(null);
        return new OpsDtos.BackupSummary(
                latestSuccess.map(OpsBackupRecord::getFinishedAt).orElse(null),
                latestSuccess.map(OpsBackupRecord::getBackupCode).orElse(null),
                latest.map(OpsBackupRecord::getStatus).orElse(null),
                ageHours == null ? null : Math.round(ageHours * 100) / 100.0,
                latestVerified.isPresent(),
                latestVerified.map(OpsBackupRecord::getRestoreVerifiedAt).orElse(null));
    }

    public int backupMaxAgeHours() {
        return defaultRetentionCheckHours;
    }

    public Optional<OpsBackupRecord> latest() {
        return backupRepository.findTop1ByOrderByStartedAtDesc();
    }

    public Optional<OpsBackupRecord> latestSuccess() {
        return backupRepository.findTop1ByStatusOrderByStartedAtDesc(SUCCESS);
    }

    OpsDtos.BackupView toView(OpsBackupRecord record) {
        return new OpsDtos.BackupView(
                record.getId(),
                record.getBackupCode(),
                record.getBackupType(),
                record.getStatus(),
                record.getSizeBytes() == null ? 0 : record.getSizeBytes(),
                record.getFileHash(),
                record.getTarget(),
                record.getStartedAt(),
                record.getFinishedAt(),
                record.getDurationSeconds(),
                record.getMessage(),
                record.getReportedBy(),
                record.isRestoreVerified(),
                record.getRestoreVerifiedAt(),
                record.getRestoreMessage(),
                record.getCreatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
