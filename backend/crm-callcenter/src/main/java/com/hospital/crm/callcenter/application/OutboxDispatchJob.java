package com.hospital.crm.callcenter.application;

import com.hospital.crm.common.ops.JobTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 消息队列到期扫描任务：独立组件保证 dispatchDue 经过事务代理执行。
 * 扫描间隔可通过 crm.callcenter.outbox.scan-interval-ms 配置；
 * 每次执行经 JobTracker 记录，供运维监控判定任务停滞与连续失败。
 */
@Component
@EnableScheduling
public class OutboxDispatchJob {

    private final SmsService smsService;
    private final JobTracker jobTracker;

    @Value("${crm.callcenter.outbox.scan-interval-ms:60000}")
    private long scanIntervalMs = 60000;

    public OutboxDispatchJob(SmsService smsService, JobTracker jobTracker) {
        this.smsService = smsService;
        this.jobTracker = jobTracker;
    }

    @Scheduled(fixedDelayString = "${crm.callcenter.outbox.scan-interval-ms:60000}",
            initialDelayString = "${crm.callcenter.outbox.initial-delay-ms:30000}")
    public void dispatch() {
        jobTracker.track("OUTBOX_DISPATCH", "短信队列派发",
                Duration.ofMillis(scanIntervalMs), smsService::dispatchDue);
    }
}
