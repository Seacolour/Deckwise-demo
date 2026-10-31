package com.xiaoyuan.paperpuppybackendintl.grpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Application-layer authentication for Agent -> Backend Tool gRPC calls.
 */
@ConfigurationProperties(prefix = "grpc.agent-tool.server.auth")
public record AgentToolGrpcAuthProperties(
        boolean enabled,
        String serviceToken,
        String previousServiceToken) {

    public String requiredServiceToken() {
        String token = serviceToken == null ? "" : serviceToken.strip();
        if (enabled && token.isEmpty()) {
            throw new IllegalStateException(
                    "grpc.agent-tool.server.auth.service-token is required when Agent Tool gRPC auth is enabled");
        }
        return token;
    }

    public List<String> acceptedServiceTokens() {
        String current = requiredServiceToken();
        if (!enabled) {
            return List.of();
        }
        String previous = previousServiceToken == null ? "" : previousServiceToken.strip();
        if (previous.isEmpty() || previous.equals(current)) {
            return List.of(current);
        }
        return List.of(current, previous);
    }
}
