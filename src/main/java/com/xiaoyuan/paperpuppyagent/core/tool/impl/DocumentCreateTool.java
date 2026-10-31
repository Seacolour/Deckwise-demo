package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.CreateDocumentResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 创建 Markdown 文档工具。
 *
 * <p>
 * 用途：让 AI 在当前项目中创建一个新的 Markdown 文档，可选提供初始内容。
 * </p>
 *
 * <p>
 * 安全说明：此工具不需要 AI 传递 userId 或 projectId 参数，
 * 这些值从 ToolExecutionContext 中自动获取（由 AgentRunner 强制注入），
 * 防止提示词注入攻击导致越权访问其他用户数据。
 * </p>
 */
@Slf4j
@Component
public class DocumentCreateTool extends Tool {

    private final BackendToolGrpcClient backendClient;

    public DocumentCreateTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "document_create";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        long userId = context.userId();
        long projectId = context.projectId();

        // 验证上下文有效性
        if (userId <= 0 || projectId <= 0) {
            throw new IllegalStateException("工具执行上下文无效: userId=" + userId + ", projectId=" + projectId);
        }

        // 从 AI 参数中获取 title（必填）
        if (arguments == null || !arguments.has("title")) {
            throw new IllegalArgumentException("缺少必需参数: title");
        }

        String title = arguments.get("title").asText("").trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title 不能为空");
        }

        // 获取可选参数 content
        String content = "";
        if (arguments.has("content") && !arguments.get("content").isNull()) {
            content = arguments.get("content").asText("");
        }

        log.info("[DocumentCreateTool] 执行文档创建: userId={}, projectId={}, title={}, contentLen={}",
                userId, projectId, title, content.length());

        // 调用 Backend gRPC 创建文档
        CreateDocumentResponse response = backendClient.createDocument(userId, projectId, title, content);

        // 转换为 JSON 格式返回
        ObjectNode result = mapper.createObjectNode();
        result.put("success", response.getSuccess());
        result.put("document_id", response.getDocumentId());

        if (!response.getTitle().isEmpty()) {
            result.put("title", response.getTitle());
        }
        if (response.getVersion() > 0) {
            result.put("version", response.getVersion());
        }
        if (!response.getCreatedAt().isEmpty()) {
            result.put("created_at", response.getCreatedAt());
        }

        result.put("message", "文档创建成功，可使用 document_read 读取内容，或使用 document_diff + document_save 进行编辑");

        log.info("[DocumentCreateTool] 文档创建成功: projectId={}, documentId={}, title={}",
                projectId, response.getDocumentId(), response.getTitle());

        return result;
    }
}
