package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolErrorSupport;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * 打开 backend 持久化的 artifact view projection/detail。
 */
@Component
public class AgentArtifactViewOpenTool extends Tool {
    public static final String TOOL_NAME = "agent_artifact_view_open";

    private static final int DEFAULT_MAX_CHARS = 8_000;
    private static final int HARD_MAX_CHARS = 30_000;

    private final BackendToolGrpcClient backendClient;

    public AgentArtifactViewOpenTool(ObjectMapper mapper, BackendToolGrpcClient backendClient) {
        super(mapper);
        this.backendClient = backendClient;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        if (context == null || context.userId() <= 0 || context.sessionId() <= 0 || context.projectId() <= 0) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "MISSING_CONTEXT",
                    "missing user/project/session context",
                    "This tool can only open views for the current Agent session/project.",
                    false);
        }

        String viewKey = firstText(arguments, "view_key", "viewKey", "read_key", "readKey");
        long documentId = optionalPositiveLong(arguments, "document_id", "documentId");
        String viewType = firstText(arguments, "view_type", "viewType");
        if (viewKey.isBlank() && documentId <= 0) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "MISSING_VIEW_SELECTOR",
                    "view_key or document_id is required",
                    "Use activeViews[].viewId/read_key from Persisted Artifact Projection, or pass document_id with optional view_type.",
                    false);
        }

        String mode = normalizeMode(firstText(arguments, "mode"));
        String path = firstText(arguments, "path", "json_pointer", "jsonPointer");
        int maxChars = resolveMaxChars(arguments);

        var response = backendClient.openAgentArtifactView(
                context.userId(),
                context.sessionId(),
                context.projectId(),
                viewKey,
                documentId,
                viewType,
                mode,
                path,
                maxChars
        );
        if (!response.getSuccess()) {
            String error = response.getError() == null || response.getError().isBlank()
                    ? "artifact view not found"
                    : response.getError();
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "ARTIFACT_VIEW_NOT_FOUND",
                    error,
                    "Use the current persisted artifact projection activeViews, or call a narrow read/search tool to rebuild the view.",
                    false);
        }
        if (response.getResultJson() == null || response.getResultJson().isBlank()) {
            return ToolErrorSupport.businessError(
                    mapper,
                    TOOL_NAME,
                    "EMPTY_ARTIFACT_VIEW",
                    "backend returned empty artifact view result",
                    "Call a narrow read/search tool to rebuild the view.",
                    false);
        }
        return mapper.readTree(response.getResultJson());
    }

    private int resolveMaxChars(JsonNode arguments) {
        JsonNode node = arguments == null ? null : arguments.get("max_chars");
        if (node == null || !node.canConvertToInt()) {
            node = arguments == null ? null : arguments.get("maxChars");
        }
        int requested = node == null || !node.canConvertToInt() ? DEFAULT_MAX_CHARS : node.asInt(DEFAULT_MAX_CHARS);
        if (requested <= 0) {
            return DEFAULT_MAX_CHARS;
        }
        return Math.min(requested, HARD_MAX_CHARS);
    }

    private static String normalizeMode(String mode) {
        String normalized = Objects.toString(mode, "summary").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "detail", "path" -> normalized;
            default -> "summary";
        };
    }

    private static long optionalPositiveLong(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return 0;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToLong()) {
                long id = value.asLong();
                if (id > 0) {
                    return id;
                }
            }
        }
        return 0;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }
}
