package com.xiaoyuan.paperpuppybackendintl.model.dto.document;

import lombok.Data;

/**
 * 发布（固化）当前草稿为一个不可变版本
 */
@Data
public class DocumentPublishRequest {
    /**
     * 可选：发布说明
     */
    private String message;
    /**
     * 可选：要求发布时文档的乐观锁版本一致（防止发布旧草稿）
     */
    private Integer expectedVersion;
}

