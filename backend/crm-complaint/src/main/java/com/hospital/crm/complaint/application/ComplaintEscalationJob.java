package com.hospital.crm.complaint.application;

import com.hospital.crm.common.ops.JobTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 超时未结案扫描任务：独立组件保证 scanOverdue 经过事务代理执行。
 * 扫描间隔可通过 crm.complaint.escalation.scan-interval-ms 配置；
 * 每次执行经 JobTracker 记录，供运维监控判定任务停滞与连续失败。
 */
@Component
@EnableScheduling
public class ComplaintEscalationJob {

    private final ComplaintService complaintService;
    private final JobTracker jobTracker;

    @Value("${crm.complaint.escalation.scan-interval-ms:300000}")
    private long scanIntervalMs = 300000;

    public ComplaintEscalationJob(ComplaintService complaintService, JobTracker jobTracker) {
        this.complaintService = complaintService;
        this.jobTracker = jobTracker;
    }

    @Scheduled(fixedDelayString = "${crm.complaint.escalation.scan-interval-ms:300000}",
            initialDelayString = "${crm.complaint.escalation.initial-delay-ms:60000}")
    public void scan() {
        jobTracker.track("COMPLAINT_ESCALATION", "投诉超时升级扫描",
                Duration.ofMillis(scanIntervalMs), complaintService::scanOverdue);
    }
}
