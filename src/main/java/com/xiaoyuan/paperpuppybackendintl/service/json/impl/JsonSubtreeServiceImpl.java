package com.xiaoyuan.paperpuppybackendintl.service.json.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonResourceService;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonSubtreeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

@Service
public class JsonSubtreeServiceImpl implements JsonSubtreeService {

    private static final int DEFAULT_MAX_BYTES = 12_000;
    private static final int DEFAULT_MAX_NODES = 400;

    private static final int MAX_ALLOWED_BYTES = 100_000;
    private static final int MAX_ALLOWED_NODES = 2_000;

    private final ObjectMapper objectMapper;
    private final JsonResourceService jsonResourceService;

    public JsonSubtreeServiceImpl(@Qualifier("jackson2Mapper") ObjectMapper objectMapper,
                                  JsonResourceService jsonResourceService) {
        this.objectMapper = objectMapper;
        this.jsonResourceService = jsonResourceService;
    }

    @Override
    public JsonSubtreeResult readSubtree(Long userId,
                                         Long projectId,
                                         Long fileId,
                                         Long documentId,
                                         String path,
                                         Integer maxBytes,
                                         Integer maxNodes) {
        JsonResourceService.JsonResourceSnapshot resource = jsonResourceService
                .readResource(userId, projectId, fileId, documentId);
        Limits limits = sanitizeLimits(maxBytes, maxNodes);
        String normalizedPath = normalizePath(path);
        JsonNode target = resolveNode(resource.root(), normalizedPath);

        TruncationState truncationState = new TruncationState();
        NodeBudget nodeBudget = new NodeBudget(limits.maxNodes);
        JsonNode boundedValue = copyWithinNodeBudget(target, nodeBudget, truncationState);
        boundedValue = trimToBytes(boundedValue, limits.maxBytes, truncationState);

        int approxBytes = serializedBytes(boundedValue);
        int returnedNodeCount = countNodes(boundedValue);
        return new JsonSubtreeResult(
                resource,
                normalizedPath,
                nodeTypeOf(target),
                truncationState.truncated,
                returnedNodeCount,
                approxBytes,
                boundedValue
        );
    }

    private JsonNode copyWithinNodeBudget(JsonNode node,
                                          NodeBudget nodeBudget,
                                          TruncationState truncationState) {
        if (node == null || node.isNull()) {
            return NullNode.instance;
        }
        if (nodeBudget.remainingNodes <= 0) {
            truncationState.truncated = true;
            return emptyLike(node);
        }
        nodeBudget.remainingNodes--;

        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                if (nodeBudget.remainingNodes <= 0) {
                    truncationState.truncated = true;
                    break;
                }
                Map.Entry<String, JsonNode> field = fields.next();
                out.set(field.getKey(), copyWithinNodeBudget(field.getValue(), nodeBudget, truncationState));
            }
            if (fields.hasNext()) {
                truncationState.truncated = true;
            }
            return out;
        }

        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                if (nodeBudget.remainingNodes <= 0) {
                    truncationState.truncated = true;
                    break;
                }
                out.add(copyWithinNodeBudget(item, nodeBudget, truncationState));
            }
            if (node.size() > out.size()) {
                truncationState.truncated = true;
            }
            return out;
        }

        return node.deepCopy();
    }

    private JsonNode trimToBytes(JsonNode node, int maxBytes, TruncationState truncationState) {
        JsonNode current = node == null ? NullNode.instance : node;
        while (serializedBytes(current) > maxBytes) {
            truncationState.truncated = true;
            if (current.isTextual()) {
                current = TextNode.valueOf(shrinkText(current.asText()));
                if (serializedBytes(current) <= maxBytes) {
                    break;
                }
                if (current.asText().isEmpty()) {
                    current = NullNode.instance;
                    break;
                }
                continue;
            }
            if (!trimOneStep(current)) {
                if (current.isObject()) {
                    ((ObjectNode) current).removeAll();
                } else if (current.isArray()) {
                    ((ArrayNode) current).removeAll();
                } else {
                    current = NullNode.instance;
                }
                break;
            }
        }
        return current;
    }

    private boolean trimOneStep(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            return trimObject((ObjectNode) node);
        }
        if (node.isArray()) {
            return trimArray((ArrayNode) node);
        }
        return false;
    }

    private boolean trimObject(ObjectNode node) {
        String lastField = lastFieldName(node);
        if (lastField == null) {
            return false;
        }
        JsonNode child = node.get(lastField);
        if (shrinkChild(node, lastField, child)) {
            return true;
        }
        node.remove(lastField);
        return true;
    }

    private boolean trimArray(ArrayNode node) {
        if (node.isEmpty()) {
            return false;
        }
        int lastIndex = node.size() - 1;
        JsonNode child = node.get(lastIndex);
        if (shrinkChild(node, lastIndex, child)) {
            return true;
        }
        node.remove(lastIndex);
        return true;
    }

    private boolean shrinkChild(ObjectNode parent, String fieldName, JsonNode child) {
        if (child == null || child.isNull()) {
            return false;
        }
        if (child.isTextual()) {
            String shrunk = shrinkText(child.asText());
            if (!shrunk.equals(child.asText())) {
                parent.put(fieldName, shrunk);
                return true;
            }
            return false;
        }
        if ((child.isObject() || child.isArray()) && trimOneStep(child)) {
            return true;
        }
        return false;
    }

    private boolean shrinkChild(ArrayNode parent, int index, JsonNode child) {
        if (child == null || child.isNull()) {
            return false;
        }
        if (child.isTextual()) {
            String shrunk = shrinkText(child.asText());
            if (!shrunk.equals(child.asText())) {
                parent.set(index, TextNode.valueOf(shrunk));
                return true;
            }
            return false;
        }
        if ((child.isObject() || child.isArray()) && trimOneStep(child)) {
            return true;
        }
        return false;
    }

    private String lastFieldName(ObjectNode node) {
        Iterator<String> fieldNames = node.fieldNames();
        String last = null;
        while (fieldNames.hasNext()) {
            last = fieldNames.next();
        }
        return last;
    }

    private String shrinkText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int targetLength = Math.max(0, (value.length() - 3) / 2);
        if (targetLength <= 0) {
            return "";
        }
        return value.substring(0, targetLength) + "...";
    }

    private int countNodes(JsonNode node) {
        if (node == null) {
            return 0;
        }
        if (!node.isContainerNode()) {
            return 1;
        }
        int count = 1;
        for (JsonNode child : node) {
            count += countNodes(child);
        }
        return count;
    }

    private Limits sanitizeLimits(Integer maxBytes, Integer maxNodes) {
        int normalizedMaxBytes = normalizePositive(maxBytes, DEFAULT_MAX_BYTES, MAX_ALLOWED_BYTES);
        int normalizedMaxNodes = normalizePositive(maxNodes, DEFAULT_MAX_NODES, MAX_ALLOWED_NODES);
        return new Limits(normalizedMaxBytes, normalizedMaxNodes);
    }

    private int normalizePositive(Integer value, int defaultValue, int maxValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "path 必须是合法的 JSON Pointer");
        }
        return trimmed;
    }

    private JsonNode resolveNode(JsonNode root, String path) {
        if ("/".equals(path)) {
            return root;
        }
        JsonNode current = root;
        for (String token : decodePointer(path)) {
            if (current == null || current.isNull()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "path 不存在: " + path);
            }
            if (current.isObject()) {
                current = current.get(token);
                if (current == null) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "path 不存在: " + path);
                }
                continue;
            }
            if (current.isArray()) {
                int index = parseArrayIndex(token, current.size(), path);
                current = current.get(index);
                continue;
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "path 父节点不是容器: " + path);
        }
        return current;
    }

    private Iterable<String> decodePointer(String path) {
        if ("/".equals(path)) {
            return java.util.List.of();
        }
        return Arrays.stream(path.substring(1).split("/", -1))
                .map(token -> token.replace("~1", "/").replace("~0", "~"))
                .toList();
    }

    private int parseArrayIndex(String token, int size, String path) {
        int index;
        try {
            index = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数组 path 索引非法: " + path);
        }
        if (index < 0 || index >= size) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数组 path 索引越界: " + path);
        }
        return index;
    }

    private String nodeTypeOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isIntegralNumber()) {
            return "integer";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return node.getNodeType().name().toLowerCase();
    }

    private JsonNode emptyLike(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.instance;
        }
        if (node.isObject()) {
            return objectMapper.createObjectNode();
        }
        if (node.isArray()) {
            return objectMapper.createArrayNode();
        }
        if (node.isTextual()) {
            return TextNode.valueOf("");
        }
        return NullNode.instance;
    }

    private int serializedBytes(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? NullNode.instance : node)
                    .getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "json subtree 序列化失败");
        }
    }

    private record Limits(int maxBytes, int maxNodes) {
    }

    private static final class NodeBudget {
        private int remainingNodes;

        private NodeBudget(int remainingNodes) {
            this.remainingNodes = remainingNodes;
        }
    }

    private static final class TruncationState {
        private boolean truncated;
    }
}
