package com.xiaoyuan.paperpuppyagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.core.JsonValue;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCompactionItemParam;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 负责对话上下文转换：
 * - canonical conversation -> Responses API input
 * - 生成多模态用户消息
 * - 生成 tool_call_output input item
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentConversationMapper {
    private final ObjectMapper om;

    /**
     * 将 backend 传入的 canonical conversation v1（full_context_json）转换为 Responses API 的 input 列表。
     */
    public List<ResponseInputItem> buildInitialInputFromCanonical(AgentChatRequest req) {
        return buildInitialInputFromCanonical(req, "openai");
    }

    public List<ResponseInputItem> buildInitialInputFromCanonical(AgentChatRequest req, String selectedProvider) {
        ArrayList<ResponseInputItem> out = new ArrayList<>();
        String s = Objects.toString(req == null ? null : req.getFullContextJson(), "").trim();
        if (s.isBlank()) {
            return out;
        }
        try {
            JsonNode root = om.readTree(s);
            JsonNode items = root.isObject() ? root.get("items") : null;
            if (items == null || !items.isArray()) {
                return out;
            }

            // 防御兜底：预扫描，收集所有有对应 function_call_output（role=tool）的 callId
            // 防止孤立 function_call（无对应 output）导致 OpenAI 400: No tool output found
            java.util.Set<String> pairedCallIds = new java.util.HashSet<>();
            for (JsonNode it : items) {
                if (it == null || !it.isObject()) continue;
                String itRole = Objects.toString(it.get("role") == null ? null : it.get("role").asText(), "").trim();
                String itType = Objects.toString(it.get("type") == null ? null : it.get("type").asText(), "").trim();
                if ("tool_output".equalsIgnoreCase(itType) && "tool".equalsIgnoreCase(itRole)) {
                    String cid = Objects.toString(it.get("tool_call_id") == null ? null : it.get("tool_call_id").asText(), "").trim();
                    if (!cid.isBlank()) {
                        pairedCallIds.add(cid);
                    }
                }
            }

            for (JsonNode it : items) {
                if (it == null || !it.isObject()) {
                    continue;
                }
                String role = Objects.toString(it.get("role") == null ? null : it.get("role").asText(), "user").trim();
                String type = Objects.toString(it.get("type") == null ? null : it.get("type").asText(), "text").trim();

                if ("provider_context_window".equalsIgnoreCase(type)) {
                    if ("openai".equalsIgnoreCase(selectedProvider)) {
                        appendProviderContextWindow(out, it);
                    }
                    continue;
                }

                // 1) tool_output：恢复为 function_call / function_call_output
                if ("tool_output".equalsIgnoreCase(type)) {
                    // Both configured providers accept paired Responses function calls and outputs.
                    // Provider-specific compacted windows are still handled separately above.
                    String callId = Objects.toString(it.get("tool_call_id") == null ? null : it.get("tool_call_id").asText(), "")
                            .trim();
                    String name = Objects.toString(it.get("name") == null ? null : it.get("name").asText(), "").trim();
                    String raw = Objects.toString(it.get("raw_content") == null ? null : it.get("raw_content").asText(), "");

                    if (callId.isBlank()) {
                        continue;
                    }

                    if ("assistant".equalsIgnoreCase(role)) {
                        // 跳过孤立 function_call（无对应 output），避免 OpenAI 400 报错
                        if (!pairedCallIds.contains(callId)) {
                            log.warn("[Agent][Context] Skipping orphaned function_call with no output: callId={}, name={}", callId, name);
                            continue;
                        }
                        com.openai.models.responses.ResponseFunctionToolCall fc = com.openai.models.responses.ResponseFunctionToolCall
                                .builder()
                                .callId(callId)
                                .name(name)
                                .arguments(raw)
                                .build();
                        out.add(ResponseInputItem.ofFunctionCall(fc));
                    } else if ("tool".equalsIgnoreCase(role)) {
                        JsonNode resultNode = parseJsonLenient(raw);
                        out.add(functionCallOutputItem(callId, resultNode));
                    }
                    continue;
                }

                // 2) text：按 role 还原为 user/assistant 文本消息
                String content = Objects.toString(it.get("content") == null ? null : it.get("content").asText(), "").trim();
                if (content.isBlank()) {
                    continue;
                }

                EasyInputMessage.Role r = switch (role.toLowerCase()) {
                    case "assistant" -> EasyInputMessage.Role.ASSISTANT;
                    case "system" -> EasyInputMessage.Role.SYSTEM;
                    case "developer" -> EasyInputMessage.Role.DEVELOPER;
                    default -> EasyInputMessage.Role.USER;
                };

                out.add(ResponseInputItem.ofEasyInputMessage(
                        EasyInputMessage.builder().role(r).content(content).build()));
            }
        } catch (Exception e) {
            log.warn("[Agent][Context] full_context_json parse failed, ignore history: {}", e.getMessage());
        }
        return out;
    }

    private void appendProviderContextWindow(List<ResponseInputItem> out, JsonNode window) {
        String provider = textField(window, "provider");
        String format = textField(window, "format");
        if (!"openai".equalsIgnoreCase(provider)
                || !"responses_compacted_window_v1".equalsIgnoreCase(format)) {
            log.warn("[Agent][Context] skip unsupported provider_context_window: provider={}, format={}",
                    provider, format);
            return;
        }
        JsonNode items = window == null ? null : window.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            log.warn("[Agent][Context] skip empty provider_context_window");
            return;
        }

        int added = 0;
        for (JsonNode item : items) {
            ResponseInputItem restored = providerWindowItem(item);
            if (restored != null) {
                out.add(restored);
                added++;
            }
        }
        if (added <= 0) {
            log.warn("[Agent][Context] provider_context_window had no restorable items: count={}", items.size());
        }
    }

    private ResponseInputItem providerWindowItem(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String type = textField(item, "type");
        if (!"compaction".equalsIgnoreCase(type)) {
            log.debug("[Agent][Context] skip unsupported provider window item: type={}", type);
            return null;
        }
        String encryptedContent = firstNonBlank(
                textField(item, "encrypted_content"),
                textField(item, "encryptedContent"));
        if (encryptedContent.isBlank()) {
            log.warn("[Agent][Context] skip compaction item without encrypted_content");
            return null;
        }
        ResponseCompactionItemParam.Builder builder = ResponseCompactionItemParam.builder()
                .type(JsonValue.from("compaction"))
                .encryptedContent(encryptedContent);
        String id = textField(item, "id");
        if (!id.isBlank()) {
            builder.id(id);
        }
        return ResponseInputItem.ofCompaction(builder.build());
    }

    /**
     * 多模态用户消息：input_text + input_image (image_url)。
     */
    public ResponseInputItem userMessageItemWithImages(String text, List<String> imageUrls) {
        return userMessageItemWithAttachments(text, imageUrls, List.of());
    }

    public ResponseInputItem userMessageItemWithAttachments(String text, List<String> imageUrls, List<String> fileUrls) {
        String t = Objects.toString(text, "");
        List<String> urls = (imageUrls == null) ? List.of() : imageUrls;
        List<String> files = (fileUrls == null) ? List.of() : fileUrls;
        boolean hasImages = !urls.isEmpty();
        boolean hasFiles = !files.isEmpty();
        if (!hasImages && !hasFiles) {
            return userMessageItem(t);
        }

        ArrayList<ResponseInputContent> parts = new ArrayList<>();
        if (!t.isBlank()) {
            ResponseInputText it = ResponseInputText.builder().text(t).build();
            parts.add(ResponseInputContent.ofInputText(it));
        }
        // 去重 + 过滤空串，避免重复喂图
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String u0 : urls) {
            String u = Objects.toString(u0, "").trim();
            if (u.isBlank() || !seen.add(u)) {
                continue;
            }
            ResponseInputImage img = ResponseInputImage.builder()
                    .imageUrl(u)
                    .detail(ResponseInputImage.Detail.of("auto"))
                    .build();
            parts.add(ResponseInputContent.ofInputImage(img));
        }
        seen.clear();
        for (String u0 : files) {
            String u = Objects.toString(u0, "").trim();
            if (u.isBlank() || !seen.add(u)) {
                continue;
            }
            ResponseInputFile file = ResponseInputFile.builder()
                    .fileUrl(u)
                    .build();
            parts.add(ResponseInputContent.ofInputFile(file));
        }

        if (parts.isEmpty()) {
            ResponseInputText it = ResponseInputText.builder().text("Please analyze these attachments.").build();
            parts.add(ResponseInputContent.ofInputText(it));
        }

        ResponseInputItem.Message msg = ResponseInputItem.Message.builder()
                .role(ResponseInputItem.Message.Role.USER)
                .content(parts)
                .build();
        return ResponseInputItem.ofMessage(msg);
    }

    public ResponseInputItem functionCallOutputItem(String callId, JsonNode output) {
        Object v = (output == null) ? null : om.convertValue(output, Object.class);
        com.openai.models.responses.ResponseInputItem.FunctionCallOutput fco = com.openai.models.responses.ResponseInputItem.FunctionCallOutput
                .builder()
                .callId(Objects.toString(callId, ""))
                .outputAsJson(v)
                .build();
        return ResponseInputItem.ofFunctionCallOutput(fco);
    }

    /**
     * 注入系统提示（用于纠错/收敛）：不会直接展示给用户，但会影响下一轮模型决策。
     */
    public ResponseInputItem systemMessageItem(String text) {
        EasyInputMessage msg = EasyInputMessage.builder()
                .role(EasyInputMessage.Role.SYSTEM)
                .content(Objects.toString(text, ""))
                .build();
        return ResponseInputItem.ofEasyInputMessage(msg);
    }

    /** 从工具结果中抽取 image_urls，并兼容旧的单值 image_url。 */
    public List<String> extractImageUrlsFromToolResult(JsonNode result) {
        if (result == null || !result.isObject()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        JsonNode many = result.get("image_urls");
        if (many != null && many.isArray()) {
            for (JsonNode item : many) {
                addUrl(urls, item);
            }
        }
        addUrl(urls, result.get("image_url"));
        return List.copyOf(urls);
    }

    private static void addUrl(java.util.Set<String> urls, JsonNode node) {
        if (node != null && node.isTextual()) {
            String value = node.asText("").trim();
            if (!value.isBlank()) {
                urls.add(value);
            }
        }
    }

    /**
     * 从工具结果中抽取 file_url 字段（file_read_url 工具返回）。
     */
    public List<String> extractFileUrlsFromToolResult(JsonNode result) {
        if (result == null || !result.isObject()) {
            return List.of();
        }
        JsonNode n = result.get("file_url");
        if (n == null) {
            return List.of();
        }
        String u = n.asText("").trim();
        if (u.isBlank()) {
            return List.of();
        }
        return List.of(u);
    }

    /**
     * 多模态用户消息：input_text + input_file (file_url)。
     * 用于将 file_read_url 工具返回的 URL 作为 input_file 注入下一轮模型输入。
     */
    public ResponseInputItem userMessageItemWithFiles(String text, List<String> fileUrls) {
        return userMessageItemWithAttachments(text, List.of(), fileUrls);
    }

    private JsonNode parseJsonLenient(String raw) {
        String s = Objects.toString(raw, "").trim();
        if (s.isBlank()) {
            return om.createObjectNode();
        }
        try {
            return om.readTree(s);
        } catch (Exception e) {
            ObjectNode n = om.createObjectNode();
            n.put("raw", s);
            return n;
        }
    }

    private ResponseInputItem userMessageItem(String text) {
        EasyInputMessage msg = EasyInputMessage.builder()
                .role(EasyInputMessage.Role.USER)
                .content(Objects.toString(text, ""))
                .build();
        return ResponseInputItem.ofEasyInputMessage(msg);
    }

    private static String textField(JsonNode node, String field) {
        if (node == null || field == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }

    private static String firstNonBlank(String first, String second) {
        String a = Objects.toString(first, "").trim();
        return a.isBlank() ? Objects.toString(second, "").trim() : a;
    }
}
