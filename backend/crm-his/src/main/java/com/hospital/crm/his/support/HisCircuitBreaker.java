package com.hospital.crm.his.support;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * HIS 查询熔断器：连续失败达到阈值后进入 OPEN，在冷却期内直接拒绝查询；
 * 冷却结束后进入 HALF_OPEN，仅允许少量探测请求，探测成功恢复 CLOSED。
 */
public class HisCircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long openMillis;
    private final int halfOpenProbes;
    private final LongSupplier clockMillis;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtMillis;
    private int halfOpenAttempts;

    public HisCircuitBreaker(int failureThreshold, long openMillis, int halfOpenProbes) {
        this(failureThreshold, openMillis, halfOpenProbes, System::currentTimeMillis);
    }

    public HisCircuitBreaker(int failureThreshold, long openMillis, int halfOpenProbes,
                             LongSupplier clockMillis) {
        this.failureThreshold = Math.max(failureThreshold, 1);
        this.openMillis = Math.max(openMillis, 1);
        this.halfOpenProbes = Math.max(halfOpenProbes, 1);
        this.clockMillis = clockMillis;
    }

    public synchronized boolean tryAcquire() {
        if (state == State.OPEN) {
            if (clockMillis.getAsLong() - openedAtMillis >= openMillis) {
                state = State.HALF_OPEN;
                halfOpenAttempts = 1;
                return true;
            }
            return false;
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenAttempts < halfOpenProbes) {
                halfOpenAttempts++;
                return true;
            }
            return false;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        halfOpenAttempts = 0;
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State state() {
        return state;
    }

    public synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    public long openMillis() {
        return openMillis;
    }

    public Instant openUntil() {
        return state == State.OPEN ? Instant.ofEpochMilli(openedAtMillis + openMillis) : null;
    }

    private void open() {
        state = State.OPEN;
        openedAtMillis = clockMillis.getAsLong();
        consecutiveFailures = failureThreshold;
        halfOpenAttempts = 0;
    }
}
