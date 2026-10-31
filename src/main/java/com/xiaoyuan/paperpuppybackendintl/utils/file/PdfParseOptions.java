package com.xiaoyuan.paperpuppybackendintl.utils.file;

import lombok.Builder;
import lombok.Value;

/**
 * PDF 解析参数（用于限流/防炸）
 */
@Value
@Builder
public class PdfParseOptions {
    /**
     * 最大页数（超过直接报错）
     */
    @Builder.Default
    int maxPages = 200;

    /**
     * 最大 PDF 字节数（超过直接报错，防止 readAllBytes 把内存打爆）
     */
    @Builder.Default
    long maxBytes = 50L * 1024 * 1024; // 50MB

    /**
     * 最大文本长度（超过则截断）
     */
    @Builder.Default
    int maxTextChars = 2_000_000;

    /**
     * 是否按位置排序（更接近阅读顺序，但更慢）
     */
    @Builder.Default
    boolean sortByPosition = true;

    /**
     * 从第几页开始（1-based）
     */
    @Builder.Default
    int startPage = 1;

    /**
     * 解析到第几页（1-based，含）
     */
    @Builder.Default
    int endPage = Integer.MAX_VALUE;

    /**
     * 是否在文本中插入页边界标记（推荐开启，便于后续 chunk/定位）
     */
    @Builder.Default
    boolean includePageMarkers = true;
}


