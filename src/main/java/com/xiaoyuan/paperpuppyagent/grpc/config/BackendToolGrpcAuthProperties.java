package com.xiaoyuan.paperpuppyagent.grpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-layer credentials used for Agent -> Backend Tool gRPC calls.
 */
@ConfigurationProperties(prefix = "backend-tool.auth")
public record BackendToolGrpcAuthProperties(
        boolean enabled,
        String serviceToken) {

    public String requiredServiceToken() {
        String token = serviceToken == null ? "" : serviceToken.strip();
        if (enabled && token.isEmpty()) {
            throw new IllegalStateException(
                    "backend-tool.auth.service-token is required when Backend Tool gRPC auth is enabled");
        }
        return token;
    }
}
