package com.xiaoyuan.paperpuppybackendintl.model.dto.file;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import lombok.Data;

/**
 * /files/upload 返回结构（重构期：不再兼容 ossUrl 历史字段名）
 *
 * 语义说明（避免与 DB 字段混淆）：
 * - sourceFileId：DB 主键（object_storage_files.id）
 * - fileId：对外“引用 ID”，固定形如 file_<cacheKey>（不是 DB 主键）
 * - cacheKey：用于后续引用/向量化的 key（与 object_storage_files.source_key 对齐）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileUploadCacheResponse {
    private Integer totalFragments;
    private Long ttlSeconds;
    private Long sourceFileId;
    /**
     * 可选：若该上传触发创建“可编辑文档”，返回对应 documents.id。
     */
    private Long documentId;
    /**
     * 可选：若上传触发 document 创建，返回对应协作文档类型。
     */
    private DocumentType documentType;

    // 简单元信息
    private String fileId;
    private String fileName;
    private Long fileSize;
    private Long sizeBytes;
    private String mimeType;

    // 对象存储信息
    private String publicUrl;
    /**
     * 当前用户可访问 URL。private storage 下为带短期访问 token 的 URL；
     * public storage 下通常与 publicUrl 相同。
     */
    private String accessUrl;
    private String objectKey;
    private String storageChannel;
    private String visibility;
    private String cacheKey;

    /**
     * PDF 上传时可选返回：表示已将派生图片解析任务放入异步队列。
     */
    private Boolean pdfDerivedAssetsExtractionQueued;
}
