package com.xiaoyuan.paperpuppybackendintl.utils.ai;

/**
 * 纯工具类：不关注会话/用户，只负责“给定首轮对话片段 -> 生成标题”的提示词与输出清洗。
 */
public final class TitleAiUtils {
    private TitleAiUtils() {}

    public static String buildInstructions() {
        // 约束：只要一个标题，避免多余解释。
        return
                """
                        You are a “Conversation Title Generator.” Based on the user’s first message in a conversation, generate a short and accurate title.
                        Rules:
                        
                        * Output only the title itself; do not include any explanations, quotation marks, prefixes/suffixes, or line breaks
                        * Keep the title within 8 English words whenever possible
                        * Avoid ending punctuation such as periods or colons""";
    }

    public static String buildInput(String userText, String assistantText) {
        String u = safeTrim(userText);
        String a = safeTrim(assistantText);
        return """
                User：%s
                Assistant：%s
                """.formatted(u, a);
    }

    /**
     * 用户首条消息（不等待 AI 回复），用于“标题生成与 AI 回复并行”。
     */
    public static String buildInputFromUserOnly(String userText) {
        String u = safeTrim(userText);
        return """
                User：%s
                """.formatted(u);
    }

    public static String sanitizeTitle(String raw, int maxLen) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        // 去掉常见包裹符号
        s = stripOnce(s, "\"");
        s = stripOnce(s, "“");
        s = stripOnce(s, "”");
        s = stripOnce(s, "「");
        s = stripOnce(s, "」");
        s = stripOnce(s, "『");
        s = stripOnce(s, "』");
        s = s.trim();
        // 去掉结尾常见标点
        while (!s.isEmpty() && isTrailingPunctuation(s.charAt(s.length() - 1))) {
            s = s.substring(0, s.length() - 1).trim();
        }
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

    private static boolean isTrailingPunctuation(char c) {
        return c == '。' || c == '！' || c == '!' || c == '？' || c == '?'
                || c == ':' || c == '：' || c == '；' || c == ';'
                || c == '，' || c == ',' || c == '、';
    }

    private static String stripOnce(String s, String wrapper) {
        if (s == null || wrapper == null || wrapper.isEmpty()) return s;
        String t = s.trim();
        if (t.startsWith(wrapper) && t.endsWith(wrapper) && t.length() >= wrapper.length() * 2) {
            return t.substring(wrapper.length(), t.length() - wrapper.length()).trim();
        }
        return s;
    }
}


