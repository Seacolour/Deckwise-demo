package com.xiaoyuan.paperpuppybackendintl.service.session.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将“生产事件”和“写入 SSE socket”解耦，避免在 gRPC 回调线程里直接 emitter.send() 造成阻塞/堆积。
 *
 * <p>设计目标：</p>
 * <ul>
 *   <li>有界队列：下游慢时不无限堆内存</li>
 *   <li>独立发送线程（虚拟线程）：send 阻塞不会拖死上游回调</li>
 *   <li>一旦检测到客户端断开/发送失败：切换为 disconnected（继续上游业务，但停止发送）</li>
 * </ul>
 */
@Slf4j
public class SseEventDispatcher {

    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    public void awaitStop(long timeout,TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
        finished.get(timeout,timeUnit);
        stopped.set(true);
        queue.clear();
    }

    public record OutboundEvent(String id, String name, Object data) {
        public OutboundEvent {
            id = id == null ? "" : id;
            name = name == null ? "" : name;
        }
    }

    private final SseEmitter emitter;
    private final ArrayBlockingQueue<OutboundEvent> queue;
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public SseEventDispatcher(SseEmitter emitter, int capacity) {
        this.emitter = Objects.requireNonNull(emitter, "emitter");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 启动发送循环（建议用 VirtualThreadTaskExecutor 执行）。
     */
    public void start(TaskExecutor executor) {
        executor.execute(this::runLoop);
    }

    /**
     * 非阻塞入队；若队列满或已断开/停止，则返回 false。
     */
    public boolean tryEmit(String name, Object data) {
        return tryEmit("", name, data);
    }

    /**
     * 非阻塞入队；若队列满或已断开/停止，则返回 false。
     *
     * @param id SSE Last-Event-ID（可为空）
     */
    public boolean tryEmit(String id, String name, Object data) {
        if (!connected.get() || stopped.get()) {
            return false;
        }
        return queue.offer(new OutboundEvent(id, name, data));
    }

    /**
     * SSE 连接断开（completion/timeout/error）时调用：停止发送并尽快释放队列内存。
     *
     * <p>注意：这不等价于“取消上游任务”。上游（gRPC/agent）可继续执行并落库。</p>
     */
    public void markDisconnected() {
        if (connected.compareAndSet(true, false)) {
            queue.clear();
            finished.complete(null);
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public int pending() {
        return queue.size();
    }

    /**
     * 请求“优雅停止”：尽量把队列里已有事件发完再退出循环。
     */
    public void requestStopGracefully() {
        stopRequested.set(true);
    }

    public void stop() {
        stopped.set(true);
        queue.clear();
        finished.complete(null);
    }

    private void runLoop() {
        try{
            while (!stopped.get()) {
                if (!connected.get()) {
                    // 客户端已断开，不再发送
                    return;
                }
                try {
                    OutboundEvent ev = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (ev == null) {
                        if (stopRequested.get() && queue.isEmpty()) return;
                        continue;
                    }
                    try {
                        SseEmitter.SseEventBuilder b = SseEmitter.event().name(ev.name()).data(ev.data());
                        if (ev.id() != null && !ev.id().isBlank()) {
                            b = b.id(ev.id());
                        }
                        emitter.send(b);
                    } catch (Exception sendEx) {
                        // 发送失败通常意味着客户端断开/网络异常：切断发送，不影响上游继续跑
                        log.debug("SSE send failed, mark disconnected: {}", sendEx.getMessage());
                        markDisconnected();
                        return;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    // 兜底：避免发送线程把异常吞掉导致忙循环
                    log.warn("SSE dispatcher loop error: {}", e.getMessage());
                }
            }
        }finally {
            finished.complete(null);
        }
    }
}

