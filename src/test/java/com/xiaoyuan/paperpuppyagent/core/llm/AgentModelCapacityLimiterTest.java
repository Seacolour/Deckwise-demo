package com.xiaoyuan.paperpuppyagent.core.llm;

import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AgentModelCapacityLimiterTest {

    @Test
    void disabledLimiterReturnsNoopReservation() {
        AgentModelCallProperties props = baseProps();
        props.setCapacityLimiterEnabled(false);
        AgentModelCapacityLimiter limiter = new AgentModelCapacityLimiter(props);

        AgentModelCapacityLimiter.Reservation reservation =
                limiter.acquire(request("gpt-5.4-mini", 500), () -> false);

        assertTrue(reservation.noop());
        assertEquals(0, reservation.reservedTokens());
    }

    @Test
    void waitsForConcurrentSlotAndContinuesAfterRelease() throws Exception {
        AgentModelCallProperties props = baseProps();
        props.setCapacityMaxConcurrent(1);
        props.setCapacityMaxQueueMs(1_000);
        AgentModelCapacityLimiter limiter = new AgentModelCapacityLimiter(props);

        AgentModelCapacityLimiter.Reservation first =
                limiter.acquire(request("gpt-5.4-mini", 100), () -> false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AgentModelCapacityLimiter.Reservation> second = executor.submit(() ->
                    limiter.acquire(request("gpt-5.4-mini", 100), () -> false));

            Thread.sleep(100);
            assertFalse(second.isDone(), "second request should wait while concurrency slot is occupied");

            limiter.release(first, 100L, true, "");
            AgentModelCapacityLimiter.Reservation acquired = second.get(1, TimeUnit.SECONDS);
            assertFalse(acquired.noop());
            assertTrue(acquired.waitedMs() >= 50, "second request should have queued before admission");
            limiter.release(acquired, 100L, true, "");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timesOutWhenCapacityDoesNotOpen() {
        AgentModelCallProperties props = baseProps();
        props.setCapacityMaxConcurrent(1);
        props.setCapacityMaxQueueMs(80);
        AgentModelCapacityLimiter limiter = new AgentModelCapacityLimiter(props);

        AgentModelCapacityLimiter.Reservation first =
                limiter.acquire(request("gpt-5.4-mini", 100), () -> false);
        try {
            assertThrows(AgentModelCapacityLimiter.CapacityTimeoutException.class,
                    () -> limiter.acquire(request("gpt-5.4-mini", 100), () -> false));
        } finally {
            limiter.release(first, 100L, true, "");
        }
    }

    @Test
    void releaseCommitsActualUsageAndFreesOverEstimatedHeadroom() {
        AgentModelCallProperties props = baseProps();
        props.setCapacitySafeTpmLimit(2_000);
        props.setCapacityMaxConcurrent(10);
        AgentModelCapacityLimiter limiter = new AgentModelCapacityLimiter(props);

        AgentModelCapacityLimiter.Reservation first =
                limiter.acquire(request("gpt-5.5", 1_900), () -> false);
        limiter.release(first, 100L, true, "");

        AgentModelCapacityLimiter.Snapshot snapshot = limiter.snapshot("openai", "gpt-5.5");
        assertEquals(100L, snapshot.committedTokens());

        AgentModelCapacityLimiter.Reservation second =
                limiter.acquire(request("gpt-5.5", 1_900), () -> false);
        assertFalse(second.noop());
        limiter.release(second, 1_900L, true, "");
    }

    private static AgentModelCallProperties baseProps() {
        AgentModelCallProperties props = new AgentModelCallProperties();
        props.setCapacityLimiterEnabled(true);
        props.setCapacityWindowMs(1_000);
        props.setCapacitySafeTpmLimit(10_000);
        props.setCapacityRpmLimit(0);
        props.setCapacityMaxConcurrent(10);
        props.setCapacityMaxQueueMs(500);
        props.setCapacityWaitTickMs(25);
        props.setCapacityCharsPerToken(1.0D);
        props.setCapacityMinReservedTokens(0);
        props.setCapacityMaxReservedTokens(0);
        return props;
    }

    private static AgentModelCapacityLimiter.CapacityRequest request(String model, long estimatedTokens) {
        return new AgentModelCapacityLimiter.CapacityRequest(
                "openai",
                model,
                "test",
                estimatedTokens,
                0,
                0,
                0,
                false,
                1);
    }
}
