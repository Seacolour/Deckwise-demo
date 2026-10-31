package com.xiaoyuan.paperpuppybackendintl.utils.ai;

/**
 * 纯工具类：不关注会话/用户，只负责“给定文档文本 -> 生成摘要”的提示词与输出清洗。
 */
public final class DocumentSummaryAiUtils {
    private DocumentSummaryAiUtils() {}

    public static String buildInstructions() {
        // 约束：输出必须简短、可用于“文件列表快速扫读”，并尽量使用与原文一致的语言。
        return """
                You are a “Markdown Document Summarizer”.
                Goal: produce a concise summary for a file list UI so the reader can quickly understand what this document is about.
                Rules:
                * Output ONLY the JSON required by the schema (no extra text)
                * Use the same language as the document when possible
                * Avoid sensitive data; do not invent facts that are not present in the document
                * Keep it short and informative
                """;
    }

    public static String buildInput(String title, String excerpt) {
        String t = safeTrim(title);
        String x = safeTrim(excerpt);
        return """
                Title: %s
                Content:
                %s
                """.formatted(t, x);
    }

    public static String sanitizeSummary(String raw, int maxLen) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (maxLen > 0 && s.length() > maxLen) {
            s = s.substring(0, maxLen).trim();
        }
        return s.isEmpty() ? null : s;
    }

    private static String safeTrim(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.isEmpty() ? "" : t;
    }
}

