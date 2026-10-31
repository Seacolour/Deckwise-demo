package com.xiaoyuan.paperpuppyagent.core.llm;

import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;

/**
 * Agent 进程内模型调用容量池。
 *
 * <p>第一版按 provider + model 维护独立窗口，用于在 OpenAI 调用前做排队治理，避免高并发下直接打爆 TPM。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModelCapacityLimiter {

    private final AgentModelCallProperties properties;
    private final ConcurrentMap<String, CapacityPool> pools = new ConcurrentHashMap<>();

    public Reservation acquire(CapacityRequest request, BooleanSupplier cancelled) {
        if (!properties.isCapacityLimiterEnabled()) {
            return Reservation.noop(poolKey(request));
        }

        CapacityRequest req = request == null ? CapacityRequest.empty() : request;
        String poolKey = poolKey(req);
        long estimatedTokens = estimateTokens(req);
        long reservedTokens = normalizeReservedTokens(estimatedTokens);
        if (reservedTokens <= 0) {
            return Reservation.noop(poolKey);
        }

        CapacityPool pool = pools.computeIfAbsent(poolKey, CapacityPool::new);
        long startedMs = System.currentTimeMillis();
        long maxQueueMs = Math.max(properties.getCapacityMaxQueueMs(), 0L);
        long deadlineMs = startedMs + maxQueueMs;
        boolean loggedWait = false;

        synchronized (pool) {
            while (true) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new CapacityTimeoutException("model capacity wait cancelled: pool=" + poolKey);
                }
                long now = System.currentTimeMillis();
                pool.purge(now, windowMs());

                AdmitDecision decision = pool.canAdmit(reservedTokens, properties, now, windowMs());
                if (decision.allowed()) {
                    String reservationId = "cap_" + UUID.randomUUID().toString().replace("-", "");
                    ReservationState state = new ReservationState(reservationId, poolKey, reservedTokens, now);
                    pool.active.put(reservationId, state);
                    pool.inFlightReservedTokens += reservedTokens;
                    pool.requestTimestamps.addLast(now);

                    long waitedMs = now - startedMs;
                    if (waitedMs > 0) {
                        log.info("[ModelCapacity] admitted after wait: pool={}, model={}, callSite={}, reservedTokens={}, waitedMs={}, active={}, committedTokens={}, inFlightReservedTokens={}",
                                poolKey,
                                normalizeModel(req.model()),
                                safe(req.callSite(), "unknown"),
                                reservedTokens,
                                waitedMs,
                                pool.active.size(),
                                pool.committedTokens,
                                pool.inFlightReservedTokens);
                    } else {
                        log.debug("[ModelCapacity] admitted: pool={}, reservedTokens={}, active={}, committedTokens={}, inFlightReservedTokens={}",
                                poolKey, reservedTokens, pool.active.size(), pool.committedTokens, pool.inFlightReservedTokens);
                    }
                    return new Reservation(false, reservationId, poolKey, reservedTokens, waitedMs);
                }

                long remainingMs = deadlineMs - now;
                if (remainingMs <= 0) {
                    String message = "model capacity queue timeout: pool=%s, reservedTokens=%d, active=%d, committedTokens=%d, inFlightReservedTokens=%d, reason=%s"
                            .formatted(poolKey, reservedTokens, pool.active.size(), pool.committedTokens, pool.inFlightReservedTokens, decision.reason());
                    log.warn("[ModelCapacity] {}", message);
                    throw new CapacityTimeoutException(message);
                }

                if (!loggedWait) {
                    loggedWait = true;
                    log.info("[ModelCapacity] waiting: pool={}, model={}, callSite={}, reservedTokens={}, reason={}, active={}, committedTokens={}, inFlightReservedTokens={}, maxQueueMs={}",
                            poolKey,
                            normalizeModel(req.model()),
                            safe(req.callSite(), "unknown"),
                            reservedTokens,
                            decision.reason(),
                            pool.active.size(),
                            pool.committedTokens,
                            pool.inFlightReservedTokens,
                            maxQueueMs);
                }

                long waitMs = Math.min(remainingMs, Math.max(1L, decision.retryAfterMs()));
                waitMs = Math.min(waitMs, Math.max(properties.getCapacityWaitTickMs(), 25L));
                try {
                    pool.wait(waitMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CapacityTimeoutException("interrupted while waiting model capacity: pool=" + poolKey, interrupted);
                }
            }
        }
    }

    public void release(Reservation reservation, Long actualTotalTokens, boolean success, String errorType) {
        if (reservation == null || reservation.noop()) {
            return;
        }
        CapacityPool pool = pools.get(reservation.poolKey());
        if (pool == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (pool) {
            ReservationState state = pool.active.remove(reservation.reservationId());
            if (state == null) {
                return;
            }
            pool.inFlightReservedTokens = Math.max(0L, pool.inFlightReservedTokens - state.reservedTokens());
            long committedTokens = actualTotalTokens != null && actualTotalTokens > 0
                    ? actualTotalTokens
                    : state.reservedTokens();
            if (committedTokens > 0) {
                pool.committedTokenEvents.addLast(new TokenEvent(now, committedTokens));
                pool.committedTokens += committedTokens;
            }
            pool.purge(now, windowMs());
            pool.notifyAll();
            log.debug("[ModelCapacity] released: pool={}, reservationId={}, reservedTokens={}, actualTokens={}, committedTokens={}, success={}, errorType={}, active={}, inFlightReservedTokens={}",
                    reservation.poolKey(),
                    reservation.reservationId(),
                    state.reservedTokens(),
                    actualTotalTokens,
                    committedTokens,
                    success,
                    safe(errorType, ""),
                    pool.active.size(),
                    pool.inFlightReservedTokens);
        }
    }

    Snapshot snapshot(String provider, String model) {
        String key = poolKey(new CapacityRequest(provider, model, "snapshot", 0, 0, 0, 0, false, 0));
        CapacityPool pool = pools.get(key);
        if (pool == null) {
            return new Snapshot(key, 0, 0, 0, 0);
        }
        synchronized (pool) {
            pool.purge(System.currentTimeMillis(), windowMs());
            return new Snapshot(key, pool.committedTokens, pool.inFlightReservedTokens, pool.active.size(), pool.requestTimestamps.size());
        }
    }

    private long estimateTokens(CapacityRequest req) {
        long chars = Math.max(req.estimatedInputChars(), 0L)
                + Math.max(req.instructionsChars(), 0L)
                + Math.max(req.toolsChars(), 0L);
        double charsPerToken = properties.getCapacityCharsPerToken();
        if (charsPerToken <= 0D || Double.isNaN(charsPerToken) || Double.isInfinite(charsPerToken)) {
            charsPerToken = 4.0D;
        }
        long inputTokens = (long) Math.ceil(chars / charsPerToken);
        return safeAdd(inputTokens, Math.max(req.maxOutputTokens(), 0L));
    }

    private long normalizeReservedTokens(long estimatedTokens) {
        long reserved = Math.max(estimatedTokens, 0L);
        long min = Math.max(properties.getCapacityMinReservedTokens(), 0L);
        if (min > 0) {
            reserved = Math.max(reserved, min);
        }
        long explicitMax = Math.max(properties.getCapacityMaxReservedTokens(), 0L);
        if (explicitMax > 0) {
            reserved = Math.min(reserved, explicitMax);
        }
        long safeTpm = Math.max(properties.getCapacitySafeTpmLimit(), 0L);
        if (safeTpm > 0 && reserved > safeTpm) {
            log.warn("[ModelCapacity] single reservation exceeds safe TPM; cap reservation: estimatedTokens={}, cappedTokens={}, safeTpm={}",
                    reserved, safeTpm, safeTpm);
            reserved = safeTpm;
        }
        return reserved;
    }

    private long windowMs() {
        return Math.max(properties.getCapacityWindowMs(), 1_000L);
    }

    private static String poolKey(CapacityRequest request) {
        String provider = safe(request == null ? null : request.provider(), "openai").toLowerCase();
        return provider + ":" + normalizeModel(request == null ? null : request.model());
    }

    private static String normalizeModel(String model) {
        String value = safe(model, "unknown").trim().toLowerCase();
        return value.isBlank() ? "unknown" : value;
    }

    private static String safe(String value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return text.isBlank() ? fallback : text;
    }

    private static long safeAdd(long a, long b) {
        if (a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    public record CapacityRequest(
            String provider,
            String model,
            String callSite,
            long estimatedInputChars,
            long instructionsChars,
            long toolsChars,
            long maxOutputTokens,
            boolean priority,
            int attempt) {

        private static CapacityRequest empty() {
            return new CapacityRequest("openai", "unknown", "unknown", 0, 0, 0, 0, false, 0);
        }
    }

    public record Reservation(
            boolean noop,
            String reservationId,
            String poolKey,
            long reservedTokens,
            long waitedMs) {

        private static Reservation noop(String poolKey) {
            return new Reservation(true, "", poolKey, 0, 0);
        }
    }

    record Snapshot(String poolKey, long committedTokens, long inFlightReservedTokens, int activeRequests, int requestWindowCount) {
    }

    public static class CapacityTimeoutException extends RuntimeException {
        public CapacityTimeoutException(String message) {
            super(message);
        }

        public CapacityTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record TokenEvent(long timestampMs, long tokens) {
    }

    private record ReservationState(String reservationId, String poolKey, long reservedTokens, long admittedAtMs) {
    }

    private record AdmitDecision(boolean allowed, long retryAfterMs, String reason) {
        private static AdmitDecision allow() {
            return new AdmitDecision(true, 0, "");
        }
    }

    private static final class CapacityPool {
        private final String key;
        private final Deque<TokenEvent> committedTokenEvents = new ArrayDeque<>();
        private final Deque<Long> requestTimestamps = new ArrayDeque<>();
        private final Map<String, ReservationState> active = new LinkedHashMap<>();
        private long committedTokens;
        private long inFlightReservedTokens;

        private CapacityPool(String key) {
            this.key = key;
        }

        private void purge(long nowMs, long windowMs) {
            long minTimestamp = nowMs - windowMs;
            while (!committedTokenEvents.isEmpty() && committedTokenEvents.peekFirst().timestampMs() <= minTimestamp) {
                TokenEvent expired = committedTokenEvents.removeFirst();
                committedTokens = Math.max(0L, committedTokens - expired.tokens());
            }
            while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() <= minTimestamp) {
                requestTimestamps.removeFirst();
            }
        }

        private AdmitDecision canAdmit(long reservedTokens,
                                       AgentModelCallProperties properties,
                                       long nowMs,
                                       long windowMs) {
            int maxConcurrent = properties.getCapacityMaxConcurrent();
            if (maxConcurrent > 0 && active.size() >= maxConcurrent) {
                return new AdmitDecision(false, 100L, "max_concurrent");
            }

            int rpmLimit = properties.getCapacityRpmLimit();
            if (rpmLimit > 0 && requestTimestamps.size() >= rpmLimit) {
                long retryAfterMs = retryAfterOldest(requestTimestamps.peekFirst(), nowMs, windowMs);
                return new AdmitDecision(false, retryAfterMs, "rpm_limit");
            }

            long safeTpm = properties.getCapacitySafeTpmLimit();
            if (safeTpm > 0) {
                long projectedTokens = committedTokens + inFlightReservedTokens + reservedTokens;
                if (projectedTokens > safeTpm) {
                    long retryAfterMs = retryAfterTokenSlot(reservedTokens, safeTpm, nowMs, windowMs);
                    return new AdmitDecision(false, retryAfterMs, "tpm_limit");
                }
            }
            return AdmitDecision.allow();
        }

        private long retryAfterTokenSlot(long reservedTokens, long safeTpm, long nowMs, long windowMs) {
            long projected = committedTokens + inFlightReservedTokens + reservedTokens;
            if (projected <= safeTpm) {
                return 1L;
            }
            long remaining = projected;
            for (TokenEvent event : committedTokenEvents) {
                remaining -= event.tokens();
                if (remaining <= safeTpm) {
                    return retryAfterOldest(event.timestampMs(), nowMs, windowMs);
                }
            }
            return 100L;
        }

        private long retryAfterOldest(Long timestampMs, long nowMs, long windowMs) {
            if (timestampMs == null) {
                return 100L;
            }
            return Math.max(1L, timestampMs + windowMs - nowMs);
        }

        @Override
        public String toString() {
            return key;
        }
    }
}
