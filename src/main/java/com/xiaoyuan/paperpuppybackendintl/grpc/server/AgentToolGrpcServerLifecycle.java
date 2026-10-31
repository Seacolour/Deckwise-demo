package com.xiaoyuan.paperpuppybackendintl.grpc.server;

import com.xiaoyuan.paperpuppybackendintl.aop.GrpcLogInterceptor;
import com.xiaoyuan.paperpuppybackendintl.grpc.server.service.AgentToolServiceGrpcImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Agent Tool gRPC Server 生命周期管理。
 */
@Slf4j
@Component
public class AgentToolGrpcServerLifecycle implements SmartLifecycle {

    private final int port;
    private final boolean enabled;
    private final AgentToolServiceGrpcImpl agentToolService;
    private final GrpcLogInterceptor grpcLogInterceptor;
    private final AgentToolAuthenticationInterceptor authenticationInterceptor;

    private volatile Server server;
    private volatile ExecutorService executor;
    private volatile Thread blockerThread;
    private volatile boolean running = false;

    public AgentToolGrpcServerLifecycle(
            @Value("${grpc.agent-tool.server.port:40052}") int port,
            @Value("${grpc.agent-tool.server.enabled:true}") boolean enabled,
            AgentToolServiceGrpcImpl agentToolService,
            GrpcLogInterceptor grpcLogInterceptor,
            AgentToolAuthenticationInterceptor authenticationInterceptor) {
        this.port = port;
        this.enabled = enabled;
        this.agentToolService = agentToolService;
        this.grpcLogInterceptor = grpcLogInterceptor;
        this.authenticationInterceptor = authenticationInterceptor;
        log.info("AgentToolGrpcServerLifecycle initialized: port={}, enabled={}", port, enabled);
    }

    @Override
    public void start() {
        if (running) {
            log.warn("Agent Tool gRPC Server already running, skip start");
            return;
        }
        if (!enabled) {
            log.warn("Agent Tool gRPC Server disabled, skip start");
            return;
        }

        log.info("Starting Agent Tool gRPC Server on port {}...", port);
        try {
            ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor();
            this.executor = ex;
            server = ServerBuilder.forPort(port)
                    .executor(ex)
                    .maxInboundMessageSize(16 * 1024 * 1024)
                    .intercept(grpcLogInterceptor)
                    .intercept(authenticationInterceptor)
                    .addService(agentToolService)
                    .build()
                    .start();
            running = true;

            log.info("Agent Tool gRPC Server started on port {} (service: paperpuppy.backendintl.agent.tool.AgentToolService)", port);

            // 启动阻塞线程，等待服务器终止
            Thread t = new Thread(() -> {
                try {
                    server.awaitTermination();
                    log.info("Agent Tool gRPC Server terminated");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Agent Tool gRPC Server termination interrupted");
                }
            }, "agent-tool-grpc-server-blocker");
            t.setDaemon(true);
            this.blockerThread = t;
            t.start();

        } catch (IOException e) {
            log.error("Failed to start Agent Tool gRPC Server on port {}: {}", port, e.getMessage(), e);
            throw new IllegalStateException("Failed to start Agent Tool gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (!running) {
            log.debug("Agent Tool gRPC Server not running, skip stop");
            return;
        }

        log.info("Stopping Agent Tool gRPC Server...");
        Server s = this.server;
        if (s != null) {
            s.shutdown();
            try {
                boolean ok = s.awaitTermination(10, TimeUnit.SECONDS);
                if (!ok) {
                    log.warn("Agent Tool gRPC Server did not terminate gracefully, forcing shutdown");
                    s.shutdownNow();
                    s.awaitTermination(2, TimeUnit.SECONDS);
                } else {
                    log.info("Agent Tool gRPC Server stopped gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while stopping Agent Tool gRPC Server, forcing shutdown");
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
                ex.close();
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
