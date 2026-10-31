package com.xiaoyuan.paperpuppybackendintl.service.document;

import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDraftUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentPublishRequest;

import java.util.List;

public interface DocumentService {

    DocumentDTO create(Long userId, DocumentCreateRequest req);

    DocumentDTO get(Long userId, Long documentId, boolean includeVersions);

    List<DocumentDTO> listByProject(Long userId, Long projectId, int offset, int limit);

    DocumentDTO updateDraft(Long userId, Long documentId, DocumentDraftUpdateRequest req);

    DocumentDTO publish(Long userId, Long documentId, DocumentPublishRequest req);

    boolean delete(Long userId, Long documentId);

    /**
     * 更新文档摘要（Agent 调用）
     */
    void updateSummary(Long userId, Long documentId, String summary);

    /**
     * 根据 fileId 列表批量查询关联的文档实体
     */
    List<com.xiaoyuan.paperpuppybackendintl.model.entity.Document> listByProjectAndCurrentFileIds(Long projectId,
            java.util.Collection<Long> currentFileIds);
}
