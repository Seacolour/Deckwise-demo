package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.Data;
import org.jooq.JSONB;

import java.time.OffsetDateTime;

/**
 * 项目文件列表查询用的内部视图（repo -> service）。
 * 不直接暴露给前端；最终由 service 组装为 ProjectFileMappingDTO。
 */
@Data
public class ProjectFileLinkView {
    private Long linkId;
    private Long projectId;
    private Long fileId;
    private Long addedBy; // 上传者用户ID
    private OffsetDateTime addedAt;

    private String displayName;
    private String kind;

    // 来自 object_storage_files
    private String cacheKey; // source_key
    private String sourceType;
    private String sourceId; // source_id（向量化 id）
    private String mimeType;
    private String fileExt;
    private Long sizeBytes;

    private String storageChannel;
    private String visibility;
    private String bucket;
    private String publicDomain;
    private String publicPath;
    private String objectKey;

    private JSONB metadata; // 文件元数据（包含origin信息）
}
