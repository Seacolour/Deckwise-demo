package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Todo-list 工具：帮助 Agent 在多轮工具调用中维护任务计划。
 *
 * <p>
 * 设计理念：此工具是一个"echo"型工具——Agent 将 todo 列表作为参数传入，
 * 工具原样确认并返回。由于 Agent 的工具调用消息会被持久化到对话上下文中，
 * todo 内容自然地存在于后续回合的上下文里，LLM 可以随时回顾当前任务进度。
 * </p>
 *
 * <p>
 * 不需要后端存储、不需要数据库、不需要 gRPC 调用。
 * </p>
 */
@Slf4j
@Component
public class TodoListTool extends Tool {

    public TodoListTool(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public String name() {
        return "todo_list";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        if (arguments == null || !arguments.has("todos")) {
            throw new IllegalArgumentException("缺少必需参数: todos");
        }

        JsonNode todosNode = arguments.get("todos");
        if (!todosNode.isArray()) {
            throw new IllegalArgumentException("todos 必须是数组");
        }

        ArrayNode todos = (ArrayNode) todosNode;

        // 统计各状态数量
        int total = todos.size();
        int pending = 0;
        int inProgress = 0;
        int completed = 0;

        for (JsonNode item : todos) {
            String status = item.has("status") ? item.get("status").asText("") : "";
            switch (status) {
                case "pending" -> pending++;
                case "in_progress" -> inProgress++;
                case "completed" -> completed++;
            }
        }

        log.info("[TodoListTool] 更新任务列表: total={}, pending={}, in_progress={}, completed={}",
                total, pending, inProgress, completed);

        // 原样返回 todo 列表 + 统计摘要
        ObjectNode result = mapper.createObjectNode();
        result.put("success", true);
        result.set("todos", todos);

        ObjectNode summary = mapper.createObjectNode();
        summary.put("total", total);
        summary.put("pending", pending);
        summary.put("in_progress", inProgress);
        summary.put("completed", completed);
        result.set("summary", summary);

        return result;
    }
}
