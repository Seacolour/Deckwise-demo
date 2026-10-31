package com.xiaoyuan.paperpuppybackendintl.model.dto.document;

import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import lombok.Data;

/**
 * 创建文档（编辑态实体）
 */
@Data
public class DocumentCreateRequest {
    private Long projectId;
    private String title;
    /**
     * 可选：初始草稿内容
     */
    private String draftContent;

    /**
     * 可选：文档类型；默认 Markdown，当前仅支持 Markdown。
     */
    private DocumentType documentType;
}

