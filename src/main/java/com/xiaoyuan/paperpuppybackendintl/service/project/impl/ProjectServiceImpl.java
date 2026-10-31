package com.xiaoyuan.paperpuppybackendintl.service.project.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectListQuery;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.PdfDerivedMediaAssetCacheRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileGarbageCollectionService;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Project Service（实现类）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectFileLinkRepository projectFileLinkRepository;
    private final FileAccessGrantRepository fileAccessGrantRepository;
    private final DocumentRepository documentRepository;
    private final FileGarbageCollectionService fileGarbageCollectionService;
    private final PdfDerivedMediaAssetCacheRepository pdfDerivedMediaAssetCacheRepository;

    @Override
    @Transactional
    public ProjectDTO createProject(Long userId, ProjectCreateRequest req) {
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目名称不能为空");
        }
        Project p = new Project();
        p.setOwnerId(userId);
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setCoverImage(req.getCoverImage());
        p.setTags(req.getTags());
        p.setArchived(false);
        p.setLastActiveAt(OffsetDateTime.now());
        p.setDeleted(false);

        Project saved = projectRepository.save(p);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ProjectDTO updateProject(Long userId, ProjectUpdateRequest req) {
        if (req == null || req.getId() == null || req.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目ID非法");
        }
        Project db = projectRepository.findById(req.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在"));
        if (Boolean.TRUE.equals(db.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        }
        if (!db.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }

        if (req.getName() != null) {
            db.setName(req.getName());
        }
        if (req.getDescription() != null) {
            db.setDescription(req.getDescription());
        }
        if (req.getCoverImage() != null) {
            db.setCoverImage(req.getCoverImage());
        }
        if (req.getTags() != null) {
            db.setTags(req.getTags());
        }
        if (req.getArchived() != null) {
            db.setArchived(req.getArchived());
        }
        db.setUpdatedAt(OffsetDateTime.now());

        Project saved = projectRepository.save(db);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目ID非法");
        }
        Project db = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在"));
        if (!db.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
        if (Boolean.TRUE.equals(db.getDeleted())) {
            return;
        }
        List<ProjectFileLinkView> existingFiles = projectFileLinkRepository.listProjectFiles(projectId);
        int softDeletedDocuments = documentRepository.softDeleteByProjectId(projectId, OffsetDateTime.now(), userId);
        int removedLinks = projectFileLinkRepository.softDeleteByProjectId(projectId);
        int removedPdfDerivedLinks = pdfDerivedMediaAssetCacheRepository.softDeleteProjectLinksByProjectId(projectId);
        int revokedGrants = fileAccessGrantRepository.softDeleteByProjectId(userId, projectId);
        int gcCandidates = enqueueFileGcCandidates(userId, projectId, existingFiles);
        int hardDeletedDocuments = documentRepository.hardDeleteByProjectId(projectId);
        projectRepository.softDelete(projectId);
        log.info("[ProjectDelete] deleted project resources: projectId={}, softDeletedDocuments={}, hardDeletedDocuments={}, links={}, pdfDerivedLinks={}, grants={}, gcCandidates={}",
                projectId, softDeletedDocuments, hardDeletedDocuments, removedLinks, removedPdfDerivedLinks, revokedGrants, gcCandidates);
    }

    private int enqueueFileGcCandidates(Long userId, Long projectId, List<ProjectFileLinkView> files) {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        Set<Long> fileIds = files.stream()
                .filter(file -> file != null && file.getFileId() != null && file.getFileId() > 0)
                .map(ProjectFileLinkView::getFileId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        fileIds.forEach(fileId -> fileGarbageCollectionService.enqueueProjectDeleteCandidateAfterCommit(
                userId,
                fileId,
                projectId));
        return fileIds.size();
    }

    @Override
    public ProjectDTO getProject(Long userId, Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目ID非法");
        }
        Project db = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在"));
        if (Boolean.TRUE.equals(db.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        }
        if (!db.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
        return toDTO(db);
    }

    @Override
    public PageResponse<ProjectDTO> listProjects(Long userId, ProjectListQuery query) {
        Boolean archived = query != null ? query.getArchived() : null;
        int current = query == null ? 1 : query.getCurrent();
        int pageSize = query == null ? 10 : query.getPageSize();
        if (current <= 0)
            current = 1;
        if (pageSize <= 0)
            pageSize = 10;
        pageSize = Math.min(pageSize, 200);
        int offset = (current - 1) * pageSize;

        String sortField = query == null ? null : query.getSortField();
        String sortOrder = query == null ? null : query.getSortOrder();

        List<Project> projects = projectRepository
                .listByOwner(userId, archived, offset, pageSize, sortField, sortOrder);
        List<ProjectDTO> records = projects.stream()
                .map(this::toDTO)
                .toList();
        int total = projectRepository.countByOwner(userId, archived);
        return PageResponse.of(current, pageSize, total, records);
    }

    private ProjectDTO toDTO(Project project) {
        if (project == null) {
            return null;
        }
        ProjectDTO dto = new ProjectDTO();
        dto.setId(project.getId());
        dto.setOwnerId(project.getOwnerId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setCoverImage(project.getCoverImage());
        dto.setTags(project.getTags());
        dto.setArchived(project.getArchived());
        dto.setLastActiveAt(project.getLastActiveAt());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        return dto;
    }

    @Override
    public void touchProjectActivity(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return;
        }
        projectRepository.updateLastActiveAt(projectId, OffsetDateTime.now());
    }
}
