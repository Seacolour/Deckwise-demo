package com.xiaoyuan.paperpuppyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent loop 内部模型调用护栏配置。
 */
@Component
@ConfigurationProperties(prefix = "agent.loop.model-call")
@Data
public class AgentModelCallProperties {

    /**
     * 普通工具循环模型调用的输出上限。小于等于 0 时不显式设置 max_output_tokens。
     */
    private long defaultMaxOutputTokens = 4096L;

    /**
     * 禁用工具的收口调用输出上限。小于等于 0 时不显式设置 max_output_tokens。
     */
    private long noToolsMaxOutputTokens = 4096L;

    /**
     * 是否允许模型在单次响应中发出多个并行 tool_call。
     *
     * <p>开启后可减少多页/多片段读取时的模型 loop 次数；写工具仍由运行时护栏限制为逐步提交。</p>
     */
    private boolean parallelToolCallsEnabled = true;

    /**
     * 当模型在同一轮响应中发出多个写工具时，是否只执行第一个写工具，其余返回顺序执行提示。
     */
    private boolean guardParallelWrites = true;

    /**
     * OpenAI rate limit / retryable 异常的最大重试次数。
     */
    private int maxRetries = 2;

    /**
     * 指数退避初始等待时间。
     */
    private long initialBackoffMs = 250L;

    /**
     * 单次退避最大等待时间。
     */
    private long maxBackoffMs = 2000L;

    /**
     * 每次退避附加的随机抖动上限。
     */
    private long jitterMs = 100L;

    /**
     * 是否启用 Agent 进程内模型调用容量池。
     *
     * <p>第一版仅保护 PaperPuppy-Agent 自己直连 OpenAI 的 loop 热路径；ASK/PlanB 仍由 AI-Intl 后续单独治理。</p>
     */
    private boolean capacityLimiterEnabled = true;

    /**
     * 容量窗口长度。OpenAI TPM/RPM 语义通常按 60s 窗口理解。
     */
    private long capacityWindowMs = 60_000L;

    /**
     * 单模型安全 TPM 上限。每个 model 独立维护窗口池。
     */
    private long capacitySafeTpmLimit = 160_000L;

    /**
     * 单模型 RPM 上限；小于等于 0 表示不限制。
     */
    private int capacityRpmLimit = 300;

    /**
     * 单模型并发 OpenAI 请求上限；小于等于 0 表示不限制。
     */
    private int capacityMaxConcurrent = 6;

    /**
     * 容量不足时单次模型调用最多排队多久。超过后快速失败，避免占住整条 gRPC 会话太久。
     */
    private long capacityMaxQueueMs = 45_000L;

    /**
     * 等待容量时的最大唤醒间隔；release 会主动 notify，这里只是兜底轮询。
     */
    private long capacityWaitTickMs = 250L;

    /**
     * token 粗估：字符数 / charsPerToken + maxOutputTokens。
     */
    private double capacityCharsPerToken = 4.0D;

    /**
     * 单次请求最小预约 token，避免非常短请求在并发场景下完全不占容量。
     */
    private long capacityMinReservedTokens = 1_000L;

    /**
     * 单次请求最大预约 token；小于等于 0 时仅受 capacitySafeTpmLimit 约束。
     */
    private long capacityMaxReservedTokens = 0L;
}
