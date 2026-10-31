package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.grpc.client.ChatGrpcCall;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the cross-callback lifecycle state for one Backend chat execution.
 *
 * <p>The lifecycle is deliberately transport-agnostic: SSE event ordering stays in the stream
 * coordinator, while this service owns Redis lock/abort state, in-flight gRPC cancellation,
 * and Backend run closeout.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatExecutionLifecycle {

    static final long LOCK_TTL_SECONDS = 75L;
    private static final String INSTANCE_ID = UUID.randomUUID().toString();

    private final RedisClient redisClient;
    private final AgentRunPersistenceRepository agentRunPersistenceRepository;
    private final Map<String, ChatGrpcCall> inFlightCalls = new ConcurrentHashMap<>();

    public record Handle(Long userId,
                         Long sessionId,
                         String lockKey,
                         String lockToken,
                         String callKey,
                         String abortKey) {
    }

    public Optional<Handle> tryAcquire(Long userId, Long sessionId) {
        String lockKey = RedisKey.chatLock(userId, sessionId);
        String lockToken = INSTANCE_ID + ":" + UUID.randomUUID();
        String callKey = callKey(userId, sessionId);
        boolean locked = redisClient.setIfAbsent(lockKey, lockToken, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (locked) {
            log.info("chat lock acquired: userId={}, sessionId={}, lockKey={}", userId, sessionId, lockKey);
        } else if (!inFlightCalls.containsKey(callKey)) {
            Long ttl = redisClient.ttl(lockKey, TimeUnit.SECONDS);
            String existingValue = redisClient.get(lockKey);
            boolean sameInstance = existingValue != null && existingValue.startsWith(INSTANCE_ID + ":");
            if (sameInstance) {
                boolean deleted = redisClient.deleteKey(lockKey);
                log.warn("stale chat lock detected (same instance), force delete and retry: userId={}, sessionId={}, ttlSec={}, existingVal={}, deleted={}",
                        userId, sessionId, ttl, existingValue, deleted);
                locked = redisClient.setIfAbsent(lockKey, lockToken, LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            } else {
                log.warn("chat lock exists but inFlight missing; skip force delete to avoid cross-instance unlock: userId={}, sessionId={}, ttlSec={}, existingVal={}",
                        userId, sessionId, ttl, existingValue);
            }
        }
        if (!locked) {
            log.info("chat lock busy: userId={}, sessionId={}, lockKey={}", userId, sessionId, lockKey);
            return Optional.empty();
        }
        return Optional.of(new Handle(
                userId,
                sessionId,
                lockKey,
                lockToken,
                callKey,
                RedisKey.chatAbort(userId, sessionId)));
    }

    public void prepareForStart(Handle handle) {
        redisClient.delete(handle.abortKey());
        inFlightCalls.remove(handle.callKey());
    }

    public void releaseAfterPreparationFailure(Handle handle) {
        releaseLock(handle, "prepare_failed");
    }

    public boolean isAbortRequested(Handle handle) {
        return handle != null && redisClient.exists(handle.abortKey());
    }

    public boolean renewLock(Handle handle) {
        return redisClient.pExpireIfValue(
                handle.lockKey(), handle.lockToken(), LOCK_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public Long lockTtlSeconds(Handle handle) {
        return redisClient.ttl(handle.lockKey(), TimeUnit.SECONDS);
    }

    public void registerCall(Handle handle, ChatGrpcCall call) {
        if (handle != null && call != null) {
            inFlightCalls.put(handle.callKey(), call);
        }
    }

    public void abort(Long userId, Long sessionId) {
        if (userId == null || sessionId == null || sessionId <= 0) {
            return;
        }
        log.info("chat abort requested: userId={}, sessionId={}", userId, sessionId);
        redisClient.set(RedisKey.chatAbort(userId, sessionId), "1", 2, TimeUnit.MINUTES);
        markLatestAgentRunCancelled(userId, sessionId, "abort_requested");

        ChatGrpcCall call = inFlightCalls.get(callKey(userId, sessionId));
        if (call != null) {
            try {
                call.cancel("abort requested");
                log.info("chat abort: in-flight gRPC cancelled: userId={}, sessionId={}", userId, sessionId);
            } catch (Exception ignored) {
            }
        } else {
            log.info("chat abort: no in-flight gRPC found (may be other instance): userId={}, sessionId={}",
                    userId, sessionId);
        }
    }

    public void finishResources(
            Handle handle,
            AtomicBoolean renewStop,
            String endReason) {
        if (!renewStop.compareAndSet(false, true)) {
            return;
        }
        try {
            inFlightCalls.remove(handle.callKey());
            if (shouldBackendCloseAgentRun(endReason)) {
                markLatestAgentRunCancelled(handle.userId(), handle.sessionId(), endReason);
            }
            releaseLock(handle, endReason);
        } catch (Exception exception) {
            log.warn("finish chat lifecycle error: {}", exception.getMessage());
        }
    }

    private void releaseLock(Handle handle, String endReason) {
        boolean released = redisClient.deleteIfValue(handle.lockKey(), handle.lockToken());
        log.info("chat lock release: userId={}, sessionId={}, lockKey={}, released={}, endReason={}",
                handle.userId(), handle.sessionId(), handle.lockKey(), released, endReason);
        if (!released && redisClient.exists(handle.lockKey())) {
            Long ttl = redisClient.ttl(handle.lockKey(), TimeUnit.SECONDS);
            String existingValue = redisClient.get(handle.lockKey());
            log.warn("chat lock release failed (token mismatch?): userId={}, sessionId={}, ttlSec={}, expectedToken={}, existingVal={}",
                    handle.userId(), handle.sessionId(), ttl, handle.lockToken(), existingValue);
        }
    }

    private void markLatestAgentRunCancelled(Long userId, Long sessionId, String reason) {
        if (userId == null || sessionId == null || userId <= 0 || sessionId <= 0) {
            return;
        }
        try {
            int updated = agentRunPersistenceRepository.markLatestRunningRunCancelled(userId, sessionId, reason);
            if (updated > 0) {
                log.info("Agent run backend closeout applied: userId={}, sessionId={}, reason={}, updated={}",
                        userId, sessionId, reason, updated);
            }
        } catch (Exception exception) {
            log.warn("Agent run backend closeout failed: userId={}, sessionId={}, reason={}, error={}",
                    userId, sessionId, reason, exception.getMessage());
        }
    }

    private static boolean shouldBackendCloseAgentRun(String endReason) {
        return switch (Objects.toString(endReason, "")) {
            case "abort", "sse_disconnected", "renew_cap" -> true;
            default -> false;
        };
    }

    private static String callKey(Long userId, Long sessionId) {
        return userId + ":" + sessionId;
    }
}
