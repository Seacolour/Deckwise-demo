package com.xiaoyuan.paperpuppybackendintl.model.entity;

import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Document {
    private Long id;
    private Long projectId;
    private String title;
    private DocumentType documentType;

    /**
     * 当前已发布版本，对应 object_storage_files.id
     */
    private Long currentFileId;

    /**
     * 编辑态（高频变动）
     */
    private String draftContent;
    private OffsetDateTime draftUpdatedAt;

    /**
     * 文档摘要（由 AI 生成）
     */
    private String summary;

    /**
     * 摘要对应的文档版本（用于判断 summary 是否过期）
     */
    private Integer summaryVersion;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    private Long createdBy;
    private Long updatedBy;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
