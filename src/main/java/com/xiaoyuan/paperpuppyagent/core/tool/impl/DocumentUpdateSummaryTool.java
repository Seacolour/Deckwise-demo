package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentSummaryResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 更新文档摘要工具。
 *
 * <p>
 * 用途：让 Agent 主动更新文档的摘要信息，便于后续 file_list 时快速了解文件内容。
 * </p>
 *
 * <p>
 * 使用场景：
 * - 发现文档无摘要时，Agent 读取内容后生成摘要
 * - 文档内容发生较大变化后，Agent 判断需要更新摘要
 * </p>
 */
@Slf4j
@Component
public class DocumentUpdateSummaryTool extends Tool {

    private final BackendToolGrpcClient backendClient;

    public DocumentUpdateSummaryTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "document_update_summary";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        // 从参数中获取 document_id 和 summary
        if (arguments == null || !arguments.has("document_id") || !arguments.has("summary")) {
            throw new IllegalArgumentException("缺少必填参数: document_id 和 summary");
        }

        long documentId = arguments.get("document_id").asLong();
        String summary = arguments.get("summary").asText();

        // 验证摘要长度
        if (summary == null || summary.isEmpty()) {
            throw new IllegalArgumentException("摘要不能为空");
        }
        if (summary.length() > 500) {
            throw new IllegalArgumentException("摘要过长，建议 100-200 字符，最大 500 字符");
        }

        long userId = context.userId();
        long projectId = context.projectId();

        // 验证上下文有效性
        if (userId <= 0 || projectId <= 0) {
            throw new IllegalStateException("工具执行上下文无效: userId=" + userId + ", projectId=" + projectId);
        }

        log.info("[DocumentUpdateSummaryTool] Execute: userId={}, documentId={}, summaryLen={}",
                userId, documentId, summary.length());

        // 调用 Backend gRPC 更新摘要
        UpdateDocumentSummaryResponse response = backendClient.updateDocumentSummary(
                userId, projectId, documentId, summary);

        // 构建返回结果
        ObjectNode result = mapper.createObjectNode();
        result.put("success", response.getSuccess());
        result.put("document_id", response.getDocumentId());
        if (!response.getUpdatedAt().isEmpty()) {
            result.put("updated_at", response.getUpdatedAt());
        }
        result.put("message", response.getSuccess() ? "摘要更新成功" : "摘要更新失败");

        log.info("[DocumentUpdateSummaryTool] Completed: documentId={}, success={}",
                documentId, response.getSuccess());

        return result;
    }
}
