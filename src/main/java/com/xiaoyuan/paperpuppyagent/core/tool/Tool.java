package com.xiaoyuan.paperpuppyagent.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tool（执行体）：仅承载“执行逻辑”，不再承载对模型暴露的描述/schema。
 *
 * <p>对模型暴露的函数签名、参数 schema、描述文本统一由 {@code skills/..../skill.json} 提供。</p>
 */
public abstract class Tool {

    protected final ObjectMapper mapper;

    public Tool(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public abstract String name();

    /**
     * 执行工具逻辑。
     *
     * @param arguments AI 提供的参数（从 function_call.arguments 解析）
     * @param context   执行上下文（userId/projectId/sessionId，由 AgentRunner 强制注入，不可被 AI
     *                  操纵）
     * @return 工具执行结果（JSON 格式）
     * @throws Exception 执行失败时抛出异常
     */
    public abstract JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception;
}
