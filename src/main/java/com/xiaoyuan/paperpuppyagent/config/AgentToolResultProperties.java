package com.xiaoyuan.paperpuppyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 工具结果回灌给模型时的上下文护栏配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.loop.tool-result")
@Data
public class AgentToolResultProperties {

    /**
     * 是否将超大工具结果压缩后再回灌给模型。
     */
    private boolean compactForModel = true;

    /**
     * 工具结果序列化后超过该字符数，才会启用模型可见 compact。
     */
    private int modelVisibleMaxChars = 8000;

    /**
     * 文本型字段进入 compact envelope 时的单字段预览长度。
     */
    private int textPreviewChars = 3000;

    /**
     * 数组型字段进入 compact envelope 时最多保留的前 N 项。
     */
    private int arrayPreviewItems = 8;

}
