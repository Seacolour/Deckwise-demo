package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetImageResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 读取图片（返回可访问的 image_url），供 AgentRunner 注入为多模态 input_image。
 */
@Slf4j
@Component
public class ImageReadTool extends Tool {

    private final BackendToolGrpcClient backendClient;

    public ImageReadTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "image_read";
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

        log.info("[ImageReadTool] Execute: userId={}, projectId={}, fileId={}", userId, projectId, fileId);

        GetImageResponse resp = backendClient.getImage(userId, projectId, fileId);

        ObjectNode out = mapper.createObjectNode();
        out.put("success", true);
        out.put("file_id", resp.getFileId());
        if (!resp.getFileName().isEmpty()) {
            out.put("file_name", resp.getFileName());
        }
        if (!resp.getFileType().isEmpty()) {
            out.put("file_type", resp.getFileType());
        }
        if (!resp.getImageUrl().isEmpty()) {
            out.put("image_url", resp.getImageUrl());
        }
        out.put("message", "图片读取成功（请将 image_url 作为 input_image 注入）");
        return out;
    }
}

