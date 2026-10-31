package com.xiaoyuan.paperpuppybackendintl.service.session.util;

/**
 * 将 markdown/富文本尽量清洗为“可读性更高”的纯文本。
 *
 * <p>对齐旧项目：raw_content 存原始内容；content 存清洗后的内容。</p>
 */
public final class MarkdownContentCleaner {

    private MarkdownContentCleaner() {
    }

    /**
     * 清理 markdown：去代码块/标题/引用/分隔线/粗斜体/行内代码/列表符号，并合并多余空白。
     * <p>注意：这是面向“可读性”的清洗，不保证严格语义等价。</p>
     */
    public static String cleanMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text
                // 1) 去大块代码
                .replaceAll("(?s)```.*?```", "")
                // 2) 去标题、引用、分隔线
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^>\\s?", "")
                .replaceAll("(?m)^(-{3,}|\\*{3,})\\s*$", "")
                // 4) 去粗体、斜体、删除线、行内代码
                .replaceAll("\\*\\*(.*?)\\*\\*", "$1")
                .replaceAll("__(.*?)__", "$1")
                .replaceAll("\\*(.*?)\\*", "$1")
                .replaceAll("_(.*?)_", "$1")
                .replaceAll("~~(.*?)~~", "$1")
                .replaceAll("`([^`]+)`", "$1")
                // 5) 去列表项标记
                .replaceAll("(?m)^[\\*\\-\\+]\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                // 6) 合并多余换行和空格
                .replaceAll("\\n{2,}", "\n")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}


