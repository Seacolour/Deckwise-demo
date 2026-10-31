package com.xiaoyuan.paperpuppyagent.grpc.server;

import com.xiaoyuan.paperpuppyagent.grpc.server.service.AgentServiceGrpcImpl;
import io.grpc.Server;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 启动 Agent gRPC Server（对外主入口）。
 *
 * <p>
 * 说明：当前项目没有引入第三方 grpc-spring-boot-starter，因此用 SmartLifecycle 自行托管。
 * </p>
 */
@Slf4j
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private final int port;
    private final boolean enabled;
    private final long shutdownGraceMs;
    private final long shutdownNowWaitMs;
    private final AgentServiceGrpcImpl agentServiceGrpc;
    private final com.xiaoyuan.paperpuppyagent.common.aop.GrpcLogInterceptor grpcLogInterceptor;

    private final HealthStatusManager healthStatusManager = new HealthStatusManager();

    private volatile Server server;
    private volatile ExecutorService executor;
    private volatile Thread blockerThread;
    private volatile boolean running = false;

    public GrpcServerLifecycle(
            @Value("${grpc.port:41051}") int port,
            @Value("${grpc.enabled:true}") boolean enabled,
            @Value("${grpc.shutdown.grace-ms:10000}") long shutdownGraceMs,
            @Value("${grpc.shutdown.now-wait-ms:2000}") long shutdownNowWaitMs,
            AgentServiceGrpcImpl agentServiceGrpc,
            com.xiaoyuan.paperpuppyagent.common.aop.GrpcLogInterceptor grpcLogInterceptor) {
        this.port = port;
        this.enabled = enabled;
        this.shutdownGraceMs = shutdownGraceMs;
        this.shutdownNowWaitMs = shutdownNowWaitMs;
        this.agentServiceGrpc = agentServiceGrpc;
        this.grpcLogInterceptor = grpcLogInterceptor;
        log.info("GrpcServerLifecycle initialized: port={}, enabled={}", port, enabled);
    }

    @Override
    public void start() {
        if (running) {
            log.warn("gRPC Server already running, skip start");
            return;
        }
        if (!enabled) {
            log.warn("gRPC Server disabled (grpc.enabled=false), skip start");
            return;
        }

        log.info("Starting Agent gRPC Server on 127.0.0.1:{}...", port);
        try {
            ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor();
            this.executor = ex;
            server = NettyServerBuilder.forAddress(localBindAddress(port))
                    .executor(ex)
                    .maxInboundMessageSize(16 * 1024 * 1024)
                    .intercept(grpcLogInterceptor) // 日志拦截器
                    .addService(agentServiceGrpc)
                    .addService(healthStatusManager.getHealthService()) // gRPC Health 探针
                    .build()
                    .start();
            running = true;

            // 标记服务就绪（grpc-health-probe / Docker HEALTHCHECK 依赖此状态）
            healthStatusManager.setStatus("", ServingStatus.SERVING);
            log.info("Agent gRPC Server started on 127.0.0.1:{} (service: paperpuppy.agent.AgentService, health: SERVING)", port);

            // 启动阻塞线程，等待服务器终止
            Thread t = new Thread(() -> {
                try {
                    server.awaitTermination();
                    log.info("Agent gRPC Server terminated");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Agent gRPC Server termination interrupted");
                }
            }, "agent-grpc-server-blocker");
            t.setDaemon(false);
            this.blockerThread = t;
            t.start();

        } catch (IOException e) {
            log.error("Failed to start Agent gRPC Server on port {}: {}", port, e.getMessage(), e);
            throw new IllegalStateException("Failed to start Agent gRPC server on port " + port, e);
        }
    }

    static InetSocketAddress localBindAddress(int port) {
        // Chat requests may contain transient BYOK credentials. Plaintext gRPC must never be
        // reachable from another host; a future remote deployment must use authenticated TLS.
        return new InetSocketAddress("127.0.0.1", port);
    }

    @Override
    public void stop() {
        if (!running) {
            log.debug("gRPC Server not running, skip stop");
            return;
        }

        log.info("Stopping Agent gRPC Server...");
        healthStatusManager.setStatus("", ServingStatus.NOT_SERVING);
        Server s = this.server;
        if (s != null) {
            s.shutdown();
            try {
                boolean ok = s.awaitTermination(Math.max(shutdownGraceMs, 0), TimeUnit.MILLISECONDS);
                if (!ok) {
                    log.warn("gRPC Server did not terminate gracefully within {}ms, forcing shutdown", shutdownGraceMs);
                    s.shutdownNow();
                    s.awaitTermination(Math.max(shutdownNowWaitMs, 0), TimeUnit.MILLISECONDS);
                } else {
                    log.info("Agent gRPC Server stopped gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while stopping gRPC Server, forcing shutdown");
                s.shutdownNow();
            }
        }
        running = false;

        Thread t = this.blockerThread;
        this.blockerThread = null;
        if (t != null) {
            t.interrupt();
        }

        ExecutorService ex = this.executor;
        this.executor = null;
        if (ex != null) {
            try {
                ex.close(); // Java 21: ExecutorService 继承 AutoCloseable
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }
}
