package com.hospital.crm.callcenter.application;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 消息队列到期扫描任务：独立组件保证 dispatchDue 经过事务代理执行。
 * 扫描间隔可通过 crm.callcenter.outbox.scan-interval-ms 配置。
 */
@Component
@EnableScheduling
public class OutboxDispatchJob {

    private final SmsService smsService;

    public OutboxDispatchJob(SmsService smsService) {
        this.smsService = smsService;
    }

    @Scheduled(fixedDelayString = "${crm.callcenter.outbox.scan-interval-ms:60000}",
            initialDelayString = "${crm.callcenter.outbox.initial-delay-ms:30000}")
    public void dispatch() {
        smsService.dispatchDue();
    }
}
