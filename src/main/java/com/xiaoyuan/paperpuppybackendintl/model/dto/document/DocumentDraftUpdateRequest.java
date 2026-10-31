package com.xiaoyuan.paperpuppybackendintl.model.dto.document;

import lombok.Data;

/**
 * 更新草稿（用于编辑器 autosave）
 */
@Data
public class DocumentDraftUpdateRequest {
    /**
     * 乐观锁版本（必须携带）
     */
    private Integer version;
    private String draftContent;
}

