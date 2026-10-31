package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.grpc.client.ChatGrpcCall;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatExecutionLifecycleTest {

    @Test
    void acquireRenewAndFinishUseTheSameLockToken() {
        RedisClient redisClient = mock(RedisClient.class);
        AgentRunPersistenceRepository persistenceRepository = mock(AgentRunPersistenceRepository.class);
        when(redisClient.setIfAbsent(anyString(), anyString(), eq(75L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(redisClient.pExpireIfValue(anyString(), anyString(), eq(75L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(redisClient.deleteIfValue(anyString(), anyString())).thenReturn(true);

        ChatExecutionLifecycle lifecycle = new ChatExecutionLifecycle(
                redisClient, persistenceRepository);
        Optional<ChatExecutionLifecycle.Handle> acquired = lifecycle.tryAcquire(7L, 101L);

        assertTrue(acquired.isPresent());
        ChatExecutionLifecycle.Handle handle = acquired.orElseThrow();
        lifecycle.prepareForStart(handle);
        assertTrue(lifecycle.renewLock(handle));
        AtomicBoolean renewStop = new AtomicBoolean(false);
        lifecycle.finishResources(
                handle,
                renewStop,
                "completed");
        lifecycle.finishResources(
                handle,
                renewStop,
                "completed");

        assertTrue(renewStop.get());
        verify(redisClient).delete(handle.abortKey());
        verify(redisClient).pExpireIfValue(handle.lockKey(), handle.lockToken(), 75L, TimeUnit.SECONDS);
        verify(redisClient).deleteIfValue(handle.lockKey(), handle.lockToken());
    }

    @Test
    void abortMarksRedisClosesRunAndCancelsTheRegisteredCall() {
        RedisClient redisClient = mock(RedisClient.class);
        AgentRunPersistenceRepository persistenceRepository = mock(AgentRunPersistenceRepository.class);
        ChatExecutionLifecycle lifecycle = new ChatExecutionLifecycle(
                redisClient, persistenceRepository);
        ChatGrpcCall call = mock(ChatGrpcCall.class);
        ChatExecutionLifecycle.Handle handle = new ChatExecutionLifecycle.Handle(
                7L, 101L, "lock", "token", "7:101", "abort");
        lifecycle.registerCall(handle, call);
        when(persistenceRepository.markLatestRunningRunCancelled(7L, 101L, "abort_requested"))
                .thenReturn(1);

        lifecycle.abort(7L, 101L);

        verify(redisClient).set(anyString(), eq("1"), eq(2L), eq(TimeUnit.MINUTES));
        verify(persistenceRepository).markLatestRunningRunCancelled(7L, 101L, "abort_requested");
        verify(call).cancel("abort requested");
    }

}
