package com.hospital.crm.ops.application;

import com.hospital.crm.common.ops.JobTracker;
import com.hospital.crm.ops.domain.OpsJobRun;
import com.hospital.crm.ops.domain.OpsJobRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 定时任务监控实现：记录每次执行的成功/失败、耗时与异常摘要，并维护任务定义
 * （名称、期望间隔）供停滞判定；运行记录按保留天数定期清理。
 */
@Component
public class JobMonitor implements JobTracker {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final OpsJobRunRepository runRepository;
    private final Map<String, JobDefinition> definitions = new ConcurrentHashMap<>();
    private final AtomicLong lastPurgeEpochSecond = new AtomicLong(0);
    private final int retentionDays;

    public JobMonitor(OpsJobRunRepository runRepository,
                      @Value("${crm.ops.job-retention-days:30}") int retentionDays) {
        this.runRepository = runRepository;
        this.retentionDays = retentionDays;
    }

    @Override
    public void track(String jobCode, String jobName, Duration expectedInterval, Runnable action) {
        definitions.put(jobCode, new JobDefinition(jobCode, jobName,
                expectedInterval == null ? Duration.ofMinutes(1) : expectedInterval));
        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();
        String status = SUCCESS;
        String message = null;
        try {
            action.run();
        } catch (RuntimeException ex) {
            status = FAILED;
            message = truncate(ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            throw ex;
        } finally {
            registerRun(jobCode, status, message, startedAt, startNanos);
        }
    }

    public Map<String, JobDefinition> definitions() {
        return Map.copyOf(definitions);
    }

    private void registerRun(String jobCode, String status, String message,
                             Instant startedAt, long startNanos) {
        try {
            OpsJobRun run = new OpsJobRun();
            run.setJobCode(jobCode);
            run.setStatus(status);
            run.setStartedAt(startedAt);
            run.setFinishedAt(Instant.now());
            run.setDurationMs((System.nanoTime() - startNanos) / 1_000_000);
            run.setMessage(message);
            runRepository.save(run);
        } finally {
            purgeIfNeeded();
        }
    }

    private void purgeIfNeeded() {
        long now = Instant.now().getEpochSecond();
        long last = lastPurgeEpochSecond.get();
        if (now - last < 3600 || !lastPurgeEpochSecond.compareAndSet(last, now)) {
            return;
        }
        runRepository.deleteByCreatedAtBefore(Instant.now().minus(Duration.ofDays(retentionDays)));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH);
    }

    /** 任务定义：编码、名称与期望执行间隔。 */
    public record JobDefinition(String jobCode, String jobName, Duration expectedInterval) {
    }
}
