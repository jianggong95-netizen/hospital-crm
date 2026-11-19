package com.hospital.crm.ops.application;

import com.hospital.crm.ops.api.OpsDtos;
import com.hospital.crm.ops.domain.OpsJobRun;
import com.hospital.crm.ops.domain.OpsJobRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 定时任务状态查询：基于任务定义与最近运行记录计算是否停滞、连续失败次数。
 *
 * <p>停滞判定：最近一次执行超过 N 倍期望间隔（默认 3 倍）；从未执行不算停滞，
 * 避免应用刚启动就误报。</p>
 */
@Service
public class JobStatusService {

    private final JobMonitor jobMonitor;
    private final OpsJobRunRepository runRepository;
    private final int staleMultiplier;
    private final int failureThreshold;

    public JobStatusService(JobMonitor jobMonitor,
                            OpsJobRunRepository runRepository,
                            @Value("${crm.ops.alerts.job-stale-multiplier:3}") int staleMultiplier,
                            @Value("${crm.ops.alerts.job-failure-threshold:3}") int failureThreshold) {
        this.jobMonitor = jobMonitor;
        this.runRepository = runRepository;
        this.staleMultiplier = Math.max(2, staleMultiplier);
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    @Transactional(readOnly = true)
    public List<OpsDtos.JobStatusView> list() {
        return jobMonitor.definitions().values().stream()
                .sorted(Comparator.comparing(JobMonitor.JobDefinition::jobCode))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpsDtos.JobRunView> runs(String jobCode, int limit) {
        return runRepository.findTop20ByJobCodeOrderByStartedAtDesc(jobCode).stream()
                .limit(limit)
                .map(run -> new OpsDtos.JobRunView(run.getId(), run.getJobCode(), run.getStatus(),
                        run.getStartedAt(), run.getFinishedAt(), run.getDurationMs(), run.getMessage()))
                .toList();
    }

    public OpsDtos.JobSummary summary(List<OpsDtos.JobStatusView> jobs) {
        int stale = (int) jobs.stream().filter(OpsDtos.JobStatusView::stale).count();
        int failing = (int) jobs.stream()
                .filter(job -> job.consecutiveFailures() >= failureThreshold)
                .count();
        return new OpsDtos.JobSummary(jobs.size(), stale, failing);
    }

    public int failureThreshold() {
        return failureThreshold;
    }

    private OpsDtos.JobStatusView toView(JobMonitor.JobDefinition definition) {
        List<OpsJobRun> recent = runRepository
                .findTop20ByJobCodeOrderByStartedAtDesc(definition.jobCode());
        if (recent.isEmpty()) {
            return new OpsDtos.JobStatusView(definition.jobCode(), definition.jobName(),
                    definition.expectedInterval().toSeconds(), null, null, null, null, 0, false, true);
        }
        OpsJobRun latest = recent.get(0);
        int consecutiveFailures = 0;
        for (OpsJobRun run : recent) {
            if (!"FAILED".equals(run.getStatus())) {
                break;
            }
            consecutiveFailures++;
        }
        Duration staleAfter = definition.expectedInterval().multipliedBy(staleMultiplier);
        boolean stale = Duration.between(latest.getStartedAt(), Instant.now()).compareTo(staleAfter) > 0;
        return new OpsDtos.JobStatusView(
                definition.jobCode(),
                definition.jobName(),
                definition.expectedInterval().toSeconds(),
                latest.getStartedAt(),
                latest.getStatus(),
                latest.getDurationMs(),
                latest.getMessage(),
                consecutiveFailures,
                stale,
                false);
    }
}
