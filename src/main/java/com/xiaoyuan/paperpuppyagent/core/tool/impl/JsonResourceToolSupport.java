package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolErrorSupport;

final class JsonResourceToolSupport {

    private JsonResourceToolSupport() {
    }

    static long optionalFileId(JsonNode arguments) {
        if (arguments == null || !arguments.has("file_id")) {
            return 0L;
        }
        long fileId = arguments.path("file_id").asLong(0L);
        if (fileId < 0) {
            throw new IllegalArgumentException("无效的 file_id: " + arguments.path("file_id").asText());
        }
        return fileId;
    }

    static long optionalDocumentId(JsonNode arguments) {
        if (arguments == null || !arguments.has("document_id")) {
            return 0L;
        }
        long documentId = arguments.path("document_id").asLong(0L);
        if (documentId < 0) {
            throw new IllegalArgumentException("无效的 document_id: " + arguments.path("document_id").asText());
        }
        return documentId;
    }

    static void requireSingleResourceTarget(long fileId, long documentId) {
        boolean hasFile = fileId > 0;
        boolean hasDocument = documentId > 0;
        if (hasFile == hasDocument) {
            throw new IllegalArgumentException("file_id 与 document_id 必须且只能传一个");
        }
    }

    static String optionalPath(JsonNode arguments) {
        if (arguments == null || !arguments.has("path")) {
            return "/";
        }
        String path = arguments.path("path").asText("/").trim();
        return path.isBlank() ? "/" : path;
    }

    static int optionalPositiveInt(JsonNode arguments, String fieldName, int defaultValue) {
        if (arguments == null || !arguments.has(fieldName)) {
            return defaultValue;
        }
        int value = arguments.path(fieldName).asInt(defaultValue);
        return value > 0 ? value : defaultValue;
    }

    static JsonNode parseResultJson(ObjectMapper mapper, String resultJson) throws Exception {
        if (resultJson == null || resultJson.isBlank()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(resultJson);
    }

    static ObjectNode buildFailure(ObjectMapper mapper, String functionName, String message) {
        return ToolErrorSupport.businessErrorForMessage(mapper, functionName, message);
    }

    static ObjectNode buildEnvelope(ObjectMapper mapper, long fileId, long documentId, JsonNode payload) {
        ObjectNode result = mapper.createObjectNode();
        result.put("success", true);
        if (fileId > 0) {
            result.put("file_id", fileId);
        }
        if (documentId > 0) {
            result.put("document_id", documentId);
        }

        if (payload == null || payload.isNull()) {
            return result;
        }
        if (!payload.isObject()) {
            result.set("result", payload);
            return result;
        }

        copyIfPresent(result, "resource_kind", payload, "resourceKind");
        copyIfPresent(result, "source_name", payload, "sourceName");
        copyIfPresent(result, "document_type", payload, "documentType");
        copyIfPresent(result, "path", payload, "path");
        copyIfPresent(result, "node_type", payload, "nodeType");
        copyIfPresent(result, "revision", payload, "revision");
        copyIfPresent(result, "canonical_kind", payload, "canonicalKind");
        copyIfPresent(result, "entry_path", payload, "entryPath");
        if (payload.has("truncated")) {
            result.put("truncated", payload.path("truncated").asBoolean(false));
        }
        copyNodeIfPresent(result, "stats", payload, "stats");
        copyNodeIfPresent(result, "skeleton", payload, "skeleton");
        copyNodeIfPresent(result, "children", payload, "children");
        copyNodeIfPresent(result, "value", payload, "value");
        copyNodeIfPresent(result, "summary", payload, "summary");
        if (payload.has("returnedNodeCount")) {
            result.put("returned_node_count", payload.path("returnedNodeCount").asInt(0));
        }
        if (payload.has("approxBytes")) {
            result.put("approx_bytes", payload.path("approxBytes").asInt(0));
        }
        return result;
    }

    private static void copyIfPresent(ObjectNode target, String targetField, JsonNode payload, String sourceField) {
        if (!payload.has(sourceField)) {
            return;
        }
        JsonNode source = payload.get(sourceField);
        if (source == null || source.isNull()) {
            return;
        }
        if (source.isTextual()) {
            String value = source.asText("");
            if (!value.isBlank()) {
                target.put(targetField, value);
            }
            return;
        }
        target.set(targetField, source.deepCopy());
    }

    private static void copyNodeIfPresent(ObjectNode target, String targetField, JsonNode payload, String sourceField) {
        if (!payload.has(sourceField)) {
            return;
        }
        JsonNode source = payload.get(sourceField);
        if (source == null || source.isNull()) {
            return;
        }
        target.set(targetField, source.deepCopy());
    }
}
