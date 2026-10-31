package com.xiaoyuan.paperpuppybackendintl.service.session.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AgentEvent -> SSE 的桥接层。
 * <p>
 * 新协议（推荐）：每种 {@link AgentEventType} 使用独立的 SSE event 名称（小写），并携带统一 envelope：
 * {type, seq, ts, payload}；同时将 SSE id 设置为 seq（用于前端排序/断线续传）。
 * </p>
 */
@Slf4j
public class AgentSseBridge {

    private final SseEventDispatcher dispatcher;

    public AgentSseBridge(SseEmitter emitter, int queueCapacity, TaskExecutor executor) {
        Objects.requireNonNull(emitter, "emitter");
        Objects.requireNonNull(executor, "executor");
        this.dispatcher = new SseEventDispatcher(emitter, queueCapacity);
        this.dispatcher.start(executor);
    }

    public boolean isConnected() {
        return dispatcher.isConnected();
    }

    public void markDisconnected() {
        dispatcher.markDisconnected();
    }

    public void requestStopGracefully() {
        dispatcher.requestStopGracefully();
    }

    public int pending() {
        return dispatcher.pending();
    }

    public void stopNow() {
        dispatcher.stop();
    }

    public void awaitStop(long timeout, TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
        dispatcher.awaitStop(timeout, timeUnit);
    }

    /**
     * 尝试发送事件。若队列满/断线，返回 false。
     */
    public boolean tryEmit(AgentEvent ev) {
        if (ev == null) {
            return true;
        }
        String eventName = ev.type() != null ? ev.type().name().toLowerCase() : "unknown";
        String eventId = ev.seq() > 0 ? String.valueOf(ev.seq()) : "";
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", ev.type() != null ? ev.type().name().toLowerCase() : null);
        envelope.put("seq", ev.seq());
        envelope.put("ts", ev.ts() != null ? ev.ts().toString() : null);
        envelope.put("payload", ev.payload());
        return dispatcher.tryEmit(eventId, eventName, envelope);
    }
}


