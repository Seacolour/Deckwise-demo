package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSession;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSessionManager;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文档保存工具。
 *
 * <p>
 * 用途：将 Agent 侧的文档工作副本保存到 Backend DB。
 * </p>
 *
 * <p>
 * AI 可以在合适的时机调用此工具来持久化修改。
 * </p>
 */
@Slf4j
@Component
public class DocumentSaveTool extends Tool {

    private final BackendToolGrpcClient backendClient;
    private final DocumentEditSessionManager sessionManager;

    public DocumentSaveTool(ObjectMapper mapper,
            BackendToolGrpcClient backendClient,
            DocumentEditSessionManager sessionManager) {
        super(mapper);
        this.backendClient = backendClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String name() {
        return "document_save";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        long userId = context.userId();
        long projectId = context.projectId();
        long sessionId = context.sessionId();

        if (userId <= 0 || projectId <= 0 || sessionId <= 0) {
            throw new IllegalStateException("工具执行上下文无效");
        }

        // 获取 document_id
        if (!arguments.has("document_id")) {
            throw new IllegalArgumentException("缺少必需参数: document_id");
        }
        long documentId = arguments.get("document_id").asLong();

        String message = arguments.has("message") ? arguments.get("message").asText() : null;

        log.info("[DocumentSaveTool] Execute: userId={}, documentId={}, message={}",
                userId, documentId, message);

        // 获取编辑会话
        DocumentEditSession session = sessionManager.get(sessionId, documentId);
        if (session == null) {
            ObjectNode result = mapper.createObjectNode();
            result.put("success", false);
            result.put("error", "没有找到该文档的编辑会话，可能尚未进行修改");
            return result;
        }

        // 调用 Backend 保存
        log.info("[DocumentSaveTool] Saving document: documentId={}, version={}, contentLen={}",
                documentId, session.getVersion(), session.getWorkingContent().length());

        com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentDraftResponse response = backendClient
                .updateDocumentDraft(userId, projectId, documentId, session.getDocumentVersion(),
                        session.getWorkingContent(), message);

        // 构建返回结果
        ObjectNode result = mapper.createObjectNode();
        result.put("success", response.getSuccess());
        result.put("document_id", documentId);
        result.put("before_revision", String.valueOf(session.getDocumentVersion()));
        result.put("revision", String.valueOf(response.getNewVersion()));
        result.put("saved_version", response.getNewVersion());
        result.put("message", message != null ? message : "已保存");
        result.put("content_length", session.getWorkingContent().length());
        if (!response.getUpdatedAt().isEmpty()) {
            result.put("updated_at", response.getUpdatedAt());
        }

        log.info("[DocumentSaveTool] Document saved: documentId={}, version={}",
                documentId, session.getVersion());

        // P0：保存成功后清理编辑会话，避免工作副本常驻内存
        if (response.getSuccess()) {
            try {
                sessionManager.remove(sessionId, documentId);
            } catch (Exception ignore) {
            }
        }

        return result;
    }
}
