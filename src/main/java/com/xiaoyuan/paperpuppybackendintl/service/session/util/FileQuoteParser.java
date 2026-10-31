package com.xiaoyuan.paperpuppybackendintl.service.session.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析用户消息中的内联文件引用标记 {@code @pp[...]{...}}。
 *
 * <h3>标记格式</h3>
 * <pre>
 * PDF:      @pp[type:pdf,fileId:15,docId:20,fileName:test.pdf]{选中文本}
 * Markdown: @pp[type:md,fileId:15,docId:20,fileName:笔记.md,lines:24-30]{选中文本}
 * </pre>
 *
 * <ul>
 *   <li>{@code type} — 必填，{@code pdf} 或 {@code md}</li>
 *   <li>{@code fileId} — 必填，文件 ID</li>
 *   <li>{@code docId} — 可选，文档 ID（Markdown 文件通常有）</li>
 *   <li>{@code fileName} — 必填，文件名（展示用）</li>
 *   <li>{@code lines} — 可选，行号范围如 {@code 24-30}（仅 Markdown）</li>
 *   <li>{@code {...}} — 可选，用户选中的文本片段</li>
 * </ul>
 *
 * <p>此类为纯工具类，无状态、线程安全。</p>
 */
public final class FileQuoteParser {

    private FileQuoteParser() {
    }

    // ==================== 正则 ====================

    /**
     * 匹配 @pp[key:val,...]{selectedText} 或 @pp[key:val,...]（无选中文本）。
     * <p>
     * 组 1 = 方括号内的属性串（如 "type:pdf,fileId:15,docId:20,fileName:test.pdf"）
     * 组 2 = 花括号内的选中文本（可选；若无花括号则为 null）
     * </p>
     * <p>
     * 花括号内容使用非贪婪匹配，支持多行（DOTALL）。
     * </p>
     */
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "@pp\\[([^\\]]+)](?:\\{((?:[^{}]|\\{[^{}]*})*)\\})?",
            Pattern.DOTALL);

    /**
     * 匹配属性串中的单个 key:value 对。
     * key 只允许字母数字；value 允许除逗号和右方括号外的任意字符。
     */
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "([A-Za-z0-9]+)\\s*:\\s*([^,\\]]+)");

    // ==================== 数据结构 ====================

    /**
     * 一个解析后的文件引用。
     */
    public record FileQuote(
            /** 原始标记的完整文本（用于替换） */
            String rawTag,
            /** 原始标记在 message 中的起始位置 */
            int startIndex,
            /** 原始标记在 message 中的结束位置（exclusive） */
            int endIndex,
            /** 属性 map（key 全部转为小写） */
            Map<String, String> attrs,
            /** 用户选中的文本（可能为 null） */
            String selectedText) {

        // ---------- 便捷访问器 ----------

        public String type() {
            return attrs.getOrDefault("type", "");
        }

        public boolean isPdf() {
            return "pdf".equalsIgnoreCase(type());
        }

        public boolean isMd() {
            return "md".equalsIgnoreCase(type());
        }

        public Long fileId() {
            return parseLongOrNull(attrs.get("fileid"));
        }

        public Long docId() {
            return parseLongOrNull(attrs.get("docid"));
        }

        public String fileName() {
            return attrs.getOrDefault("filename", "");
        }

        /**
         * 行号范围（仅 Markdown），如 "24-30"。
         */
        public String lines() {
            return attrs.getOrDefault("lines", null);
        }

        /**
         * 解析行号范围，返回 [startLine, endLine]（1-based）。
         * 解析失败返回 null。
         */
        public int[] lineRange() {
            String l = lines();
            if (l == null || l.isBlank()) {
                return null;
            }
            String[] parts = l.split("-", 2);
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : start;
                if (start <= 0 || end < start) {
                    return null;
                }
                return new int[] { start, end };
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public boolean hasSelectedText() {
            return selectedText != null && !selectedText.isBlank();
        }

        private static Long parseLongOrNull(String s) {
            if (s == null || s.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * 解析结果：包含所有引用和"去除标记后的纯文本"。
     */
    public record ParseResult(
            /** 原始 message */
            String originalMessage,
            /** 解析出的所有文件引用（按出现顺序） */
            List<FileQuote> quotes,
            /** 是否包含任何引用 */
            boolean hasQuotes) {

        /**
         * 将 message 中的所有 @pp[...]{...} 标记替换为指定的替换文本。
         *
         * @param replacements 与 quotes 一一对应的替换文本列表
         * @return 替换后的 message
         */
        public String replaceAll(List<String> replacements) {
            if (!hasQuotes || quotes.isEmpty()) {
                return originalMessage;
            }
            if (replacements.size() != quotes.size()) {
                throw new IllegalArgumentException(
                        "replacements size (" + replacements.size() + ") != quotes size (" + quotes.size() + ")");
            }
            StringBuilder sb = new StringBuilder();
            int cursor = 0;
            for (int i = 0; i < quotes.size(); i++) {
                FileQuote q = quotes.get(i);
                sb.append(originalMessage, cursor, q.startIndex());
                sb.append(replacements.get(i));
                cursor = q.endIndex();
            }
            sb.append(originalMessage, cursor, originalMessage.length());
            return sb.toString();
        }
    }

    // ==================== 解析入口 ====================

    /**
     * 解析 message 中的所有 @pp[...]{...} 标记。
     *
     * @param message 用户原始消息
     * @return 解析结果（若无标记，quotes 为空列表）
     */
    public static ParseResult parse(String message) {
        if (message == null || message.isEmpty()) {
            return new ParseResult(message, Collections.emptyList(), false);
        }

        // 快速路径：不包含 @pp[ 则跳过正则
        if (!message.contains("@pp[")) {
            return new ParseResult(message, Collections.emptyList(), false);
        }

        List<FileQuote> quotes = new ArrayList<>();
        Matcher m = TAG_PATTERN.matcher(message);
        while (m.find()) {
            String attrStr = m.group(1);
            String selectedText = m.group(2); // 可能为 null

            Map<String, String> attrs = parseAttrs(attrStr);

            // 基本校验：必须有 type 和 fileId
            if (!attrs.containsKey("type") || !attrs.containsKey("fileid")) {
                continue;
            }

            quotes.add(new FileQuote(
                    m.group(0),
                    m.start(),
                    m.end(),
                    attrs,
                    selectedText != null ? selectedText.trim() : null));
        }

        return new ParseResult(message, Collections.unmodifiableList(quotes), !quotes.isEmpty());
    }

    // ==================== 内部方法 ====================

    private static Map<String, String> parseAttrs(String attrStr) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher m = ATTR_PATTERN.matcher(attrStr);
        while (m.find()) {
            String key = m.group(1).trim().toLowerCase();
            String value = m.group(2).trim();
            attrs.put(key, value);
        }
        return attrs;
    }
}
