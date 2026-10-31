package com.xiaoyuan.paperpuppyagent.grpc.config;

import com.xiaoyuan.paperpuppyagent.grpc.backend.AgentToolServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ProxiedSocketAddress;
import io.grpc.ProxyDetector;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Backend Tool gRPC Client 配置（ManagedChannel + Stub）。
 */
@Configuration
@EnableConfigurationProperties({BackendToolGrpcProperties.class, BackendToolGrpcAuthProperties.class})
public class BackendToolGrpcClientConfig {

    @Bean
    public BackendToolAuthenticationInterceptor backendToolAuthenticationInterceptor(
            BackendToolGrpcAuthProperties authProperties) {
        return new BackendToolAuthenticationInterceptor(authProperties);
    }

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel backendToolManagedChannel(BackendToolGrpcProperties props) {
        String host = Objects.toString(props.host(), "127.0.0.1").trim();
        int port = props.port() > 0 ? props.port() : 40052;

        ProxyDetector noProxyDetector = new ProxyDetector() {
            @Override
            public ProxiedSocketAddress proxyFor(SocketAddress targetServerAddress) throws IOException {
                return null;
            }
        };

        return NettyChannelBuilder.forAddress(host, port)
                .proxyDetector(noProxyDetector)
                .usePlaintext()
                .idleTimeout(5, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    public AgentToolServiceGrpc.AgentToolServiceBlockingStub backendToolServiceStub(
            ManagedChannel backendToolManagedChannel,
            BackendToolAuthenticationInterceptor authenticationInterceptor) {
        return AgentToolServiceGrpc.newBlockingStub(backendToolManagedChannel)
                .withInterceptors(authenticationInterceptor);
    }
}
