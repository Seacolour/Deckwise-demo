package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileContentResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 读取文件切片内容工具。
 *
 * <p>
 * 用途：读取 PDF 等无 Document 副本的文件内容（通过切片拼接）。
 * </p>
 * 
 * <p>
 * 与 DocumentReadTool 的区别：
 * - DocumentReadTool：用于读取 Markdown 等有 Document 副本的文件，获取最新编辑内容
 * - FileContentReadTool：用于读取 PDF 等原始文件的解析内容（切片）
 * </p>
 * 
 * <p>
 * 安全说明：
 * - file_id 由 AI 从 file_list 结果中获取
 * - userId 和 projectId 从 ToolExecutionContext 自动获取，防止提示词注入
 * - Backend 返回的响应已经过脱敏处理
 * </p>
 */
@Slf4j
@Component
public class FileContentReadTool extends Tool {

    private static final int DEFAULT_MAX_FRAGMENTS = 10;
    private static final int DEFAULT_START_FRAGMENT = 1;

    private final BackendToolGrpcClient backendClient;

    public FileContentReadTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "file_content_read";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        // 从上下文获取安全的身份信息
        long userId = context.userId();
        long projectId = context.projectId();

        // 验证上下文有效性
        if (userId <= 0 || projectId <= 0) {
            throw new IllegalStateException("工具执行上下文无效: userId=" + userId + ", projectId=" + projectId);
        }

        // 从 AI 参数中获取 file_id
        if (arguments == null || !arguments.has("file_id")) {
            throw new IllegalArgumentException("缺少必需参数: file_id");
        }

        long fileId = arguments.get("file_id").asLong();
        if (fileId <= 0) {
            throw new IllegalArgumentException("无效的 file_id: " + fileId);
        }

        // 获取 max_fragments 参数
        int maxFragments = DEFAULT_MAX_FRAGMENTS;
        if (arguments.has("max_fragments")) {
            maxFragments = arguments.get("max_fragments").asInt(DEFAULT_MAX_FRAGMENTS);
        }
        maxFragments = Math.max(1, Math.min(maxFragments, 50));

        // 获取 start_fragment 参数（1-based）
        int startFragment = DEFAULT_START_FRAGMENT;
        if (arguments.has("start_fragment")) {
            startFragment = arguments.get("start_fragment").asInt(DEFAULT_START_FRAGMENT);
        }
        startFragment = Math.max(1, startFragment);

        log.info("[FileContentReadTool] 执行文件内容读取: userId={}, projectId={}, fileId={}, startFragment={}, maxFragments={}",
                userId, projectId, fileId, startFragment, maxFragments);

        // 调用 Backend gRPC 获取文件切片内容
        GetFileContentResponse response = backendClient.getFileContent(userId, projectId, fileId, startFragment, maxFragments);

        // 转换为 JSON 格式返回
        ObjectNode result = mapper.createObjectNode();
        result.put("success", true);
        result.put("file_id", response.getFileId());
        result.put("start_fragment", startFragment);

        if (!response.getFileName().isEmpty()) {
            result.put("file_name", response.getFileName());
        }

        if (!response.getFileType().isEmpty()) {
            result.put("file_type", response.getFileType());
        }

        if (!response.getContent().isEmpty()) {
            result.put("content", response.getContent());
        } else {
            result.put("content", "");
            result.put("message", "文件内容为空或尚未解析");
        }

        result.put("total_fragments", response.getTotalFragments());
        result.put("returned_fragments", response.getReturnedFragments());
        // 1-based inclusive
        int endFragment = response.getReturnedFragments() <= 0
                ? startFragment - 1
                : (startFragment + response.getReturnedFragments() - 1);
        result.put("end_fragment", endFragment);

        if (response.getTotalFragments() > response.getReturnedFragments()) {
            result.put("truncated", true);
            int nextStart = endFragment + 1;
            result.put("truncation_message",
                    "文件内容已截断，仅返回前 " + response.getReturnedFragments() +
                            " 个切片（共 " + response.getTotalFragments() + " 个）。" +
                            "如需下一页，请将 start_fragment 设置为 " + nextStart + "（通常取 end_fragment+1），并保持/调整 max_fragments。");
        }

        log.info("[FileContentReadTool] 返回文件内容: fileId={}, returnedFragments={}, totalFragments={}, contentLen={}",
                fileId, response.getReturnedFragments(), response.getTotalFragments(),
                response.getContent() != null ? response.getContent().length() : 0);

        return result;
    }
}
