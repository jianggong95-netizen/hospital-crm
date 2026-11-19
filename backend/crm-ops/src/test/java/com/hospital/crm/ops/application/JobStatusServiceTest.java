package com.hospital.crm.ops.application;

import com.hospital.crm.ops.api.OpsDtos;
import com.hospital.crm.ops.domain.OpsJobRun;
import com.hospital.crm.ops.domain.OpsJobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 定时任务状态：从未执行不算停滞、超过 3 倍间隔判定停滞、连续失败计数正确。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobStatusServiceTest {

    private static final String JOB_CODE = "REMINDER_DISPATCH";

    @Mock
    private JobMonitor jobMonitor;
    @Mock
    private OpsJobRunRepository runRepository;

    private JobStatusService service;

    @BeforeEach
    void setUp() {
        service = new JobStatusService(jobMonitor, runRepository, 3, 3);
        when(jobMonitor.definitions()).thenReturn(Map.of(JOB_CODE,
                new JobMonitor.JobDefinition(JOB_CODE, "预约提醒派发", Duration.ofSeconds(60))));
    }

    @Test
    void neverRunJobIsNotMarkedStale() {
        when(runRepository.findTop20ByJobCodeOrderByStartedAtDesc(JOB_CODE)).thenReturn(List.of());

        List<OpsDtos.JobStatusView> jobs = service.list();

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).neverRun()).isTrue();
        assertThat(jobs.get(0).stale()).isFalse();
        assertThat(jobs.get(0).lastRunAt()).isNull();
    }

    @Test
    void jobOlderThanThreeIntervalsIsStale() {
        when(runRepository.findTop20ByJobCodeOrderByStartedAtDesc(JOB_CODE))
                .thenReturn(List.of(run("SUCCESS", Instant.now().minusSeconds(200), null)));

        OpsDtos.JobStatusView job = service.list().get(0);

        assertThat(job.stale()).isTrue();
        assertThat(job.neverRun()).isFalse();
        assertThat(job.lastStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void jobWithinIntervalIsNotStale() {
        when(runRepository.findTop20ByJobCodeOrderByStartedAtDesc(JOB_CODE))
                .thenReturn(List.of(run("SUCCESS", Instant.now().minusSeconds(30), null)));

        assertThat(service.list().get(0).stale()).isFalse();
    }

    @Test
    void consecutiveFailuresCountedFromLatestRuns() {
        when(runRepository.findTop20ByJobCodeOrderByStartedAtDesc(JOB_CODE)).thenReturn(List.of(
                run("FAILED", Instant.now().minusSeconds(10), "IllegalStateException: boom"),
                run("FAILED", Instant.now().minusSeconds(70), "IllegalStateException: boom"),
                run("SUCCESS", Instant.now().minusSeconds(130), null)));

        OpsDtos.JobStatusView job = service.list().get(0);

        assertThat(job.consecutiveFailures()).isEqualTo(2);
        assertThat(job.lastMessage()).contains("boom");
        assertThat(service.summary(service.list()).failing()).isZero();
    }

    @Test
    void summaryCountsStaleAndFailingJobs() {
        when(runRepository.findTop20ByJobCodeOrderByStartedAtDesc(JOB_CODE)).thenReturn(List.of(
                run("FAILED", Instant.now().minusSeconds(200), "err"),
                run("FAILED", Instant.now().minusSeconds(260), "err"),
                run("FAILED", Instant.now().minusSeconds(320), "err")));

        OpsDtos.JobSummary summary = service.summary(service.list());

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.stale()).isEqualTo(1);
        assertThat(summary.failing()).isEqualTo(1);
    }

    private OpsJobRun run(String status, Instant startedAt, String message) {
        OpsJobRun run = new OpsJobRun();
        run.setJobCode(JOB_CODE);
        run.setStatus(status);
        run.setStartedAt(startedAt);
        run.setFinishedAt(startedAt.plusMillis(120));
        run.setDurationMs(120L);
        run.setMessage(message);
        return run;
    }
}
