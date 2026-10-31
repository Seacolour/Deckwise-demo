package com.xiaoyuan.paperpuppyagent.core.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.Locale;
import java.util.Objects;

/**
 * 统一构造工具错误回灌 JSON，增强模型的自诊断能力。
 */
public final class ToolErrorSupport {

    private ToolErrorSupport() {
    }

    public static ObjectNode fromException(ObjectMapper om, String functionName, Throwable throwable) {
        Throwable root = rootCause(throwable);
        StatusRuntimeException grpc = findGrpcStatus(throwable);
        String grpcStatus = grpc == null ? null : grpc.getStatus().getCode().name();
        String message = firstNonBlank(
                root == null ? null : root.getMessage(),
                throwable == null ? null : throwable.getMessage(),
                "tool failed"
        );
        String errorCode = classify(functionName, grpcStatus, message);
        String hint = hint(functionName, errorCode);

        ObjectNode err = om.createObjectNode();
        err.put("success", false);
        err.put("error", "tool_failed");
        err.put("function_name", Objects.toString(functionName, ""));
        err.put("error_code", errorCode);
        err.put("message", message);
        err.put("retryable", isRetryable(grpcStatus));
        if (grpcStatus != null) {
            err.put("grpc_status", grpcStatus);
        }
        if (root != null) {
            err.put("exception", root.getClass().getName());
        }
        if (hint != null && !hint.isBlank()) {
            err.put("hint", hint);
        }
        return err;
    }

    public static ObjectNode businessError(ObjectMapper om,
                                           String functionName,
                                           String errorCode,
                                           String message,
                                           String hint,
                                           boolean retryable) {
        ObjectNode err = om.createObjectNode();
        err.put("success", false);
        err.put("error", "tool_failed");
        err.put("function_name", Objects.toString(functionName, ""));
        err.put("error_code", Objects.toString(errorCode, "TOOL_ERROR"));
        err.put("message", Objects.toString(message, "tool failed"));
        err.put("retryable", retryable);
        if (hint != null && !hint.isBlank()) {
            err.put("hint", hint);
        }
        return err;
    }

    public static ObjectNode businessErrorForMessage(ObjectMapper om,
                                                     String functionName,
                                                     String message) {
        String normalizedMessage = Objects.toString(message, "tool failed");
        String errorCode = classify(functionName, null, normalizedMessage);
        String hint = hint(functionName, errorCode);
        return businessError(om, functionName, errorCode, normalizedMessage, hint, false);
    }

    private static String classify(String functionName, String grpcStatus, String message) {
        String fn = Objects.toString(functionName, "").trim().toLowerCase(Locale.ROOT);
        String msg = Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);
        String status = Objects.toString(grpcStatus, "").trim().toUpperCase(Locale.ROOT);

        if ("NOT_FOUND".equals(status)) return "NOT_FOUND";
        if ("DEADLINE_EXCEEDED".equals(status)) return "DEADLINE_EXCEEDED";
        if ("UNAVAILABLE".equals(status)) return "SERVICE_UNAVAILABLE";
        if ("FAILED_PRECONDITION".equals(status)) return "FAILED_PRECONDITION";

        if (isJsonReadTool(fn)) {
            if (msg.contains("file_id 与 document_id 必须且只能传一个")) return "INVALID_RESOURCE_TARGET";
            if (msg.contains("path 不存在")) return "JSON_PATH_NOT_FOUND";
            if (msg.contains("path 父节点不是容器")) return "JSON_PATH_NOT_CONTAINER";
            if (msg.contains("json pointer") || msg.contains("path 必须是合法")) return "INVALID_JSON_POINTER";
            if (msg.contains("目标资源不是合法 json") || msg.contains("不是合法 json")) return "INVALID_JSON_RESOURCE";
        }
        if ("INVALID_ARGUMENT".equals(status)) return "INVALID_ARGUMENT";
        return "TOOL_EXECUTION_FAILED";
    }

    private static String hint(String functionName, String errorCode) {
        String fn = Objects.toString(functionName, "").trim().toLowerCase(Locale.ROOT);
        return switch (Objects.toString(errorCode, "")) {
            case "INVALID_RESOURCE_TARGET" -> "请在 file_id 与 document_id 中二选一；普通 JSON 文件传 file_id，受支持的文档传 document_id。";
            case "JSON_PATH_NOT_FOUND" -> "先用 json_structure_read 查看当前节点下的 children，再选择正确的 JSON Pointer 路径。";
            case "JSON_PATH_NOT_CONTAINER" -> "当前路径的父节点不是 object/array；请先读取上一级路径确认结构。";
            case "INVALID_JSON_POINTER" -> "path 必须是合法 JSON Pointer，例如 /cards/0/title；若不确定路径，请先调用 json_structure_read。";
            case "INVALID_JSON_RESOURCE" -> "目标资源不是合法 JSON；请先用 file_list 确认文件类型。";
            case "SERVICE_UNAVAILABLE", "DEADLINE_EXCEEDED" ->
                    "服务暂时不可用，可稍后重试；不要用相同错误参数反复调用。";
            default -> genericHint(fn);
        };
    }

    private static boolean isJsonReadTool(String functionName) {
        return functionName.startsWith("json_structure_") || functionName.startsWith("json_subtree_");
    }

    private static String genericHint(String functionName) {
        if (isJsonReadTool(functionName)) {
            return "先用 json_structure_read 查看骨架和 children，再用 json_subtree_read 逐层下钻。";
        }
        return "不要直接重复相同参数；请根据错误信息调整参数，或先用读取工具获取更准确的上下文。";
    }

    private static boolean isRetryable(String grpcStatus) {
        if (grpcStatus == null || grpcStatus.isBlank()) {
            return false;
        }
        return switch (Status.Code.valueOf(grpcStatus)) {
            case UNAVAILABLE, DEADLINE_EXCEEDED, ABORTED, RESOURCE_EXHAUSTED -> true;
            default -> false;
        };
    }

    private static StatusRuntimeException findGrpcStatus(Throwable throwable) {
        Throwable cur = throwable;
        while (cur != null) {
            if (cur instanceof StatusRuntimeException sre) {
                return sre;
            }
            cur = cur.getCause();
        }
        return null;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cur = throwable;
        Throwable last = throwable;
        while (cur != null) {
            last = cur;
            cur = cur.getCause();
        }
        return last;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
