package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 *  ProjectFileMapping 返回（DTO 版本）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectFileMappingDTO {
    private Long id;
    private Long projectId;
    private String cacheKey;
    private String fileName;
    private OffsetDateTime addedAt;

    /**
     * 可选：若该文件有可编辑文档副本，则返回 documents.id。
     * <p>前端可据此优先加载草稿（draft），否则回退到 publicUrl（已发布版本）。</p>
     */
    private Long documentId;
    /**
     * 可选：关联 document 的类型，当前仅支持 Markdown 编辑器。
     */
    private DocumentType documentType;
    /**
     * 可选：草稿最近更新时间（用于展示“未发布修改”提示）。
     */
    private OffsetDateTime draftUpdatedAt;
    /**
     * 可选：documents.version（用于前端草稿保存时的乐观锁）。
     */
    private Integer documentVersion;

    // 透传给前端（用于向量化/展示）
    private String sourceId;
    private Long sizeBytes;
    private String objectKey;
    private String publicUrl;
    private String storageChannel;
    private String visibility;

    // 上传者信息
    private UploaderInfo uploader;

    /**
     * 上传者信息
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UploaderInfo {
        private Long userId;
        private String name;
        private String email;
    }
}
