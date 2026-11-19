package com.hospital.crm.appointment.application;

import com.hospital.crm.common.ops.JobTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 提醒到期扫描任务：独立组件保证 dispatchDue 经过事务代理执行。
 * 扫描间隔可通过 crm.appointment.reminder.scan-interval-ms 配置；
 * 每次执行经 JobTracker 记录，供运维监控判定任务停滞与连续失败。
 */
@Component
@EnableScheduling
public class ReminderDispatchJob {

    private final ReminderService reminderService;
    private final JobTracker jobTracker;

    @Value("${crm.appointment.reminder.scan-interval-ms:60000}")
    private long scanIntervalMs = 60000;

    public ReminderDispatchJob(ReminderService reminderService, JobTracker jobTracker) {
        this.reminderService = reminderService;
        this.jobTracker = jobTracker;
    }

    @Scheduled(fixedDelayString = "${crm.appointment.reminder.scan-interval-ms:60000}",
            initialDelayString = "${crm.appointment.reminder.initial-delay-ms:30000}")
    public void dispatch() {
        jobTracker.track("REMINDER_DISPATCH", "预约提醒派发",
                Duration.ofMillis(scanIntervalMs), reminderService::dispatchDue);
    }
}
