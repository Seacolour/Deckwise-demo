package com.xiaoyuan.paperpuppyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI Responses context_management 自动 compaction 灰度配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.loop.openai-context-management")
@Data
public class AgentOpenAiContextManagementProperties {

    /**
     * 是否在 Agent loop 的 Responses create 请求中启用官方 context_management compaction。
     */
    private boolean enabled = false;

    /**
     * 启用 context_management 时传给 OpenAI 的 store 值。
     *
     * <p>官方 compaction 可与 store=false 配合使用；默认关闭存储，保持 provider-side state 最小化。</p>
     */
    private boolean store = false;

    /**
     * 触发自动 compaction 的 token 阈值。小于等于 0 时只设置 type=compaction，不设置阈值。
     */
    private long compactThreshold = 180_000L;

    /**
     * 是否将捕获到的 compacted window best-effort 上报 backend 持久化。
     */
    private boolean persistCompactedWindow = true;
}
