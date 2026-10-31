package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserMemoryResponse;
import com.xiaoyuan.paperpuppyagent.grpc.backend.UserMemoryItem;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 管理用户持久化记忆的工具。
 *
 * <p>
 * 记忆是关于用户的事实性信息（如研究方向、技术栈、偏好等），
 * 会在后续对话中作为背景知识提供。
 * AI 可主动提取有价值的信息保存为记忆。
 * </p>
 */
@Slf4j
@Component
public class ManageUserMemoryTool extends Tool {

    private final BackendToolGrpcClient backendClient;

    public ManageUserMemoryTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "manage_user_memory";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        long userId = context.userId();
        if (userId <= 0) {
            throw new IllegalStateException("工具执行上下文无效：缺少 userId");
        }

        if (arguments == null || !arguments.has("action")) {
            throw new IllegalArgumentException("缺少必需参数: action");
        }

        String action = arguments.get("action").asText();
        long memoryId = arguments.has("memory_id") ? arguments.get("memory_id").asLong(0) : 0;
        String title = arguments.has("title") ? arguments.get("title").asText(null) : null;
        String content = arguments.has("content") ? arguments.get("content").asText(null) : null;

        log.info("[ManageUserMemoryTool] userId={}, action={}, memoryId={}", userId, action, memoryId);

        ManageUserMemoryResponse response = backendClient.manageUserMemory(userId, action, memoryId, title, content);

        ObjectNode result = mapper.createObjectNode();
        result.put("success", response.getSuccess());
        result.put("message", response.getMessage());

        if (response.getMemoriesCount() > 0) {
            ArrayNode memoriesArr = mapper.createArrayNode();
            for (UserMemoryItem item : response.getMemoriesList()) {
                ObjectNode memNode = mapper.createObjectNode();
                memNode.put("memory_id", item.getMemoryId());
                memNode.put("title", item.getTitle());
                memNode.put("content", item.getContent());
                memNode.put("source", item.getSource());
                if (!item.getCreatedAt().isEmpty()) {
                    memNode.put("created_at", item.getCreatedAt());
                }
                if (!item.getUpdatedAt().isEmpty()) {
                    memNode.put("updated_at", item.getUpdatedAt());
                }
                memoriesArr.add(memNode);
            }
            result.set("memories", memoriesArr);
        }

        return result;
    }
}
