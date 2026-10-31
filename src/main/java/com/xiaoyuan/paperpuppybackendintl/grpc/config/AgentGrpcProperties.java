package com.xiaoyuan.paperpuppybackendintl.grpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent gRPC 服务配置（对外主入口）。
 */
@ConfigurationProperties(prefix = "grpc.agent.server")
@Data
public class AgentGrpcProperties {
    private String host;
    private int port;
    private boolean plaintext;
    private long deadlineMs;
}

