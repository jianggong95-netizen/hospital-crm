package com.hospital.crm.ops.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * 运维接口契约 DTO。总览、任务、告警、备份均返回明确的状态与时间戳，
 * 便于运维定位「何时、哪个任务/接口/备份出了问题」。
 */
public final class OpsDtos {

    private OpsDtos() {
    }

    // ------------------------------------------------------------------
    // 总览
    // ------------------------------------------------------------------
    public record OverviewView(
            String appName,
            String appVersion,
            String profile,
            Instant startedAt,
            long uptimeSeconds,
            DatabaseStatus database,
            ApiSummary api,
            JobSummary jobs,
            BackupSummary backups,
            AlertSummary alerts,
            Capabilities capabilities,
            Instant generatedAt
    ) {
    }

    public record DatabaseStatus(String status, long roundTripMs) {
    }

    public record ApiSummary(
            int windowMinutes,
            long requestCount,
            long errorCount,
            double errorRate,
            double avgLatencyMs,
            long maxLatencyMs
    ) {
    }

    public record JobSummary(int total, int stale, int failing) {
    }

    public record BackupSummary(
            Instant lastSuccessAt,
            String lastBackupCode,
            String lastStatus,
            Double ageHours,
            boolean restoreVerified,
            Instant lastVerifiedAt
    ) {
    }

    public record AlertSummary(long open, long critical, long high, long medium) {
    }

    public record Capabilities(boolean hisEnabled, boolean callCenterEnabled, String smsProvider) {
    }

    // ------------------------------------------------------------------
    // 定时任务
    // ------------------------------------------------------------------
    public record JobStatusView(
            String jobCode,
            String jobName,
            long expectedIntervalSeconds,
            Instant lastRunAt,
            String lastStatus,
            Long lastDurationMs,
            String lastMessage,
            int consecutiveFailures,
            boolean stale,
            boolean neverRun
    ) {
    }

    public record JobRunView(
            UUID id,
            String jobCode,
            String status,
            Instant startedAt,
            Instant finishedAt,
            long durationMs,
            String message
    ) {
    }

    // ------------------------------------------------------------------
    // 告警
    // ------------------------------------------------------------------
    public record AlertView(
            UUID id,
            String alertCode,
            String severity,
            String status,
            String title,
            String detail,
            String dedupKey,
            String source,
            Instant firstSeenAt,
            Instant lastSeenAt,
            int occurrences,
            UUID ackedBy,
            String ackedByName,
            Instant ackedAt,
            Instant resolvedAt,
            Instant createdAt
    ) {
    }

    public record AlertRuleView(
            String alertCode,
            String name,
            String severity,
            String condition,
            Double threshold,
            Integer windowSeconds,
            boolean enabled
    ) {
    }

    public record RaiseAlertRequest(
            @NotBlank(message = "告警编码不能为空")
            @Size(max = 64, message = "告警编码长度不能超过 64")
            String alertCode,

            @NotBlank(message = "告警级别不能为空")
            @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW", message = "告警级别取值不合法")
            String severity,

            @NotBlank(message = "告警标题不能为空")
            @Size(max = 200, message = "告警标题长度不能超过 200")
            String title,

            String detail,

            @Size(max = 160, message = "去重键长度不能超过 160")
            String dedupKey,

            @Size(max = 32, message = "来源长度不能超过 32")
            String source
    ) {
    }

    // ------------------------------------------------------------------
    // 备份
    // ------------------------------------------------------------------
    public record BackupView(
            UUID id,
            String backupCode,
            String backupType,
            String status,
            long sizeBytes,
            String fileHash,
            String target,
            Instant startedAt,
            Instant finishedAt,
            Long durationSeconds,
            String message,
            String reportedBy,
            boolean restoreVerified,
            Instant restoreVerifiedAt,
            String restoreMessage,
            Instant createdAt
    ) {
    }

    public record BackupReportRequest(
            @NotBlank(message = "备份编号不能为空")
            @Size(max = 80, message = "备份编号长度不能超过 80")
            String backupCode,

            @NotBlank(message = "备份类型不能为空")
            @Pattern(regexp = "FULL|WAL|OBJECT", message = "备份类型取值不合法")
            String backupType,

            @NotBlank(message = "备份状态不能为空")
            @Pattern(regexp = "SUCCESS|FAILED", message = "备份状态取值不合法")
            String status,

            @NotNull(message = "备份大小不能为空")
            @Min(value = 0, message = "备份大小不能为负数")
            Long sizeBytes,

            @Pattern(regexp = "[0-9a-fA-F]{64}", message = "文件哈希必须为 64 位十六进制 SHA-256")
            String fileHash,

            @Size(max = 120, message = "备份目标长度不能超过 120")
            String target,

            @NotNull(message = "开始时间不能为空")
            Instant startedAt,

            @NotNull(message = "结束时间不能为空")
            Instant finishedAt,

            @Size(max = 500, message = "备注长度不能超过 500")
            String message
    ) {
    }

    public record BackupVerifyRequest(
            @NotNull(message = "恢复演练结果不能为空")
            Boolean restoreVerified,

            @Size(max = 500, message = "演练说明长度不能超过 500")
            String restoreMessage
    ) {
    }

    // ------------------------------------------------------------------
    // 查询参数归一化
    // ------------------------------------------------------------------
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_RUN_LIMIT = 100;

    public static int page(int page) {
        return Math.max(1, page);
    }

    public static int pageSize(int pageSize) {
        return Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
    }

    public static int runLimit(Integer limit) {
        return Math.min(Math.max(1, limit == null ? 20 : limit), MAX_RUN_LIMIT);
    }
}
