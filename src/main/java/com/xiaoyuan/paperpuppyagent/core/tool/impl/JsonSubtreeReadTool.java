package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JsonSubtreeReadTool extends Tool {

    private static final int DEFAULT_MAX_BYTES = 12_000;
    private static final int DEFAULT_MAX_NODES = 400;

    private final BackendToolGrpcClient backendClient;

    public JsonSubtreeReadTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "json_subtree_read";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        long userId = context.userId();
        long projectId = context.projectId();
        if (userId <= 0 || projectId <= 0) {
            throw new IllegalStateException("工具执行上下文无效");
        }

        long fileId = JsonResourceToolSupport.optionalFileId(arguments);
        long documentId = JsonResourceToolSupport.optionalDocumentId(arguments);
        JsonResourceToolSupport.requireSingleResourceTarget(fileId, documentId);
        String path = JsonResourceToolSupport.optionalPath(arguments);
        int maxBytes = JsonResourceToolSupport.optionalPositiveInt(arguments, "max_bytes", DEFAULT_MAX_BYTES);
        int maxNodes = JsonResourceToolSupport.optionalPositiveInt(arguments, "max_nodes", DEFAULT_MAX_NODES);

        log.info("[JsonSubtreeReadTool] execute: userId={}, projectId={}, fileId={}, documentId={}, path={}",
                userId, projectId, fileId, documentId, path);

        var response = backendClient.jsonSubtreeRead(
                userId,
                projectId,
                fileId,
                documentId,
                path,
                maxBytes,
                maxNodes
        );
        if (!response.getSuccess()) {
            return JsonResourceToolSupport.buildFailure(mapper, name(), response.getError());
        }

        JsonNode payload = JsonResourceToolSupport.parseResultJson(mapper, response.getResultJson());
        return JsonResourceToolSupport.buildEnvelope(mapper, response.getFileId(), response.getDocumentId(), payload);
    }
}
