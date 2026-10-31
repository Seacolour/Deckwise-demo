package com.xiaoyuan.paperpuppyagent.grpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backend Tool gRPC 客户端配置属性。
 */
@ConfigurationProperties(prefix = "backend-tool")
public record BackendToolGrpcProperties(
        String host,
        int port,
        boolean plaintext,
        long deadlineMs) {
}
