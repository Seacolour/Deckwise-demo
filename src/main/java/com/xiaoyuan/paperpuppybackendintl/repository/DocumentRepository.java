package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    Optional<Document> findById(Long id);

    /**
     * 通过 (projectId, currentFileId) 查找绑定到某个文件版本的文档（用于避免重复创建）。
     */
    Optional<Document> findByProjectAndCurrentFileId(Long projectId, Long currentFileId);

    /**
     * 批量通过 (projectId, current_file_id IN ...) 查询文档（用于文件列表避免 N+1）。
     */
    List<Document> listByProjectAndCurrentFileIds(Long projectId, Collection<Long> currentFileIds);

    /**
     * 批量通过 (project_id IN ..., current_file_id IN ...) 查询文档（管理员聚合查询避免 N+1）。
     */
    List<Document> listByProjectsAndCurrentFileIds(Collection<Long> projectIds, Collection<Long> currentFileIds);

    /**
     * 返回项目文件关联中已经被文档 current file 取代的历史版本 file id。
     *
     * <p>只有 current file 也存在于 {@code linkedFileIds} 中的活动文档才参与判断，
     * 避免异常数据下把文档从项目文件列表中完全隐藏。</p>
     */
    List<Long> listSupersededVersionFileIdsByProject(Long projectId, Collection<Long> linkedFileIds);

    List<Document> listByProjectId(Long projectId, int offset, int limit);

    Document insert(Document doc);

    /**
     * 更新草稿（乐观锁：version 必须匹配）
     */
    boolean updateDraft(Long id, int expectedVersion, String draftContent, OffsetDateTime now, Long updatedBy);

    /**
     * 更新当前发布版本（乐观锁：version 必须匹配）
     */
    boolean updateCurrentFile(Long id, int expectedVersion, Long currentFileId, OffsetDateTime now, Long updatedBy);

    boolean softDelete(Long id, OffsetDateTime now, Long updatedBy);

    int softDeleteByProjectId(Long projectId, OffsetDateTime now, Long updatedBy);

    /**
     * Hard-deletes project-scoped document rows after the owning project has been deleted.
     */
    int hardDeleteByProjectId(Long projectId);

    /**
     * 更新文档摘要
     */
    boolean updateSummary(Long id, String summary, Integer summaryVersion, OffsetDateTime now, Long updatedBy);

}
