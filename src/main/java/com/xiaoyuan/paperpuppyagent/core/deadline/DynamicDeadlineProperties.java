package com.xiaoyuan.paperpuppyagent.core.deadline;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 动态超时配置属性。
 */
@Component
@ConfigurationProperties(prefix = "agent.deadline")
@Data
public class DynamicDeadlineProperties {

    /**
     * 基础思考预算（毫秒）：AI 首次回复前的超时容限。
     * 默认 20 秒。
     */
    private long baseThinkingBudgetMs = 20000L;

    /**
     * 每次工具调用成功后增加的预算（毫秒）。
     * 默认 15 秒。
     */
    private long perToolBudgetMs = 15000L;

    /**
     * 最大硬上限（毫秒）：无论工具调用多少次，总时长不超过此值。
     * 默认 120 秒。
     */
    private long maxTotalCapMs = 120000L;
}
