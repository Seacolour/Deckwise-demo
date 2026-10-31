package com.xiaoyuan.paperpuppybackendintl.model.dto.document;

import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class DocumentDTO {
    private Long id;
    private Long projectId;
    private String title;
    private DocumentType documentType;

    private Long currentFileId;
    private String draftContent;
    private OffsetDateTime draftUpdatedAt;
    private Integer version;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * 可选：返回部分版本历史（用于详情页）
     */
    private List<DocumentVersionDTO> versions;
}

