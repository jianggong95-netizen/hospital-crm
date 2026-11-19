package com.hospital.crm.ops.application;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口指标滑动窗口：按 10 秒分桶记录业务接口请求数、5xx 数、401 数与耗时，
 * 供总览展示与告警规则评估使用；进程内保留最近 15 分钟，不落库。
 *
 * <p>只统计 /api/** 业务接口，跳过 actuator 探针与 Prometheus 抓取，避免自观测噪声。</p>
 */
@Component
public class ApiMetricsRegistry {

    private static final int BUCKET_SECONDS = 10;
    private static final long RETENTION_SECONDS = 900;
    private static final int MAX_MODULES = 64;

    private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, ModuleCounter> modules = new ConcurrentHashMap<>();

    public void record(String module, int status, long durationMs) {
        long key = Instant.now().getEpochSecond() / BUCKET_SECONDS;
        buckets.computeIfAbsent(key, ignored -> new Bucket()).add(status, durationMs);
        if (modules.size() < MAX_MODULES) {
            modules.computeIfAbsent(module, ignored -> new ModuleCounter()).add(status);
        }
        prune();
    }

    public WindowSummary summarize(int windowSeconds) {
        long minKey = Instant.now().getEpochSecond() / BUCKET_SECONDS - windowSeconds / BUCKET_SECONDS;
        long count = 0;
        long errors = 0;
        long authFailures = 0;
        long latencySum = 0;
        long maxLatency = 0;
        for (Map.Entry<Long, Bucket> entry : buckets.entrySet()) {
            if (entry.getKey() < minKey) {
                continue;
            }
            Snapshot snapshot = entry.getValue().snapshot();
            count += snapshot.count();
            errors += snapshot.errors();
            authFailures += snapshot.authFailures();
            latencySum += snapshot.latencySum();
            maxLatency = Math.max(maxLatency, snapshot.maxLatency());
        }
        double avgLatency = count == 0 ? 0 : (double) latencySum / count;
        return new WindowSummary(count, errors, authFailures, avgLatency, maxLatency);
    }

    /** 各接口模块（路径第二段）的累计计数，用于总览展示。 */
    public List<ModuleStat> modules() {
        List<ModuleStat> stats = new ArrayList<>();
        modules.forEach((module, counter) -> stats.add(counter.stat(module)));
        stats.sort((left, right) -> Long.compare(right.count(), left.count()));
        return stats;
    }

    private void prune() {
        long minKey = Instant.now().getEpochSecond() / BUCKET_SECONDS - RETENTION_SECONDS / BUCKET_SECONDS;
        buckets.keySet().removeIf(key -> key < minKey);
    }

    public record WindowSummary(long count, long errors, long authFailures,
                                double avgLatencyMs, long maxLatencyMs) {
    }

    public record ModuleStat(String module, long count, long errors) {
    }

    private record Snapshot(long count, long errors, long authFailures, long latencySum, long maxLatency) {
    }

    private static final class Bucket {

        private long count;
        private long errors;
        private long authFailures;
        private long latencySum;
        private long maxLatency;

        synchronized void add(int status, long durationMs) {
            count++;
            latencySum += Math.max(0, durationMs);
            maxLatency = Math.max(maxLatency, durationMs);
            if (status >= 500) {
                errors++;
            }
            if (status == 401) {
                authFailures++;
            }
        }

        synchronized Snapshot snapshot() {
            return new Snapshot(count, errors, authFailures, latencySum, maxLatency);
        }
    }

    private static final class ModuleCounter {

        private long count;
        private long errors;

        synchronized void add(int status) {
            count++;
            if (status >= 500) {
                errors++;
            }
        }

        synchronized ModuleStat stat(String module) {
            return new ModuleStat(module, count, errors);
        }
    }
}
