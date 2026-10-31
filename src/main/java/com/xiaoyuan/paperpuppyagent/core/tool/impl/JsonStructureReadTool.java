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
public class JsonStructureReadTool extends Tool {

    private static final int DEFAULT_MAX_DEPTH = 4;
    private static final int DEFAULT_SAMPLE_ARRAY_ITEMS = 3;
    private static final int DEFAULT_MAX_KEYS_PER_OBJECT = 80;

    private final BackendToolGrpcClient backendClient;

    public JsonStructureReadTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return "json_structure_read";
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
        int maxDepth = JsonResourceToolSupport.optionalPositiveInt(arguments, "max_depth", DEFAULT_MAX_DEPTH);
        int sampleArrayItems = JsonResourceToolSupport.optionalPositiveInt(
                arguments,
                "sample_array_items",
                DEFAULT_SAMPLE_ARRAY_ITEMS
        );
        int maxKeysPerObject = JsonResourceToolSupport.optionalPositiveInt(
                arguments,
                "max_keys_per_object",
                DEFAULT_MAX_KEYS_PER_OBJECT
        );

        log.info("[JsonStructureReadTool] execute: userId={}, projectId={}, fileId={}, documentId={}, path={}",
                userId, projectId, fileId, documentId, path);

        var response = backendClient.jsonStructureRead(
                userId,
                projectId,
                fileId,
                documentId,
                path,
                maxDepth,
                sampleArrayItems,
                maxKeysPerObject
        );
        if (!response.getSuccess()) {
            return JsonResourceToolSupport.buildFailure(mapper, name(), response.getError());
        }

        JsonNode payload = JsonResourceToolSupport.parseResultJson(mapper, response.getResultJson());
        return JsonResourceToolSupport.buildEnvelope(mapper, response.getFileId(), response.getDocumentId(), payload);
    }
}
