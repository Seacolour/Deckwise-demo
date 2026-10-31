package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileUrlResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 获取文件 AI 可访问 URL，供 AgentRunner 注入为多模态 input_file。
 *
 * <p>适用于"参考阅读"场景（ASK 模式 / 非编辑意图）：
 * 工具返回 file_url，AgentRunner 检测到后将其作为 input_file 直接传给 OpenAI 模型，
 * 由模型原生解析 PDF / docx 等文件全文，无需后端分片注入上下文。</p>
 *
 * <p>支持 public URL 与 backend 颁发的私有资源 token URL。
 * 若后端无法生成 AI 可访问 URL，再退化使用 file_content_read 工具。</p>
 *
 */
@Slf4j
@Component
public class FileReadUrlTool extends Tool {

    private final BackendToolGrpcClient backendClient;

    public FileReadUrlTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "file_read_url";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        long userId = context.userId();
        long projectId = context.projectId();
        if (userId <= 0 || projectId <= 0) {
            throw new IllegalStateException("工具执行上下文无效: userId=" + userId + ", projectId=" + projectId);
        }

        long fileId = arguments == null ? 0 : arguments.path("file_id").asLong(0);
        if (fileId <= 0) {
            throw new IllegalArgumentException("file_id 不能为空或非法");
        }

        log.info("[FileReadUrlTool] Execute: userId={}, projectId={}, fileId={}", userId, projectId, fileId);

        GetFileUrlResponse resp = backendClient.getFileUrl(userId, projectId, fileId);

        ObjectNode out = mapper.createObjectNode();
        out.put("success", true);
        out.put("file_id", resp.getFileId());
        if (!resp.getFileName().isEmpty()) {
            out.put("file_name", resp.getFileName());
        }
        if (!resp.getFileType().isEmpty()) {
            out.put("file_type", resp.getFileType());
        }
        if (!resp.getFileUrl().isEmpty()) {
            out.put("file_url", resp.getFileUrl());
        }
        if (resp.getSizeBytes() > 0) {
            out.put("size_bytes", resp.getSizeBytes());
        }
        out.put("message", "文件 URL 获取成功（请将 file_url 作为 input_file 注入，由模型原生解析文件内容）");
        return out;
    }
}
