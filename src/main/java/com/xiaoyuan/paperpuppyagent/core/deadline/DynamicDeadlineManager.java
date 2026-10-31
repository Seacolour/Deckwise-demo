package com.xiaoyuan.paperpuppyagent.core.deadline;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 动态超时管理器：基于工具调用心跳延长会话超时。
 *
 * <p>
 * 核心思路：
 * <ul>
 * <li>初始截止时间 = 启动时间 + baseThinkingBudgetMs</li>
 * <li>每次工具成功执行后，截止时间 += perToolBudgetMs（但不超过硬上限）</li>
 * <li>任何时刻 totalElapsed > maxTotalCapMs，视为超时</li>
 * </ul>
 *
 * <p>
 * 使用方式：
 * <ol>
 * <li>在 AgentRunner.run() 开始时创建实例</li>
 * <li>每轮循环开始前调用 {@link #isExpired()} 检测</li>
 * <li>工具执行成功后调用 {@link #onToolExecuted()}</li>
 * </ol>
 */
@Slf4j
public class DynamicDeadlineManager {

    private final long baseThinkingBudgetMs;
    private final long perToolBudgetMs;
    private final long maxTotalCapMs;
    private final long startTimeMs;

    /**
     * 绝对截止时间（毫秒时间戳）；原子类保证并发安全。
     */
    private final AtomicLong deadlineMs;

    /**
     * 硬上限绝对时间（毫秒时间戳）。
     */
    private final long hardCapMs;

    /**
     * 会话标识（用于日志）
     */
    private final String sessionKey;

    public DynamicDeadlineManager(long baseThinkingBudgetMs,
            long perToolBudgetMs,
            long maxTotalCapMs,
            String sessionKey) {
        this.baseThinkingBudgetMs = baseThinkingBudgetMs;
        this.perToolBudgetMs = perToolBudgetMs;
        this.maxTotalCapMs = maxTotalCapMs;
        this.startTimeMs = System.currentTimeMillis();
        this.hardCapMs = startTimeMs + maxTotalCapMs;
        // 初始截止时间 = 启动时间 + 基础预算
        this.deadlineMs = new AtomicLong(startTimeMs + baseThinkingBudgetMs);
        this.sessionKey = sessionKey != null ? sessionKey : "unknown";

        log.info("[DynamicDeadline] Created: sessionKey={}, baseBudgetMs={}, perToolMs={}, maxCapMs={}",
                this.sessionKey, baseThinkingBudgetMs, perToolBudgetMs, maxTotalCapMs);
    }

    /**
     * 检测是否已超时。
     *
     * @return true 表示已超时，应停止处理
     */
    public boolean isExpired() {
        long now = System.currentTimeMillis();
        long elapsed = now - startTimeMs;

        // 硬上限检测
        if (now >= hardCapMs) {
            log.warn("[DynamicDeadline] Hard cap exceeded: sessionKey={}, elapsedMs={}, maxCapMs={}",
                    sessionKey, elapsed, maxTotalCapMs);
            return true;
        }

        // 软截止时间检测
        long deadline = deadlineMs.get();
        if (now >= deadline) {
            log.warn("[DynamicDeadline] Deadline exceeded: sessionKey={}, elapsedMs={}, deadlineMs={}",
                    sessionKey, elapsed, deadline - startTimeMs);
            return true;
        }
        return false;
    }

    /**
     * 工具执行成功后调用，延长截止时间。
     */
    public void onToolExecuted() {
        long now = System.currentTimeMillis();
        long elapsed = now - startTimeMs;

        // 原子更新：新截止时间 = max(当前, 当前时间 + perToolBudgetMs)，但不超过硬上限
        long oldDeadline = deadlineMs.accumulateAndGet(
                Math.min(now + perToolBudgetMs, hardCapMs),
                Math::max);

        log.info(
                "[DynamicDeadline] Budget extended: sessionKey={}, elapsedMs={}, newDeadlineMs={}, addedMs={}",
                sessionKey, elapsed, oldDeadline - startTimeMs, perToolBudgetMs);
    }

    /**
     * 获取当前剩余预算（毫秒），用于日志/监控。
     */
    public long getRemainingBudgetMs() {
        long now = System.currentTimeMillis();
        long remaining = deadlineMs.get() - now;
        return Math.max(remaining, 0);
    }

    /**
     * 获取已运行时间（毫秒）。
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * 获取基础思考预算。
     */
    public long getBaseThinkingBudgetMs() {
        return baseThinkingBudgetMs;
    }

    /**
     * 获取每次工具调用增加的预算。
     */
    public long getPerToolBudgetMs() {
        return perToolBudgetMs;
    }

    /**
     * 获取最大硬上限。
     */
    public long getMaxTotalCapMs() {
        return maxTotalCapMs;
    }
}
