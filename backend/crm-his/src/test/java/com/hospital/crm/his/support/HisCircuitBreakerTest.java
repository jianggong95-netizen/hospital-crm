package com.hospital.crm.his.support;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HisCircuitBreakerTest {

    @Test
    void opensAfterFailureThresholdAndRejectsQueries() {
        AtomicLong clock = new AtomicLong(1000);
        HisCircuitBreaker breaker = new HisCircuitBreaker(2, 10_000, 1, clock::get);

        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();
        assertEquals(HisCircuitBreaker.State.CLOSED, breaker.state());

        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();
        assertEquals(HisCircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());

        clock.addAndGet(10_000);
        assertTrue(breaker.tryAcquire());
        assertEquals(HisCircuitBreaker.State.HALF_OPEN, breaker.state());
    }

    @Test
    void successfulHalfOpenProbeClosesCircuit() {
        AtomicLong clock = new AtomicLong(0);
        HisCircuitBreaker breaker = new HisCircuitBreaker(1, 5_000, 1, clock::get);

        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();
        assertEquals(HisCircuitBreaker.State.OPEN, breaker.state());

        clock.addAndGet(5_000);
        assertTrue(breaker.tryAcquire());
        assertEquals(HisCircuitBreaker.State.HALF_OPEN, breaker.state());
        breaker.recordSuccess();

        assertEquals(HisCircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(0, breaker.consecutiveFailures());
        assertTrue(breaker.tryAcquire());
    }

    @Test
    void failedHalfOpenProbeReopensCircuit() {
        AtomicLong clock = new AtomicLong(0);
        HisCircuitBreaker breaker = new HisCircuitBreaker(1, 5_000, 1, clock::get);

        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();
        clock.addAndGet(5_000);
        assertTrue(breaker.tryAcquire());
        breaker.recordFailure();

        assertEquals(HisCircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());

        clock.addAndGet(4_999);
        assertFalse(breaker.tryAcquire());
        clock.addAndGet(1);
        assertTrue(breaker.tryAcquire());
    }
}
