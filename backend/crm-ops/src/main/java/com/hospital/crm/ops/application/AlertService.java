package com.hospital.crm.ops.application;

import com.hospital.crm.common.api.ErrorCode;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.ops.api.OpsDtos;
import com.hospital.crm.ops.domain.OpsAlert;
import com.hospital.crm.ops.domain.OpsAlertRepository;
import com.hospital.crm.ops.domain.OpsBackupRecord;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 运维告警服务：内置规则定时评估（数据库、任务、备份、接口错误率/延迟、认证失败），
 * 同时接收外部（日志告警脚本等）上报；同一 dedup_key 只保留一条未关闭告警，
 * 条件恢复自动置 RESOLVED，人工可 ACK。
 *
 * <p>所有处置动作记录审计（OPS_ALERT_RAISE / OPS_ALERT_ACK），不记录患者数据。</p>
 */
@Service
public class AlertService {

    private static final String RESOURCE = "OPS_ALERT";
    private static final List<String> ACTIVE_STATUSES = List.of("OPEN", "ACKED");
    private static final List<String> SEVERITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final OpsAlertRepository alertRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final JobStatusService jobStatusService;
    private final BackupService backupService;
    private final ApiMetricsRegistry metricsRegistry;
    private final AuditService auditService;

    private final boolean enabled;
    private final int windowSeconds;
    private final int minSamples;
    private final double errorRateThreshold;
    private final long latencyThresholdMs;
    private final long authFailureThreshold;
    private final int backupMaxAgeHours;

    public AlertService(OpsAlertRepository alertRepository,
                        JdbcTemplate jdbcTemplate,
                        NamedParameterJdbcTemplate namedJdbcTemplate,
                        JobStatusService jobStatusService,
                        BackupService backupService,
                        ApiMetricsRegistry metricsRegistry,
                        AuditService auditService,
                        @Value("${crm.ops.alerts.enabled:true}") boolean enabled,
                        @Value("${crm.ops.alerts.api-window-seconds:300}") int windowSeconds,
                        @Value("${crm.ops.alerts.api-min-samples:20}") int minSamples,
                        @Value("${crm.ops.alerts.api-error-rate-threshold:0.05}") double errorRateThreshold,
                        @Value("${crm.ops.alerts.api-latency-threshold-ms:2000}") long latencyThresholdMs,
                        @Value("${crm.ops.alerts.auth-failure-threshold:20}") long authFailureThreshold,
                        @Value("${crm.ops.alerts.backup-max-age-hours:24}") int backupMaxAgeHours) {
        this.alertRepository = alertRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.jobStatusService = jobStatusService;
        this.backupService = backupService;
        this.metricsRegistry = metricsRegistry;
        this.auditService = auditService;
        this.enabled = enabled;
        this.windowSeconds = Math.max(60, windowSeconds);
        this.minSamples = Math.max(1, minSamples);
        this.errorRateThreshold = errorRateThreshold;
        this.latencyThresholdMs = latencyThresholdMs;
        this.authFailureThreshold = Math.max(1, authFailureThreshold);
        this.backupMaxAgeHours = Math.max(1, backupMaxAgeHours);
    }

    // ------------------------------------------------------------------
    // 规则评估
    // ------------------------------------------------------------------
    @Transactional
    public void evaluate() {
        if (!enabled) {
            return;
        }
        Instant now = Instant.now();

        boolean dbUp = databaseAvailable();
        apply("DB_UNAVAILABLE", !dbUp,
                "数据库不可用",
                "应用无法执行 PostgreSQL 校验查询（select 1 失败）",
                "DB_UNAVAILABLE", "CRITICAL", now);

        evaluateBackups(now);
        evaluateJobs(now);
        evaluateApi(now);
    }

    private void evaluateBackups(Instant now) {
        Optional<OpsBackupRecord> latest = backupService.latest();
        boolean latestFailed = latest.isPresent() && "FAILED".equals(latest.get().getStatus());
        apply("BACKUP_FAILED", latestFailed,
                "最近一次备份失败",
                latest.map(record -> "备份编号 " + record.getBackupCode() + " 状态 FAILED")
                        .orElse("无备份记录"),
                "BACKUP_FAILED", "HIGH", now);

        Optional<OpsBackupRecord> latestSuccess = backupService.latestSuccess();
        boolean stale = latestSuccess.isEmpty()
                || Duration.between(latestSuccess.get().getFinishedAt(), now).toHours() > backupMaxAgeHours;
        String detail = latestSuccess
                .map(record -> "最近成功备份 " + record.getBackupCode() + " 完成于 " + record.getFinishedAt()
                        + "，已超过 " + backupMaxAgeHours + " 小时")
                .orElse("尚无成功备份记录");
        apply("BACKUP_STALE", stale, "备份超过 " + backupMaxAgeHours + " 小时未成功", detail,
                "BACKUP_STALE", "HIGH", now);
    }

    private void evaluateJobs(Instant now) {
        List<OpsDtos.JobStatusView> jobs = jobStatusService.list();
        for (OpsDtos.JobStatusView job : jobs) {
            apply("JOB_STALE:" + job.jobCode(), job.stale(),
                    "定时任务停滞：" + job.jobName(),
                    "任务 " + job.jobCode() + " 最近执行时间 " + job.lastRunAt()
                            + "，超过 " + job.expectedIntervalSeconds() + " 秒的 3 倍",
                    "JOB_STALE:" + job.jobCode(), "HIGH", now);

            boolean failing = job.consecutiveFailures() >= jobStatusService.failureThreshold();
            apply("JOB_FAILED:" + job.jobCode(), failing,
                    "定时任务连续失败：" + job.jobName(),
                    "任务 " + job.jobCode() + " 连续失败 " + job.consecutiveFailures()
                            + " 次，最近错误：" + (job.lastMessage() == null ? "-" : job.lastMessage()),
                    "JOB_FAILED:" + job.jobCode(), "HIGH", now);
        }
    }

    private void evaluateApi(Instant now) {
        ApiMetricsRegistry.WindowSummary window = metricsRegistry.summarize(windowSeconds);
        boolean enoughSamples = window.count() >= minSamples;
        double errorRate = window.count() == 0 ? 0 : (double) window.errors() / window.count();

        apply("API_ERROR_RATE_HIGH", enoughSamples && errorRate >= errorRateThreshold,
                "关键接口 5xx 比例过高",
                String.format("最近 %d 秒请求 %d 次，5xx %d 次，比例 %.2f%%，阈值 %.2f%%",
                        windowSeconds, window.count(), window.errors(), errorRate * 100, errorRateThreshold * 100),
                "API_ERROR_RATE_HIGH", "HIGH", now);

        apply("API_LATENCY_HIGH", enoughSamples && window.avgLatencyMs() >= latencyThresholdMs,
                "接口平均耗时过高",
                String.format("最近 %d 秒平均耗时 %.0f ms，最大 %d ms，阈值 %d ms",
                        windowSeconds, window.avgLatencyMs(), window.maxLatencyMs(), latencyThresholdMs),
                "API_LATENCY_HIGH", "MEDIUM", now);

        apply("AUTH_FAILURE_BURST", window.authFailures() >= authFailureThreshold,
                "认证失败次数激增",
                String.format("最近 %d 秒收到 %d 次 401，阈值 %d 次，疑似弱口令爆破或令牌失效",
                        windowSeconds, window.authFailures(), authFailureThreshold),
                "AUTH_FAILURE_BURST", "HIGH", now);
    }

    /**
     * 条件成立则建单/刷新，条件恢复则自动关闭；用于内置规则。
     */
    private void apply(String code, boolean active, String title, String detail,
                       String dedupKey, String severity, Instant now) {
        if (active) {
            raiseInternal(code, severity, title, detail, dedupKey, "SYSTEM", now);
        } else {
            resolve(dedupKey, now);
        }
    }

    private void raiseInternal(String code, String severity, String title, String detail,
                               String dedupKey, String source, Instant now) {
        Optional<OpsAlert> existing = alertRepository.findFirstByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES);
        if (existing.isPresent()) {
            OpsAlert alert = existing.get();
            alert.setLastSeenAt(now);
            alert.setOccurrences(alert.getOccurrences() == null ? 1 : alert.getOccurrences() + 1);
            alert.setDetail(detail);
            alertRepository.save(alert);
            return;
        }
        OpsAlert alert = new OpsAlert();
        alert.setAlertCode(code);
        alert.setSeverity(severity);
        alert.setStatus("OPEN");
        alert.setTitle(title);
        alert.setDetail(detail);
        alert.setDedupKey(dedupKey);
        alert.setSource(source);
        alert.setFirstSeenAt(now);
        alert.setLastSeenAt(now);
        alert.setOccurrences(1);
        try {
            alertRepository.saveAndFlush(alert);
        } catch (DataIntegrityViolationException ex) {
            // 并发评估命中唯一索引：以已存在记录为准，忽略本次插入
            alertRepository.findFirstByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)
                    .ifPresent(concurrent -> {
                        concurrent.setLastSeenAt(now);
                        concurrent.setOccurrences(
                                (concurrent.getOccurrences() == null ? 1 : concurrent.getOccurrences()) + 1);
                        alertRepository.save(concurrent);
                    });
        }
    }

    private void resolve(String dedupKey, Instant now) {
        alertRepository.findFirstByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES).ifPresent(alert -> {
            alert.setStatus("RESOLVED");
            alert.setResolvedAt(now);
            alertRepository.save(alert);
        });
    }

    private boolean databaseAvailable() {
        try {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            return value != null && value == 1;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 查询与处置
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<OpsDtos.AlertView> list(String status, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(OpsDtos.page(page) - 1, OpsDtos.pageSize(pageSize),
                Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        Page<OpsAlert> result = (status == null || status.isBlank())
                ? alertRepository.findAll(pageRequest)
                : alertRepository.findByStatus(status.trim().toUpperCase(), pageRequest);
        Map<UUID, String> names = userNames(result.getContent().stream()
                .map(OpsAlert::getAckedBy).filter(java.util.Objects::nonNull).toList());
        return PageResponse.of(
                result.getContent().stream().map(alert -> toView(alert, names)).toList(),
                result.getTotalElements(), OpsDtos.page(page), OpsDtos.pageSize(pageSize));
    }

    @Transactional
    public OpsDtos.AlertView ack(UUID alertId) {
        OpsAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "告警不存在"));
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_FAILED, "告警已恢复，无需确认");
        }
        UUID userId = currentUserId();
        alert.setStatus("ACKED");
        alert.setAckedBy(userId);
        alert.setAckedAt(Instant.now());
        OpsAlert saved = alertRepository.save(alert);

        auditService.record("OPS_ALERT_ACK", RESOURCE, alertId.toString(),
                Map.of("alertCode", saved.getAlertCode(), "severity", saved.getSeverity()));
        return toView(saved, userNames(userId == null ? List.of() : List.of(userId)));
    }

    @Transactional
    public OpsDtos.AlertView raiseExternal(OpsDtos.RaiseAlertRequest request) {
        String severity = request.severity().toUpperCase();
        if (!SEVERITIES.contains(severity)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "告警级别取值不合法");
        }
        String dedupKey = request.dedupKey() == null || request.dedupKey().isBlank()
                ? request.alertCode().trim()
                : request.dedupKey().trim();
        raiseInternal(request.alertCode().trim(), severity, request.title().trim(),
                request.detail(), dedupKey,
                request.source() == null || request.source().isBlank() ? "EXTERNAL" : request.source().trim(),
                Instant.now());

        OpsAlert saved = alertRepository.findFirstByDedupKeyAndStatusIn(dedupKey, ACTIVE_STATUSES)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "告警写入失败"));
        auditService.record("OPS_ALERT_RAISE", RESOURCE, saved.getId().toString(),
                Map.of("alertCode", saved.getAlertCode(),
                        "severity", saved.getSeverity(),
                        "source", saved.getSource()));
        return toView(saved, Map.of());
    }

    @Transactional(readOnly = true)
    public List<OpsDtos.AlertRuleView> rules() {
        return List.of(
                new OpsDtos.AlertRuleView("DB_UNAVAILABLE", "数据库不可用", "CRITICAL",
                        "应用无法执行 PostgreSQL 校验查询或健康检查 db 组件不可用", null, null, enabled),
                new OpsDtos.AlertRuleView("JOB_STALE", "定时任务停滞", "HIGH",
                        "任务最近一次执行时间超过期望间隔的 3 倍", 3.0, null, enabled),
                new OpsDtos.AlertRuleView("JOB_FAILED", "定时任务连续失败", "HIGH",
                        "任务连续失败次数达到阈值", (double) jobStatusService.failureThreshold(), null, enabled),
                new OpsDtos.AlertRuleView("BACKUP_STALE", "备份超期", "HIGH",
                        "距离最近一次成功备份超过设定小时数", (double) backupMaxAgeHours, null, enabled),
                new OpsDtos.AlertRuleView("BACKUP_FAILED", "最近备份失败", "HIGH",
                        "最新一条备份记录状态为 FAILED", null, null, enabled),
                new OpsDtos.AlertRuleView("API_ERROR_RATE_HIGH", "接口 5xx 比例过高", "HIGH",
                        "窗口内 5xx 占请求数比例达到阈值且样本量足够", errorRateThreshold, windowSeconds, enabled),
                new OpsDtos.AlertRuleView("API_LATENCY_HIGH", "接口平均耗时过高", "MEDIUM",
                        "窗口内平均耗时达到阈值且样本量足够", (double) latencyThresholdMs, windowSeconds, enabled),
                new OpsDtos.AlertRuleView("AUTH_FAILURE_BURST", "认证失败激增", "HIGH",
                        "窗口内 401 次数达到阈值，疑似爆破或令牌批量失效",
                        (double) authFailureThreshold, windowSeconds, enabled));
    }

    @Transactional(readOnly = true)
    public OpsDtos.AlertSummary summary() {
        long open = alertRepository.countByStatusIn(List.of("OPEN"));
        long acked = alertRepository.countByStatusIn(List.of("ACKED"));
        long critical = alertRepository.countByStatusInAndSeverity(ACTIVE_STATUSES, "CRITICAL");
        long high = alertRepository.countByStatusInAndSeverity(ACTIVE_STATUSES, "HIGH");
        long medium = alertRepository.countByStatusInAndSeverity(ACTIVE_STATUSES, "MEDIUM");
        return new OpsDtos.AlertSummary(open + acked, critical, high, medium);
    }

    private Map<UUID, String> userNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", userIds);
        namedJdbcTemplate.query("select id, display_name from sys_user where id in (:ids)",
                parameters, resultSet -> {
                    names.put(resultSet.getObject("id", UUID.class), resultSet.getString("display_name"));
                });
        return names;
    }

    private OpsDtos.AlertView toView(OpsAlert alert, Map<UUID, String> names) {
        return new OpsDtos.AlertView(
                alert.getId(),
                alert.getAlertCode(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getTitle(),
                alert.getDetail(),
                alert.getDedupKey(),
                alert.getSource(),
                alert.getFirstSeenAt(),
                alert.getLastSeenAt(),
                alert.getOccurrences() == null ? 0 : alert.getOccurrences(),
                alert.getAckedBy(),
                alert.getAckedBy() == null ? null : names.get(alert.getAckedBy()),
                alert.getAckedAt(),
                alert.getResolvedAt(),
                alert.getCreatedAt());
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.userId();
        }
        return null;
    }
}
