package com.hospital.crm.common.ops;

import java.time.Duration;

/**
 * 定时任务追踪 SPI：业务模块（预约提醒、投诉升级、短信派发等）通过该接口上报
 * 任务执行开始/结束、耗时与失败信息，由运维模块统一落库并在看板与告警中使用。
 *
 * <p>放在 crm-common 中是为了让任务组件只依赖接口，避免业务模块反向依赖运维模块。</p>
 */
public interface JobTracker {

    /**
     * 执行任务并记录一次运行结果。
     *
     * @param jobCode          任务编码（全局唯一，如 OUTBOX_DISPATCH）
     * @param jobName          任务名称（用于运维展示）
     * @param expectedInterval 期望执行间隔，用于判定任务停滞
     * @param action           任务体
     */
    void track(String jobCode, String jobName, Duration expectedInterval, Runnable action);
}
