package com.hospital.crm.ops.application;

import com.hospital.crm.ops.domain.OpsJobRun;
import com.hospital.crm.ops.domain.OpsJobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务追踪：成功与失败均落库，失败保留异常摘要并继续抛出，不吞异常。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobMonitorTest {

    @Mock
    private OpsJobRunRepository runRepository;

    private JobMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new JobMonitor(runRepository, 30);
        when(runRepository.save(any(OpsJobRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void successfulRunIsRecordedAsSuccess() {
        monitor.track("OUTBOX_DISPATCH", "短信队列派发", Duration.ofSeconds(60), () -> {
        });

        ArgumentCaptor<OpsJobRun> captor = ArgumentCaptor.forClass(OpsJobRun.class);
        verify(runRepository).save(captor.capture());
        OpsJobRun run = captor.getValue();
        assertThat(run.getJobCode()).isEqualTo("OUTBOX_DISPATCH");
        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getDurationMs()).isNotNull();
        assertThat(run.getMessage()).isNull();
        assertThat(monitor.definitions()).containsKey("OUTBOX_DISPATCH");
    }

    @Test
    void failedRunIsRecordedAndRethrown() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> monitor.track("REMINDER_DISPATCH", "预约提醒派发",
                Duration.ofSeconds(60), () -> {
                    throw failure;
                })).isSameAs(failure);

        ArgumentCaptor<OpsJobRun> captor = ArgumentCaptor.forClass(OpsJobRun.class);
        verify(runRepository).save(captor.capture());
        OpsJobRun run = captor.getValue();
        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getMessage()).contains("IllegalStateException").contains("boom");
    }

    @Test
    void longErrorMessageIsTruncated() {
        String longMessage = "x".repeat(900);

        assertThatThrownBy(() -> monitor.track("COMPLAINT_ESCALATION", "投诉超时升级扫描",
                Duration.ofSeconds(300), () -> {
                    throw new IllegalStateException(longMessage);
                })).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<OpsJobRun> captor = ArgumentCaptor.forClass(OpsJobRun.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).hasSize(500);
    }
}
