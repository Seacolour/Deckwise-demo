package com.xiaoyuan.paperpuppyagent.core.tool;

/**
 * 工具执行上下文：封装当前请求的身份信息。
 *
 * <p>
 * 由 AgentRunner 从 AgentChatRequest 构建，传递给每个工具的 execute() 方法。
 * 工具内部使用这些值调用下游服务，确保不可被用户提示词注入篡改。
 * </p>
 *
 * <p>
 * 这是一个不可变的 record，工具只能读取，不能修改。
 * </p>
 *
 * @param userId    当前用户 ID（从请求强制注入，不可被 AI 操纵）
 * @param projectId 当前项目 ID（从请求强制注入，不可被 AI 操纵）
 * @param sessionId 当前会话 ID（从请求强制注入，不可被 AI 操纵）
 * @param agentRunId 当前 Agent run ID（由 AgentRunner 生成，用于 run-scoped 工作记忆）
 * @param toolCallId 当前模型 function call ID（每次调用时由执行器注入）
 */
public record ToolExecutionContext(
        long userId,
        long projectId,
        long sessionId,
        String agentRunId,
        String toolCallId) {

    public ToolExecutionContext(long userId, long projectId, long sessionId, String agentRunId) {
        this(userId, projectId, sessionId, agentRunId, "");
    }

    public ToolExecutionContext(long userId, long projectId, long sessionId) {
        this(userId, projectId, sessionId, "", "");
    }

    public ToolExecutionContext withToolCallId(String nextToolCallId) {
        return new ToolExecutionContext(userId, projectId, sessionId, agentRunId,
                nextToolCallId == null ? "" : nextToolCallId);
    }

    /**
     * 创建一个空的上下文（仅用于测试或工具不需要上下文的场景）。
     */
    public static ToolExecutionContext empty() {
        return new ToolExecutionContext(0, 0, 0, "", "");
    }
}
