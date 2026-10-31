package com.xiaoyuan.paperpuppybackendintl.service.json.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonResourceService;
import com.xiaoyuan.paperpuppybackendintl.service.json.JsonStructureService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class JsonStructureServiceImpl implements JsonStructureService {

    private static final int DEFAULT_MAX_DEPTH = 4;
    private static final int DEFAULT_SAMPLE_ARRAY_ITEMS = 3;
    private static final int DEFAULT_MAX_KEYS_PER_OBJECT = 80;

    private static final int MAX_ALLOWED_DEPTH = 8;
    private static final int MAX_ALLOWED_SAMPLE_ARRAY_ITEMS = 10;
    private static final int MAX_ALLOWED_KEYS_PER_OBJECT = 120;

    private final ObjectMapper objectMapper;
    private final JsonResourceService jsonResourceService;

    public JsonStructureServiceImpl(@Qualifier("jackson2Mapper") ObjectMapper objectMapper,
                                    JsonResourceService jsonResourceService) {
        this.objectMapper = objectMapper;
        this.jsonResourceService = jsonResourceService;
    }

    @Override
    public JsonStructureResult readStructure(Long userId,
                                             Long projectId,
                                             Long fileId,
                                             Long documentId,
                                             String path,
                                             Integer maxDepth,
                                             Integer sampleArrayItems,
                                             Integer maxKeysPerObject) {
        JsonResourceService.JsonResourceSnapshot resource = jsonResourceService
                .readResource(userId, projectId, fileId, documentId);
        Limits limits = sanitizeLimits(maxDepth, sampleArrayItems, maxKeysPerObject);
        String normalizedPath = normalizePath(path);
        JsonNode target = resolveNode(resource.root(), normalizedPath);

        StructureStats stats = new StructureStats();
        accumulateStats(target, 1, stats);

        TruncationState truncationState = new TruncationState();
        JsonNode skeleton = buildSkeleton(target, 0, limits, truncationState);
        List<String> children = listChildren(target, normalizedPath, limits, truncationState);

        ObjectNode statsNode = objectMapper.createObjectNode();
        statsNode.put("approx_depth", stats.approxDepth);
        statsNode.put("object_count", stats.objectCount);
        statsNode.put("array_count", stats.arrayCount);
        statsNode.put("scalar_count", stats.scalarCount);
        statsNode.put("node_count", stats.nodeCount);

        return new JsonStructureResult(
                resource,
                normalizedPath,
                nodeTypeOf(target),
                truncationState.truncated,
                statsNode,
                skeleton,
                children
        );
    }

    private JsonNode buildSkeleton(JsonNode node, int depth, Limits limits, TruncationState truncationState) {
        if (node == null || node.isNull()) {
            return TextNode.valueOf("null");
        }
        if (node.isObject()) {
            return buildObjectSkeleton(node, depth, limits, truncationState);
        }
        if (node.isArray()) {
            return buildArraySkeleton((ArrayNode) node, depth, limits, truncationState);
        }
        return TextNode.valueOf(nodeTypeOf(node));
    }

    private JsonNode buildObjectSkeleton(JsonNode node,
                                         int depth,
                                         Limits limits,
                                         TruncationState truncationState) {
        if (depth >= limits.maxDepth) {
            if (node.size() > 0) {
                truncationState.truncated = true;
            }
            return TextNode.valueOf("object");
        }

        ObjectNode out = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        int count = 0;
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (count >= limits.maxKeysPerObject) {
                truncationState.truncated = true;
                break;
            }
            out.set(field.getKey(), buildSkeleton(field.getValue(), depth + 1, limits, truncationState));
            count++;
        }
        return out;
    }

    private JsonNode buildArraySkeleton(ArrayNode array,
                                        int depth,
                                        Limits limits,
                                        TruncationState truncationState) {
        if (depth >= limits.maxDepth) {
            if (!array.isEmpty()) {
                truncationState.truncated = true;
            }
            return TextNode.valueOf(arrayTypeOf(array, limits));
        }

        ObjectNode out = objectMapper.createObjectNode();
        int size = array.size();
        int sampleCount = Math.min(size, limits.sampleArrayItems);
        out.put("$type", arrayTypeOf(array, limits));
        out.put("$length", size);
        out.put("$sampleCount", sampleCount);

        if (size == 0) {
            return out;
        }
        if (size > sampleCount) {
            truncationState.truncated = true;
        }

        boolean allSampledObjects = true;
        ArrayNode sampleTypes = objectMapper.createArrayNode();
        for (int i = 0; i < sampleCount; i++) {
            JsonNode item = array.get(i);
            sampleTypes.add(shapeOf(item, limits));
            if (item == null || !item.isObject()) {
                allSampledObjects = false;
            }
        }

        if (allSampledObjects) {
            out.set("$itemSkeleton", buildSampledObjectItemSkeleton(array, sampleCount, limits, truncationState));
        } else {
            out.set("$sampleTypes", sampleTypes);
        }
        return out;
    }

    private ObjectNode buildSampledObjectItemSkeleton(ArrayNode array,
                                                      int sampleCount,
                                                      Limits limits,
                                                      TruncationState truncationState) {
        LinkedHashMap<String, LinkedHashSet<String>> mergedTypes = new LinkedHashMap<>();
        for (int i = 0; i < sampleCount; i++) {
            JsonNode item = array.get(i);
            if (item == null || !item.isObject()) {
                continue;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!mergedTypes.containsKey(field.getKey()) && mergedTypes.size() >= limits.maxKeysPerObject) {
                    truncationState.truncated = true;
                    continue;
                }
                mergedTypes.computeIfAbsent(field.getKey(), ignored -> new LinkedHashSet<>())
                        .add(shapeOf(field.getValue(), limits));
            }
        }

        ObjectNode out = objectMapper.createObjectNode();
        for (Map.Entry<String, LinkedHashSet<String>> entry : mergedTypes.entrySet()) {
            out.put(entry.getKey(), mergeTypeNames(entry.getValue()));
        }
        return out;
    }

    private List<String> listChildren(JsonNode node,
                                      String basePath,
                                      Limits limits,
                                      TruncationState truncationState) {
        List<String> children = new ArrayList<>();
        if (node == null || node.isNull()) {
            return children;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            int count = 0;
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (count >= limits.maxKeysPerObject) {
                    truncationState.truncated = true;
                    break;
                }
                children.add(childPath(basePath, fieldName));
                count++;
            }
            return children;
        }
        if (!node.isArray()) {
            return children;
        }
        int size = node.size();
        int sampleCount = Math.min(size, limits.sampleArrayItems);
        for (int i = 0; i < sampleCount; i++) {
            children.add(childPath(basePath, String.valueOf(i)));
        }
        if (size > sampleCount) {
            truncationState.truncated = true;
        }
        return children;
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

    private void accumulateStats(JsonNode node, int depth, StructureStats stats) {
        if (node == null) {
            return;
        }
        stats.nodeCount++;
        stats.approxDepth = Math.max(stats.approxDepth, depth);
        if (node.isObject()) {
            stats.objectCount++;
            node.elements().forEachRemaining(child -> accumulateStats(child, depth + 1, stats));
            return;
        }
        if (node.isArray()) {
            stats.arrayCount++;
            node.elements().forEachRemaining(child -> accumulateStats(child, depth + 1, stats));
            return;
        }
        stats.scalarCount++;
    }

    private Limits sanitizeLimits(Integer maxDepth, Integer sampleArrayItems, Integer maxKeysPerObject) {
        int normalizedDepth = normalizePositive(maxDepth, DEFAULT_MAX_DEPTH, MAX_ALLOWED_DEPTH);
        int normalizedSampleArrayItems = normalizePositive(
                sampleArrayItems,
                DEFAULT_SAMPLE_ARRAY_ITEMS,
                MAX_ALLOWED_SAMPLE_ARRAY_ITEMS
        );
        int normalizedMaxKeys = normalizePositive(
                maxKeysPerObject,
                DEFAULT_MAX_KEYS_PER_OBJECT,
                MAX_ALLOWED_KEYS_PER_OBJECT
        );
        return new Limits(normalizedDepth, normalizedSampleArrayItems, normalizedMaxKeys);
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

    private List<String> decodePointer(String path) {
        if ("/".equals(path)) {
            return List.of();
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

    private String arrayTypeOf(ArrayNode array, Limits limits) {
        if (array == null || array.isEmpty()) {
            return "array<empty>";
        }
        int sampleCount = Math.min(array.size(), limits.sampleArrayItems);
        LinkedHashSet<String> itemTypes = new LinkedHashSet<>();
        for (int i = 0; i < sampleCount; i++) {
            itemTypes.add(shapeOf(array.get(i), limits));
        }
        return "array<" + mergeTypeNames(itemTypes) + ">";
    }

    private String shapeOf(JsonNode node, Limits limits) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isArray()) {
            return arrayTypeOf((ArrayNode) node, limits);
        }
        return nodeTypeOf(node);
    }

    private String mergeTypeNames(Iterable<String> types) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String type : types) {
            if (type != null && !type.isBlank()) {
                normalized.add(type);
            }
        }
        if (normalized.isEmpty()) {
            return "unknown";
        }
        if (normalized.size() == 1) {
            return normalized.iterator().next();
        }
        return "mixed:" + String.join("|", normalized);
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

    private String childPath(String basePath, String token) {
        String encodedToken = token.replace("~", "~0").replace("/", "~1");
        if ("/".equals(basePath)) {
            return "/" + encodedToken;
        }
        return basePath + "/" + encodedToken;
    }

    private record Limits(int maxDepth, int sampleArrayItems, int maxKeysPerObject) {
    }

    private static final class StructureStats {
        private int approxDepth;
        private int objectCount;
        private int arrayCount;
        private int scalarCount;
        private int nodeCount;
    }

    private static final class TruncationState {
        private boolean truncated;
    }
}
