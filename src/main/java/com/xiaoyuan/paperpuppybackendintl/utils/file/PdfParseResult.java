package com.xiaoyuan.paperpuppybackendintl.utils.file;

import lombok.Value;

/**
 * PDF 解析结果（最小必要字段）
 */
@Value
public class PdfParseResult {
    /**
     * 语义层级：当前解析结果属于“原始文本”（方便未来扩展 OCR/Markdown/API 等）。
     */
    String level; // e.g. RAW_TEXT

    /**
     * 解析器标识
     */
    String parser; // e.g. pdfbox

    /**
     * 解析器版本（尽量自描述；可能为 null）
     */
    String parserVersion; // e.g. 3.0.6

    /**
     * 权威标题：仅信任 PDF 元信息；没有则兜底为 Untitled PDF
     */
    String title;

    /**
     * 展示候选标题：从正文“弱猜”得到的候选（不应作为权威标题使用）
     */
    String titleCandidate;

    String text;
    Integer pageCount;
}


