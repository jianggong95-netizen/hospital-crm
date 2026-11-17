package com.hospital.crm.appointment.application;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提醒到期扫描任务：独立组件保证 dispatchDue 经过事务代理执行。
 * 扫描间隔可通过 crm.appointment.reminder.scan-interval-ms 配置。
 */
@Component
@EnableScheduling
public class ReminderDispatchJob {

    private final ReminderService reminderService;

    public ReminderDispatchJob(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${crm.appointment.reminder.scan-interval-ms:60000}",
            initialDelayString = "${crm.appointment.reminder.initial-delay-ms:30000}")
    public void dispatch() {
        reminderService.dispatchDue();
    }
}
