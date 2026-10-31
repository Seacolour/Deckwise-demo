package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSession;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSessionManager;
import com.xiaoyuan.paperpuppyagent.core.tool.document.EditHunk;
import com.xiaoyuan.paperpuppyagent.core.tool.document.MarkdownDiffApplier;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetDocumentResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档 Diff 工具。
 *
 * <p>
 * 用途：应用 hunks 到 Agent 侧的文档工作副本。
 * </p>
 *
 * <p>
 * 工作流程：
 * 1. 获取/创建 DocumentEditSession
 * 2. 如果没有工作副本，从 Backend 获取文档内容
 * 3. 应用 hunks 到工作副本
 * 4. 返回修改结果（包含 metadata 供前端高亮）
 * </p>
 *
 * <p>
 * Hunk 格式：
 * - hunk_id: 标识符
 * - op: INSERT | DELETE | REPLACE
 * - start_line / end_line: 行范围（1-based）
 * - before: 原始内容（DELETE/REPLACE 必填）
 * - after: 新内容（INSERT/REPLACE 必填）
 * - reason: 修改理由
 * </p>
 */
@Slf4j
@Component
public class DocumentDiffTool extends Tool {

    private static final int CONTENT_PREVIEW_LENGTH = 500;

    private final BackendToolGrpcClient backendClient;
    private final DocumentEditSessionManager sessionManager;

    public DocumentDiffTool(ObjectMapper mapper,
            BackendToolGrpcClient backendClient,
            DocumentEditSessionManager sessionManager) {
        super(mapper);
        this.backendClient = backendClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String name() {
        return "document_diff";
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

        // 解析 hunks
        List<EditHunk> hunks = new ArrayList<>();
        if (arguments.has("hunks") && arguments.get("hunks").isArray()) {
            for (JsonNode hunkNode : arguments.get("hunks")) {
                hunks.add(EditHunk.fromJson(hunkNode));
            }
        }

        log.info("[DocumentDiffTool] Execute: userId={}, documentId={}, hunksCount={}",
                userId, documentId, hunks.size());

        // 获取或创建编辑会话
        DocumentEditSession session = sessionManager.get(sessionId, documentId);
        if (session == null) {
            // 首次访问，从 Backend 获取文档内容
            log.info("[DocumentDiffTool] Fetching document from Backend: documentId={}", documentId);
            GetDocumentResponse doc = backendClient.getDocument(userId, projectId, documentId, true);
            String content = doc.getContent();
            session = sessionManager.createOrUpdate(sessionId, documentId, content, doc.getVersion());
        }

        // 如果 hunks 为空，表示修改完成
        if (hunks.isEmpty()) {
            ObjectNode result = mapper.createObjectNode();
            result.put("success", true);
            result.put("status", "completed");
            result.put("message", "没有更多修改。注意：如需将修改写入数据库，必须调用 document_save。");
            result.put("current_version", session.getVersion());
            result.put("document_version", session.getDocumentVersion());
            result.put("content_preview", session.getContentPreview(CONTENT_PREVIEW_LENGTH));
            result.put("must_call_document_save", true);
            return result;
        }

        // 应用 hunks
        MarkdownDiffApplier.ApplyResult applyResult = session.applyHunks(hunks);

        // 构建返回结果
        ObjectNode result = mapper.createObjectNode();
        result.put("success", applyResult.success());
        result.put("new_version", session.getVersion());
        result.put("document_version", session.getDocumentVersion());
        result.put("hunks_count", hunks.size());
        result.put("applied_count", applyResult.appliedHunkIds() == null ? 0 : applyResult.appliedHunkIds().size());
        result.put("content_preview", session.getContentPreview(CONTENT_PREVIEW_LENGTH));
        // 重要：diff 只修改 Agent 侧工作副本，必须 save 才会落库
        result.put("must_call_document_save", true);

        ArrayNode appliedIds = mapper.createArrayNode();
        for (String id : applyResult.appliedHunkIds()) {
            appliedIds.add(id);
        }
        result.set("applied_hunks", appliedIds);

        if (!applyResult.warnings().isEmpty()) {
            ArrayNode warnings = mapper.createArrayNode();
            for (String w : applyResult.warnings()) {
                warnings.add(w);
            }
            result.set("warnings", warnings);
        }
        if (!applyResult.success()) {
            result.put("message", "diff 未能应用（通常是 before 内容或行号不匹配）。建议先调用 document_read 获取最新内容与精确行文本，再重试 document_diff。");
        } else {
            result.put("message", "diff 已应用到工作副本。请调用 document_save 将修改持久化到数据库。");
        }

        log.info("[DocumentDiffTool] Applied hunks: documentId={}, version={}, appliedCount={}",
                documentId, session.getVersion(), applyResult.appliedHunkIds().size());

        return result;
    }

    /**
     * 构建用于前端的 metadata。
     * 
     * <p>
     * 调用方可以从 arguments 中提取 hunks 并通过 SSE metadata 传递给前端。
     * </p>
     */
    public ObjectNode buildMetadata(long documentId, JsonNode hunksNode, int newVersion) {
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("type", "document_diff");
        metadata.put("document_id", documentId);
        metadata.put("version", newVersion);
        metadata.set("hunks", hunksNode);
        return metadata;
    }
}
