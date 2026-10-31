package com.xiaoyuan.paperpuppyagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent loop 内部工作记忆 MVP 配置。
 *
 * <p>当前阶段只保存单次 run 内的工具结果引用，run 结束即清理，不做跨请求恢复。</p>
 */
@Component
@ConfigurationProperties(prefix = "agent.loop.work-memory")
@Data
public class AgentWorkMemoryProperties {

    /**
     * 是否启用 run-scoped 工具结果存储和 agent_tool_result_open。
     */
    private boolean enabled = true;

    /**
     * 单个 run 最多保留多少条工具结果。
     */
    private int maxResultsPerRun = 80;

    /**
     * 单条工具结果最多保留的序列化字符数，超过则不保存 raw result。
     */
    private int maxStoredResultChars = 1_000_000;

    /**
     * agent_tool_result_open 默认返回字符上限。
     */
    private int defaultOpenMaxChars = 4_000;

    /**
     * agent_tool_result_open 单次返回硬上限。
     */
    private int maxOpenChars = 8_000;

    /**
     * summary 模式下数组最多预览项数。
     */
    private int summaryArrayPreviewItems = 6;

    /**
     * 每轮注入模型的 LedgerView 字符上限。
     */
    private int maxLedgerViewChars = 6_000;

    /**
     * 是否把工具轮中已经流式发给用户的 assistant message 继续保留在下一轮 loop input 中。
     *
     * <p>默认关闭：这些 message 多是进度说明，保留会造成每轮上下文线性增大；
     * reasoning/function_call/function_call_output 仍会保留，以满足 Responses API 工具链路。</p>
     */
    private boolean retainAssistantMessagesInLoopHistory = false;

    /**
     * 是否启用 loop history compaction。
     *
     * <p>启用后，AgentRunner 会在当前 run 内移除较旧且已完成的工具交换块
     * (reasoning/function_call/function_call_output)，保留 LedgerView、最近工具轮和
     * agent_tool_result_open 可重开的工具结果引用。</p>
     */
    private boolean loopHistoryCompactionEnabled = true;

    /**
     * loop input 中最多保留最近多少个已完成工具交换块。
     */
    private int retainedToolTurnsInLoopHistory = 4;

    /**
     * Ledger 中最多保留的 todo 数。
     */
    private int maxLedgerTodos = 12;

    /**
     * Ledger 中最多保留的事实数。
     */
    private int maxLedgerFacts = 12;

    /**
     * Ledger 中最多保留的决策数。
     */
    private int maxLedgerDecisions = 8;

    /**
     * Ledger 中最多保留的 artifact/revision 数。
     */
    private int maxLedgerArtifacts = 12;

    /**
     * LedgerView 中最多展示的最近工具调用数。
     */
    private int maxLedgerRecentToolCalls = 8;

    /**
     * LedgerView 中最多展示的重复读取复用提示数。
     */
    private int maxLedgerReuseHints = 6;

    /**
     * agent_ledger_update 单次最多接收多少条更新。
     */
    private int maxLedgerUpdateItems = 12;

    /**
     * Ledger 中单个文本字段的最大字符数。
     */
    private int maxLedgerTextChars = 500;

    /**
     * 单次 run 内模型调用软上限。达到后只做 LedgerView 提示，不在当前阶段硬拦截。
     */
    private int softMaxModelCallsPerRun = 8;

    /**
     * 单次 run 内工具调用软上限。
     */
    private int softMaxToolCallsPerRun = 24;

    /**
     * 单次 run 内 agent_tool_result_open 调用软上限。
     */
    private int softMaxToolResultOpenCallsPerRun = 6;

    /**
     * 到达软上限百分比后开始提示模型收口。
     */
    private int budgetWarningPercent = 75;

    /**
     * 是否将 Agent run/model/tool/ledger 事实 best-effort 上报到 backend 持久化表。
     *
     * <p>这是阶段三 A 开关。关闭时保留阶段二 run-scoped 内存行为。</p>
     */
    private boolean persistenceEnabled = false;

    /**
     * 单条工具结果最多上报多少字符到 backend agent_tool_results。
     */
    private int maxPersistedToolResultChars = 200_000;

    /**
     * 阶段三 A：运行中 ledger snapshot 的最小 flush 间隔。
     *
     * <p>每 N 次工具调用最多上报一次，run 结束时仍会强制上报最终快照。
     * 设为 1 可恢复为每轮工具后都上报。</p>
     */
    private int ledgerSnapshotFlushToolCallInterval = 4;
}
