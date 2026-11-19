package com.hospital.crm.ops.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口指标滑动窗口：5xx 与 401 计数、平均/最大耗时统计。
 */
class ApiMetricsRegistryTest {

    @Test
    void summarizeCountsErrorsAuthFailuresAndLatency() {
        ApiMetricsRegistry registry = new ApiMetricsRegistry();

        registry.record("appointments", 200, 30);
        registry.record("appointments", 200, 50);
        registry.record("orders", 500, 100);
        registry.record("orders", 401, 10);
        registry.record("orders", 401, 20);

        ApiMetricsRegistry.WindowSummary summary = registry.summarize(300);

        assertThat(summary.count()).isEqualTo(5);
        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.authFailures()).isEqualTo(2);
        assertThat(summary.maxLatencyMs()).isEqualTo(100);
        assertThat(summary.avgLatencyMs()).isEqualTo(42.0);
    }

    @Test
    void summarizeEmptyWindowReturnsZero() {
        ApiMetricsRegistry.WindowSummary summary = new ApiMetricsRegistry().summarize(300);

        assertThat(summary.count()).isZero();
        assertThat(summary.errors()).isZero();
        assertThat(summary.avgLatencyMs()).isZero();
    }

    @Test
    void moduleStatsAggregateByModule() {
        ApiMetricsRegistry registry = new ApiMetricsRegistry();
        registry.record("analytics", 200, 10);
        registry.record("analytics", 500, 20);
        registry.record("customers", 200, 10);

        var stats = registry.modules();

        assertThat(stats).hasSize(2);
        assertThat(stats.get(0).module()).isEqualTo("analytics");
        assertThat(stats.get(0).count()).isEqualTo(2);
        assertThat(stats.get(0).errors()).isEqualTo(1);
    }
}
