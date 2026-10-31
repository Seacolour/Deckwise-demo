package com.xiaoyuan.paperpuppyagent.core.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppyagent.core.tool.Tool;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSession;
import com.xiaoyuan.paperpuppyagent.core.tool.document.DocumentEditSessionManager;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetDocumentResponse;
import com.xiaoyuan.paperpuppyagent.grpc.client.BackendToolGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档定位工具。
 *
 * <p>
 * 用途：在文档中定位指定内容，返回匹配位置的精确行号和原文行内容（before_lines）。
 * 解决 AI 猜测行号的问题：先通过此工具定位，再使用 document_diff 进行编辑。
 * </p>
 *
 * <p>
 * 工作流程：
 * 1. 获取文档内容（优先使用 Agent 工作副本）
 * 2. 规范化换行符并分割为行数组
 * 3. 搜索包含 query 的行
 * 4. 返回匹配位置及上下文行（before_lines 为“可直接用于 diff 的最小唯一片段”；context_lines 仅用于阅读确认）
 * </p>
 */
@Slf4j
@Component
public class DocumentLocateTool extends Tool {

    private static final int DEFAULT_MAX_RESULTS = 3;
    private static final int DEFAULT_CONTEXT_LINES = 3;
    private static final int MAX_PREVIEW_LENGTH = 200;

    private final BackendToolGrpcClient backendClient;
    private final DocumentEditSessionManager sessionManager;

    public DocumentLocateTool(ObjectMapper mapper,
            BackendToolGrpcClient backendClient,
            DocumentEditSessionManager sessionManager) {
        super(mapper);
        this.backendClient = backendClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String name() {
        return "document_locate";
    }

    @Override
    public JsonNode execute(JsonNode arguments, ToolExecutionContext context) throws Exception {
        long userId = context.userId();
        long projectId = context.projectId();
        long sessionId = context.sessionId();

        if (userId <= 0 || projectId <= 0 || sessionId <= 0) {
            throw new IllegalStateException("工具执行上下文无效");
        }

        // 获取参数
        if (!arguments.has("document_id")) {
            throw new IllegalArgumentException("缺少必需参数: document_id");
        }
        long documentId = arguments.get("document_id").asLong();

        if (!arguments.has("query") || arguments.get("query").asText().isBlank()) {
            throw new IllegalArgumentException("缺少必需参数: query");
        }
        String query = arguments.get("query").asText().trim();

        int maxResults = arguments.has("max_results")
                ? arguments.get("max_results").asInt()
                : DEFAULT_MAX_RESULTS;
        maxResults = Math.max(1, Math.min(maxResults, 10)); // 限制 1-10

        int contextLines = arguments.has("context_lines")
                ? arguments.get("context_lines").asInt()
                : DEFAULT_CONTEXT_LINES;
        contextLines = Math.max(0, Math.min(contextLines, 10)); // 限制 0-10

        log.info("[DocumentLocateTool] Execute: userId={}, documentId={}, query='{}', maxResults={}, contextLines={}",
                userId, documentId, query, maxResults, contextLines);

        // 获取文档内容（优先使用工作副本）
        String content = getDocumentContent(userId, projectId, sessionId, documentId);
        if (content == null || content.isEmpty()) {
            ObjectNode result = mapper.createObjectNode();
            result.put("success", false);
            result.put("document_id", documentId);
            result.put("message", "文档内容为空");
            return result;
        }

        // 规范化换行符并分割为行
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        int totalLines = lines.length;

        // 搜索匹配
        List<Match> matches = findMatches(lines, query, maxResults, contextLines);

        // 构建返回结果
        ObjectNode result = mapper.createObjectNode();
        result.put("success", true);
        result.put("document_id", documentId);
        result.put("line_mode", "physical_newlines");
        result.put("total_lines", totalLines);

        ArrayNode matchesNode = mapper.createArrayNode();
        int matchIndex = 0;
        for (Match m : matches) {
            ObjectNode matchNode = mapper.createObjectNode();
            matchNode.put("match_id", "m" + (++matchIndex));
            // start_line/end_line 对应 before_lines（可直接用于 diff 的权威片段）
            matchNode.put("start_line", m.beforeStartLine());
            matchNode.put("end_line", m.beforeEndLine());

            // before_lines：尽量短且唯一，推荐直接用于 document_diff（无行号模式 or 行号模式）
            ArrayNode beforeLines = mapper.createArrayNode();
            for (String line : m.beforeLines()) {
                beforeLines.add(line);
            }
            matchNode.set("before_lines", beforeLines);

            // context：仅用于阅读确认（不建议直接用来替换）
            matchNode.put("context_start_line", m.contextStartLine());
            matchNode.put("context_end_line", m.contextEndLine());
            ArrayNode ctxLines = mapper.createArrayNode();
            for (String line : m.contextLines()) {
                ctxLines.add(line);
            }
            matchNode.set("context_lines", ctxLines);
            matchNode.put("before_is_unique", m.beforeIsUnique());

            // preview: 匹配行的预览
            matchNode.put("preview", m.preview());

            matchesNode.add(matchNode);
        }
        result.set("matches", matchesNode);

        String message = matches.isEmpty()
                ? "未找到匹配内容，请检查 query 是否正确"
                : "找到 " + matches.size() + " 处匹配";
        if (matches.size() > 1) {
            message += "。建议使用更具体的 query 或选择合适的 match_id 进行编辑。";
        }
        result.put("message", message);

        log.info("[DocumentLocateTool] Found {} matches for query '{}' in document {}",
                matches.size(), query, documentId);

        return result;
    }

    /**
     * 获取文档内容（优先工作副本，否则从 Backend 获取）
     */
    private String getDocumentContent(long userId, long projectId, long sessionId, long documentId) {
        // 优先检查 Agent 工作副本
        DocumentEditSession session = sessionManager.get(sessionId, documentId);
        if (session != null) {
            log.info("[DocumentLocateTool] Using agent working copy for document {}", documentId);
            return session.getWorkingContent();
        }

        // 从 Backend 获取
        try {
            log.info("[DocumentLocateTool] Fetching document {} from Backend", documentId);
            GetDocumentResponse doc = backendClient.getDocument(userId, projectId, documentId, true);
            return doc.getContent();
        } catch (Exception e) {
            log.error("[DocumentLocateTool] Failed to fetch document {}: {}", documentId, e.getMessage());
            return null;
        }
    }

    /**
     * 在行数组中搜索包含 query 的匹配。
     *
     * @param lines        行数组
     * @param query        搜索关键词
     * @param maxResults   最大返回数
     * @param contextLines 上下文行数
     * @return 匹配列表
     */
    private List<Match> findMatches(String[] lines, String query, int maxResults, int contextLines) {
        List<Match> matches = new ArrayList<>();
        String queryLower = query.toLowerCase();

        int i = 0;
        while (i < lines.length && matches.size() < maxResults) {
            // 查找包含 query 的行（大小写不敏感）
            if (lines[i].toLowerCase().contains(queryLower)) {
                // 找到匹配行，扩展上下文
                int matchLineIndex = i;

                // 计算范围（包含上下文）
                int startIndex = Math.max(0, matchLineIndex - contextLines);
                int endIndex = Math.min(lines.length - 1, matchLineIndex + contextLines);

                // context_lines：用于阅读确认
                List<String> context = new ArrayList<>();
                for (int j = startIndex; j <= endIndex; j++) {
                    context.add(lines[j]);
                }

                // before_lines：尽量短且唯一（默认从命中行开始，必要时在上下文范围内扩展）
                UniqueWindow win = findMinimalUniqueWindow(lines, matchLineIndex, startIndex, endIndex);
                List<String> beforeLines = new ArrayList<>();
                for (int j = win.start; j <= win.end; j++) {
                    beforeLines.add(lines[j]);
                }

                // 生成预览（匹配行的截断版本）
                String preview = lines[matchLineIndex];
                if (preview.length() > MAX_PREVIEW_LENGTH) {
                    preview = preview.substring(0, MAX_PREVIEW_LENGTH) + "...";
                }

                matches.add(new Match(
                        // before window（1-based）
                        win.start + 1,
                        win.end + 1,
                        beforeLines,
                        // context window（1-based）
                        startIndex + 1,
                        endIndex + 1,
                        context,
                        win.unique,
                        preview));

                // 跳过已处理的上下文行，避免重复匹配
                i = endIndex + 1;
            } else {
                i++;
            }
        }

        return matches;
    }

    /**
     * 在给定上下文边界内，寻找包含 matchIndex 的“最短且唯一”的窗口。
     * 唯一性定义：窗口行序列在全文 lines 中作为连续子序列出现次数恰好为 1。
     * 若在上下文范围内无法做到唯一，则返回上下文范围内尽量扩展后的窗口，并标记 unique=false。
     */
    private UniqueWindow findMinimalUniqueWindow(String[] lines, int matchIndex, int minBound, int maxBound) {
        int l = matchIndex;
        int r = matchIndex;
        if (countSubsequenceMatches(lines, l, r) == 1) {
            return new UniqueWindow(l, r, true);
        }
        boolean expandLeft = true;
        while ((l > minBound || r < maxBound) && (r - l) < 20) { // 防御：窗口最大 21 行
            if (expandLeft && l > minBound) {
                l--;
            } else if (!expandLeft && r < maxBound) {
                r++;
            } else if (l > minBound) {
                l--;
            } else if (r < maxBound) {
                r++;
            }
            expandLeft = !expandLeft;
            if (countSubsequenceMatches(lines, l, r) == 1) {
                return new UniqueWindow(l, r, true);
            }
        }
        return new UniqueWindow(l, r, false);
    }

    /**
     * 统计窗口 [start,end] 在全文中作为连续子序列出现次数（>1 时可提前返回）。
     */
    private int countSubsequenceMatches(String[] lines, int start, int end) {
        if (lines == null || lines.length == 0 || start < 0 || end < start || end >= lines.length) {
            return 0;
        }
        int patternLen = end - start + 1;
        int count = 0;
        outer: for (int i = 0; i <= lines.length - patternLen; i++) {
            for (int j = 0; j < patternLen; j++) {
                if (!lines[i + j].equals(lines[start + j])) {
                    continue outer;
                }
            }
            count++;
            if (count > 1) {
                return count;
            }
        }
        return count;
    }

    private record UniqueWindow(int start, int end, boolean unique) {
    }

    /**
     * 匹配结果
     */
    private record Match(
            int beforeStartLine, // 1-based
            int beforeEndLine, // 1-based
            List<String> beforeLines,
            int contextStartLine, // 1-based
            int contextEndLine, // 1-based
            List<String> contextLines,
            boolean beforeIsUnique,
            String preview) {
    }
}
