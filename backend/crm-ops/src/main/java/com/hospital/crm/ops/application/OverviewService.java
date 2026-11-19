package com.hospital.crm.ops.application;

import com.hospital.crm.ops.api.OpsDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 运维总览：聚合服务运行时长、数据库连通性与往返耗时、接口窗口指标、
 * 任务/备份/告警摘要与外部集成开关，便于一屏判断系统健康状态。
 */
@Service
public class OverviewService {

    private static final int API_WINDOW_MINUTES = 5;

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final JobStatusService jobStatusService;
    private final BackupService backupService;
    private final AlertService alertService;
    private final ApiMetricsRegistry metricsRegistry;
    private final Instant startedAt = Instant.now();

    private final String appName;
    private final String appVersion;
    private final boolean hisEnabled;
    private final boolean callCenterEnabled;
    private final String smsProvider;

    public OverviewService(JdbcTemplate jdbcTemplate,
                           Environment environment,
                           JobStatusService jobStatusService,
                           BackupService backupService,
                           AlertService alertService,
                           ApiMetricsRegistry metricsRegistry,
                           @Value("${spring.application.name:hospital-crm}") String appName,
                           @Value("${crm.version:1.0.0-SNAPSHOT}") String appVersion,
                           @Value("${crm.his.enabled:false}") boolean hisEnabled,
                           @Value("${crm.callcenter.enabled:false}") boolean callCenterEnabled,
                           @Value("${crm.sms.provider:simulated}") String smsProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.jobStatusService = jobStatusService;
        this.backupService = backupService;
        this.alertService = alertService;
        this.metricsRegistry = metricsRegistry;
        this.appName = appName;
        this.appVersion = appVersion;
        this.hisEnabled = hisEnabled;
        this.callCenterEnabled = callCenterEnabled;
        this.smsProvider = smsProvider;
    }

    @Transactional(readOnly = true)
    public OpsDtos.OverviewView overview() {
        List<OpsDtos.JobStatusView> jobs = jobStatusService.list();
        ApiMetricsRegistry.WindowSummary window = metricsRegistry.summarize(API_WINDOW_MINUTES * 60);
        double errorRate = window.count() == 0 ? 0 : (double) window.errors() / window.count();
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? environment.getDefaultProfiles()[0] : String.join(",", profiles);

        return new OpsDtos.OverviewView(
                appName,
                appVersion,
                profile,
                startedAt,
                Math.max(0, Duration.between(startedAt, Instant.now()).toSeconds()),
                databaseStatus(),
                new OpsDtos.ApiSummary(API_WINDOW_MINUTES, window.count(), window.errors(),
                        Math.round(errorRate * 10000) / 10000.0,
                        Math.round(window.avgLatencyMs() * 10) / 10.0,
                        window.maxLatencyMs()),
                jobStatusService.summary(jobs),
                backupService.summary(),
                alertService.summary(),
                new OpsDtos.Capabilities(hisEnabled, callCenterEnabled, smsProvider),
                Instant.now());
    }

    private OpsDtos.DatabaseStatus databaseStatus() {
        long startNanos = System.nanoTime();
        try {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            long roundTrip = (System.nanoTime() - startNanos) / 1_000_000;
            return new OpsDtos.DatabaseStatus(value != null && value == 1 ? "UP" : "DOWN", roundTrip);
        } catch (RuntimeException ex) {
            return new OpsDtos.DatabaseStatus("DOWN", (System.nanoTime() - startNanos) / 1_000_000);
        }
    }
}
