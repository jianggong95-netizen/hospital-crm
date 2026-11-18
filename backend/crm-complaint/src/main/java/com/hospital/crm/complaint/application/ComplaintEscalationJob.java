package com.hospital.crm.complaint.application;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未结案扫描任务：独立组件保证 scanOverdue 经过事务代理执行。
 * 扫描间隔可通过 crm.complaint.escalation.scan-interval-ms 配置。
 */
@Component
@EnableScheduling
public class ComplaintEscalationJob {

    private final ComplaintService complaintService;

    public ComplaintEscalationJob(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @Scheduled(fixedDelayString = "${crm.complaint.escalation.scan-interval-ms:300000}",
            initialDelayString = "${crm.complaint.escalation.initial-delay-ms:60000}")
    public void scan() {
        complaintService.scanOverdue();
    }
}
