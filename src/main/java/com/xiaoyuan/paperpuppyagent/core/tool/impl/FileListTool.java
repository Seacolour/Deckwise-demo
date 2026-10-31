package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.FileItem;
import com.xiaoyuan.paperpuppyagent.grpc.backend.ListProjectFilesResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 列出项目文件列表工具。
 *
 * <p>
 * 用途：让 AI 了解当前项目有哪些文件可用。
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
public class FileListTool extends Tool {

    private final BackendToolGrpcClient backendClient;

    public FileListTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "file_list";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        // 安全：忽略 AI 传递的任何参数，强制从 context 获取敏感信息
        // 即使 AI 尝试传递 userId 或 projectId，也会被忽略，防止提示词注入攻击
        if (arguments != null && !arguments.isEmpty()) {
            // 避免将模型输入参数原样写入日志（可能包含提示词注入内容或敏感信息）
            log.warn("[FileListTool] 检测到 AI 传递了参数（count={}），但将被忽略以确保安全", arguments.size());
        }

        long userId = context.userId();
        long projectId = context.projectId();

        // 验证上下文有效性
        if (userId <= 0 || projectId <= 0) {
            throw new IllegalStateException("工具执行上下文无效: userId=" + userId + ", projectId=" + projectId);
        }

        log.info("[FileListTool] 执行文件列表查询: userId={}, projectId={}", userId, projectId);

        // 调用 Backend gRPC 获取真实数据
        ListProjectFilesResponse response = backendClient.listProjectFiles(userId, projectId);

        // 转换为 JSON 格式返回
        ObjectNode result = mapper.createObjectNode();
        result.put("success", true);
        result.put("message", "文件列表查询成功");

        ArrayNode files = mapper.createArrayNode();
        for (FileItem item : response.getFilesList()) {
            ObjectNode file = mapper.createObjectNode();
            file.put("file_id", item.getFileId());
            if (!item.getFileName().isEmpty()) {
                file.put("file_name", item.getFileName());
            }
            if (!item.getFileType().isEmpty()) {
                file.put("file_type", item.getFileType());
            }
            file.put("size_bytes", item.getSizeBytes());
            if (!item.getCreatedAt().isEmpty()) {
                file.put("created_at", item.getCreatedAt());
            }
            if (item.getDocumentId() > 0) {
                file.put("document_id", item.getDocumentId());
            }
            if (!item.getDocumentType().isEmpty()) {
                file.put("document_type", item.getDocumentType());
            }
            if (!item.getPreviewUrl().isEmpty()) {
                file.put("preview_url", item.getPreviewUrl());
            }
            if (!item.getSummary().isEmpty()) {
                file.put("summary", item.getSummary());
            }
            if (item.getDocumentVersion() > 0) {
                file.put("document_version", item.getDocumentVersion());
            }
            if (item.getSummaryVersion() > 0) {
                file.put("summary_version", item.getSummaryVersion());
            }
            if (item.getEstimatedModelChars() > 0) {
                file.put("estimated_model_chars", item.getEstimatedModelChars());
            }
            if (item.getPageCount() > 0) {
                file.put("page_count", item.getPageCount());
            }
            if (item.getEstimatedTextChars() > 0) {
                file.put("estimated_text_chars", item.getEstimatedTextChars());
            }
            // 工具组合提示：列出可与此文件交互的所有工具，供模型根据意图灵活选择
            ArrayNode compositeTools = mapper.createArrayNode();
            for (String t : compositeToolsFor(item.getFileType(), item.getDocumentType(), item.getDocumentId() > 0)) {
                compositeTools.add(t);
            }
            file.set("composite_tools", compositeTools);
            files.add(file);
        }

        result.set("files", files);
        result.put("total_count", files.size());

        log.info("[FileListTool] 返回文件列表: projectId={}, fileCount={}", projectId, files.size());
        return result;
    }

    /**
     * 返回可与该文件交互的工具组合列表，供模型根据实际意图灵活选择。
     *
     * <ul>
     * <li>markdown document → document 工具链</li>
     * <li>xlsx Excel → file_read_url（首选）+ file_content_read（兜底）</li>
     * <li>PDF/docx/pptx 等二进制格式 → file_read_url（首选，多模态）+ file_content_read（兜底）</li>
     * <li>图片格式 → image_read</li>
     * <li>普通 json 文件 → json_structure_read + json_subtree_read + file_content_read</li>
     * <li>txt/csv 等纯文本 → file_content_read</li>
     * </ul>
     */
    private static java.util.List<String> compositeToolsFor(String fileType, String documentType, boolean hasDocumentId) {
        String normalizedDocumentType = documentType == null ? "" : documentType.trim().toLowerCase(java.util.Locale.ROOT);
        if ("markdown".equals(normalizedDocumentType) && hasDocumentId) {
            return java.util.List.of("document_read", "document_locate", "document_diff", "document_save");
        }
        if (fileType == null || fileType.isBlank()) {
            return java.util.List.of("file_content_read");
        }
        return switch (fileType.toLowerCase().trim()) {
            case "xlsx" -> java.util.List.of("file_read_url", "file_content_read");
            case "pdf", "docx", "doc", "xls", "pptx", "ppt" ->
                    java.util.List.of("file_read_url", "file_content_read");
            case "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif" ->
                    java.util.List.of("image_read");
            case "json" -> java.util.List.of("json_structure_read", "json_subtree_read", "file_content_read");
            default -> java.util.List.of("file_content_read");
        };
    }
}
