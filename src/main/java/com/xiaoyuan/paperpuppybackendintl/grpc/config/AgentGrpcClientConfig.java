package com.xiaoyuan.paperpuppybackendintl.grpc.config;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Agent gRPC Client 配置（ManagedChannel + Stub）
 */
@Configuration
@RequiredArgsConstructor
public class AgentGrpcClientConfig {

    private final AgentGrpcProperties properties;

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel agentManagedChannel() {
        String host = properties.getHost();
        // V1 BYOK sends a transient provider key through this channel. Until
        // TLS/mTLS is configured, refuse any non-loopback Agent destination.
        if (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
            throw new IllegalStateException("BYOK Agent gRPC requires a loopback host");
        }
        return NettyChannelBuilder.forAddress("127.0.0.1", properties.getPort())
                .usePlaintext()
                .idleTimeout(5, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    public AgentServiceGrpc.AgentServiceStub agentServiceStub(ManagedChannel agentManagedChannel) {
        return AgentServiceGrpc.newStub(agentManagedChannel);
    }
}

