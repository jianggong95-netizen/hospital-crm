package com.hospital.crm.ops.config;

import com.hospital.crm.ops.application.AlertService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 告警规则定时评估：默认每分钟执行一次，评估结果写入 crm_ops_alert。
 * 评估间隔可通过 crm.ops.alerts.evaluation-interval-ms 配置。
 */
@Component
@EnableScheduling
public class OpsAlertJob {

    private final AlertService alertService;

    public OpsAlertJob(AlertService alertService) {
        this.alertService = alertService;
    }

    @Scheduled(fixedDelayString = "${crm.ops.alerts.evaluation-interval-ms:60000}",
            initialDelayString = "${crm.ops.alerts.initial-delay-ms:30000}")
    public void evaluate() {
        alertService.evaluate();
    }
}
