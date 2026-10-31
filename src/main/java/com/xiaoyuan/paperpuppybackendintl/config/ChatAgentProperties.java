package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Seacolour
 * @date 2026/1/16 11:02
 * @description:
 */
@Data
@ConfigurationProperties(prefix = "chat.agent")
public class ChatAgentProperties {
    private boolean enabled = true;
}
