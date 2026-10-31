package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.JsonStructureReadRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.JsonStructureReadResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.JsonSubtreeReadRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.JsonSubtreeReadResponse;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonStructureService;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonSubtreeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolJsonStructureHandler {

    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;
    private final JsonStructureService jsonStructureService;
    private final JsonSubtreeService jsonSubtreeService;

    public JsonStructureReadResponse jsonStructureRead(JsonStructureReadRequest request) {
        try {
            var result = jsonStructureService.readStructure(
                    request.getUserId(),
                    request.getProjectId(),
                    request.getFileId(),
                    request.getDocumentId(),
                    request.getPath(),
                    request.getMaxDepth(),
                    request.getSampleArrayItems(),
                    request.getMaxKeysPerObject()
            );
            var resource = result.resource();
            return JsonStructureReadResponse.newBuilder()
                    .setSuccess(true)
                    .setResourceKind(nvl(resource.resourceKind()))
                    .setFileId(resource.fileId() == null ? 0L : resource.fileId())
                    .setDocumentId(resource.documentId() == null ? 0L : resource.documentId())
                    .setRevision(nvl(resource.revision()))
                    .setResultJson(writeJson(buildPayload(result)))
                    .build();
        } catch (Exception e) {
            log.error("[AgentToolJsonStructureHandler] jsonStructureRead failed: fileId={}, documentId={}, err={}",
                    request.getFileId(), request.getDocumentId(), e.getMessage(), e);
            return JsonStructureReadResponse.newBuilder()
                    .setSuccess(false)
                    .setResourceKind(resolveErrorResourceKind(request))
                    .setFileId(request.getFileId())
                    .setDocumentId(request.getDocumentId())
                    .setError(nvl(e.getMessage()))
                    .build();
        }
    }

    public JsonSubtreeReadResponse jsonSubtreeRead(JsonSubtreeReadRequest request) {
        try {
            var result = jsonSubtreeService.readSubtree(
                    request.getUserId(),
                    request.getProjectId(),
                    request.getFileId(),
                    request.getDocumentId(),
                    request.getPath(),
                    request.getMaxBytes(),
                    request.getMaxNodes()
            );
            var resource = result.resource();
            return JsonSubtreeReadResponse.newBuilder()
                    .setSuccess(true)
                    .setResourceKind(nvl(resource.resourceKind()))
                    .setFileId(resource.fileId() == null ? 0L : resource.fileId())
                    .setDocumentId(resource.documentId() == null ? 0L : resource.documentId())
                    .setRevision(nvl(resource.revision()))
                    .setResultJson(writeJson(buildSubtreePayload(result)))
                    .build();
        } catch (Exception e) {
            log.error("[AgentToolJsonStructureHandler] jsonSubtreeRead failed: fileId={}, documentId={}, err={}",
                    request.getFileId(), request.getDocumentId(), e.getMessage(), e);
            return JsonSubtreeReadResponse.newBuilder()
                    .setSuccess(false)
                    .setResourceKind(resolveErrorResourceKind(request.getFileId(), request.getDocumentId()))
                    .setFileId(request.getFileId())
                    .setDocumentId(request.getDocumentId())
                    .setError(nvl(e.getMessage()))
                    .build();
        }
    }


    private ObjectNode buildPayload(JsonStructureService.JsonStructureResult result) {
        var resource = result.resource();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("resourceKind", nvl(resource.resourceKind()));
        payload.put("fileId", resource.fileId() == null ? 0L : resource.fileId());
        payload.put("documentId", resource.documentId() == null ? 0L : resource.documentId());
        payload.put("writable", resource.writable());
        payload.put("revision", nvl(resource.revision()));
        payload.put("sourceName", nvl(resource.sourceName()));
        payload.put("documentType", nvl(resource.documentType()));
        payload.put("path", nvl(result.path()));
        payload.put("nodeType", nvl(result.nodeType()));
        payload.put("truncated", result.truncated());
        payload.set("stats", result.stats() == null ? objectMapper.createObjectNode() : result.stats());
        payload.set("skeleton", result.skeleton() == null ? objectMapper.createObjectNode() : result.skeleton());

        ArrayNode children = objectMapper.createArrayNode();
        if (result.children() != null) {
            for (String child : result.children()) {
                if (child != null && !child.isBlank()) {
                    children.add(child);
                }
            }
        }
        payload.set("children", children);
        return payload;
    }

    private ObjectNode buildSubtreePayload(JsonSubtreeService.JsonSubtreeResult result) {
        var resource = result.resource();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("resourceKind", nvl(resource.resourceKind()));
        payload.put("fileId", resource.fileId() == null ? 0L : resource.fileId());
        payload.put("documentId", resource.documentId() == null ? 0L : resource.documentId());
        payload.put("writable", resource.writable());
        payload.put("revision", nvl(resource.revision()));
        payload.put("sourceName", nvl(resource.sourceName()));
        payload.put("documentType", nvl(resource.documentType()));
        payload.put("path", nvl(result.path()));
        payload.put("nodeType", nvl(result.nodeType()));
        payload.put("truncated", result.truncated());
        payload.put("returnedNodeCount", result.returnedNodeCount());
        payload.put("approxBytes", result.approxBytes());
        payload.set("value", result.value() == null ? NullNode.instance : result.value());
        return payload;
    }



    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json structure 响应序列化失败", e);
        }
    }

    private String resolveErrorResourceKind(long fileId, long documentId) {
        if (documentId > 0) {
            return "document";
        }
        if (fileId > 0) {
            return "file";
        }
        return "";
    }

    private String resolveErrorResourceKind(JsonStructureReadRequest request) {
        return resolveErrorResourceKind(request.getFileId(), request.getDocumentId());
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
