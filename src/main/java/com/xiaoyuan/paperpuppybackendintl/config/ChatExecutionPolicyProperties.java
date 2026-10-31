package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天执行策略默认配置。
 *
 * <p>当前既用于 backend 内部语义收口，也会向 Agent 下传细粒度能力位。</p>
 */
@Data
@ConfigurationProperties(prefix = "chat.execution")
public class ChatExecutionPolicyProperties {
    private boolean allowAgentSkills = true;
}
