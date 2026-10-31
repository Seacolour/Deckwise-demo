package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.DocumentVersion;

import java.util.List;

public interface DocumentVersionRepository {

    /**
     * 获取下一个版本号（= max(version_no)+1）
     */
    int nextVersionNo(Long documentId);

    DocumentVersion insert(DocumentVersion v);

    DocumentVersion insertIfAbsent(DocumentVersion v);

    List<DocumentVersion> listByDocumentId(Long documentId, int limit);
}

