package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkUpsertItem;

import java.util.List;
import java.util.Optional;

public interface ProjectFileLinkRepository {

    void upsertLink(Long projectId, Long fileId, Long addedBy, String displayName, String kind);

    //批量插入
    void upsertLinks(Long projectId, Long addedBy, List<ProjectFileLinkUpsertItem> items);

    boolean softDeleteLink(Long projectId, Long fileId);

    int softDeleteByProjectId(Long projectId);

    List<ProjectFileLinkView> listProjectFiles(Long projectId);

    boolean existsActiveLink(Long projectId, Long fileId);

    Optional<Long> findActiveFileIdByProjectAndCacheKey(Long projectId, String cacheKey);

    boolean updateDisplayName(Long projectId, Long fileId, String newDisplayName);
}


