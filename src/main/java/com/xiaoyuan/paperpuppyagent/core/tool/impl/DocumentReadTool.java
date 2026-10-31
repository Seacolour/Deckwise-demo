package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSession;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSessionManager;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetDocumentResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 读取文档内容工具。
 *
 * <p>
 * 用途：读取指定文档的内容（草稿或已发布版本），供 AI 理解文档内容并回答用户问题。
 * </p>
 * 
 * <p>
 * 安全说明：
 * - document_id 由 AI 从 file_list 结果中获取（用户无法伪造）
 * - userId 和 projectId 从 ToolExecutionContext 自动获取，防止提示词注入
 * - Backend 返回的响应已经过脱敏处理
 * </p>
 */
@Slf4j
@Component
public class DocumentReadTool extends Tool {

    private final BackendToolGrpcClient backendClient;
    private final DocumentEditSessionManager sessionManager;

    public DocumentReadTool(ObjectMapper mapper,
            BackendToolGrpcClient backendClient,
            DocumentEditSessionManager sessionManager) {
        super(mapper);
        this.backendClient = backendClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String name() {
        return "document_read";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        // 从上下文获取安全的身份信息
        long userId = context.userId();
        long projectId = context.projectId();
        long sessionId = context.sessionId();

        // 验证上下文有效性
        if (userId <= 0 || projectId <= 0) {
            throw new IllegalStateException("工具执行上下文无效: userId=" + userId + ", projectId=" + projectId);
        }

        // 从 AI 参数中获取 document_id
        if (arguments == null || !arguments.has("document_id")) {
            throw new IllegalArgumentException("缺少必需参数: document_id");
        }

        long documentId = arguments.get("document_id").asLong();
        if (documentId <= 0) {
            throw new IllegalArgumentException("无效的 document_id: " + documentId);
        }

        // 获取可选参数 read_draft（默认 true）
        boolean readDraft = true;
        if (arguments.has("read_draft")) {
            readDraft = arguments.get("read_draft").asBoolean(true);
        }

        log.info("[DocumentReadTool] 执行文档读取: userId={}, sessionId={}, documentId={}, readDraft={}",
                userId, sessionId, documentId, readDraft);

        // 【关键修复】优先检查 Agent 侧是否有 workingContent
        DocumentEditSession session = sessionManager.get(sessionId, documentId);
        if (session != null) {
            // 如果有正在编辑的副本，返回工作副本内容（确保行号正确）
            log.info("[DocumentReadTool] 返回 Agent 工作副本: documentId={}, version={}, contentLen={}",
                    documentId, session.getVersion(), session.getWorkingContent().length());

            ObjectNode result = mapper.createObjectNode();
            result.put("success", true);
            result.put("document_id", documentId);
            String content = session.getWorkingContent();
            result.put("content", content);
            result.put("version", session.getDocumentVersion());
            result.put("working_version", session.getVersion());
            result.put("source", "agent_working_copy");
            result.put("line_mode", "physical_newlines");
            result.put("total_lines", countLines(content));
            result.put("message", "返回正在编辑的工作副本（确保行号与之前的修改一致）");

            return result;
        }

        // 如果没有工作副本，从 Backend 获取
        log.info("[DocumentReadTool] 从 Backend 获取文档: documentId={}", documentId);
        GetDocumentResponse response = backendClient.getDocument(userId, projectId, documentId, readDraft);

        // 转换为 JSON 格式返回
        ObjectNode result = mapper.createObjectNode();
        result.put("success", true);
        result.put("document_id", response.getDocumentId());

        if (!response.getTitle().isEmpty()) {
            result.put("title", response.getTitle());
        }

        String content = response.getContent();
        if (!content.isEmpty()) {
            result.put("content", content);
            result.put("line_mode", "physical_newlines");
            result.put("total_lines", countLines(content));
        } else {
            result.put("content", "");
            result.put("total_lines", 0);
            result.put("message", "文档内容为空");
        }

        if (response.getVersion() > 0) {
            result.put("version", response.getVersion());
        }

        if (!response.getUpdatedAt().isEmpty()) {
            result.put("updated_at", response.getUpdatedAt());
        }

        result.put("source", "backend");
        
        log.info("[DocumentReadTool] 返回 Backend 文档内容: documentId={}, titleLen={}, contentLen={}",
                documentId,
                response.getTitle().length(),
                response.getContent().length());

        return result;
    }

    /**
     * 计算内容的物理行数。
     */
    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        return normalized.split("\n", -1).length;
    }
}
