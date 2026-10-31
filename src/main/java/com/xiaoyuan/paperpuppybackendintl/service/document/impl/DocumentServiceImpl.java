package com.xiaoyuan.paperpuppybackendintl.service.document.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.*;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.entity.DocumentVersion;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentVersionRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.utils.file.InMemoryMultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final int MAX_DRAFT_CHARS = 2_000_000;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ProjectRepository projectRepository;
    private final FileService fileService;

    @Override
    @Transactional
    public DocumentDTO create(Long userId, DocumentCreateRequest req) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        ThrowUtils.throwIf(req.getProjectId() == null || req.getProjectId() <= 0, ErrorCode.PARAMS_ERROR,
                "projectId非法");

        String title = req.getTitle();
        ThrowUtils.throwIf(title == null || title.isBlank(), ErrorCode.PARAMS_ERROR, "title不能为空");
        title = title.trim();
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        requireProjectOwner(userId, req.getProjectId());

        String draft = normalizeDraft(req.getDraftContent());
        OffsetDateTime now = OffsetDateTime.now();

        DocumentType documentType = normalizeRequestedDocumentType(req.getDocumentType());
        String storedDraft = draft;

        Document d = new Document();
        d.setProjectId(req.getProjectId());
        d.setTitle(title);
        d.setDocumentType(documentType);
        d.setCurrentFileId(null);
        d.setDraftContent(storedDraft);
        d.setDraftUpdatedAt(storedDraft == null ? null : now);
        d.setVersion(1);
        d.setCreatedBy(userId);
        d.setUpdatedBy(userId);

        Document inserted = documentRepository.insert(d);
        return toDto(inserted, List.of());
    }

    @Override
    public DocumentDTO get(Long userId, Long documentId, boolean includeVersions) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(documentId == null || documentId <= 0, ErrorCode.PARAMS_ERROR, "documentId非法");

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在"));
        requireProjectOwner(userId, doc.getProjectId());

        List<DocumentVersionDTO> versions = includeVersions
                ? documentVersionRepository.listByDocumentId(documentId, 20).stream().map(this::toDto).toList()
                : List.of();
        return toDto(doc, versions);
    }

    @Override
    public List<DocumentDTO> listByProject(Long userId, Long projectId, int offset, int limit) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "projectId非法");
        requireProjectOwner(userId, projectId);

        return documentRepository.listByProjectId(projectId, offset, limit).stream()
                .map(d -> toDto(d, List.of()))
                .toList();
    }

    @Override
    @Transactional
    public DocumentDTO updateDraft(Long userId, Long documentId, DocumentDraftUpdateRequest req) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(documentId == null || documentId <= 0, ErrorCode.PARAMS_ERROR, "documentId非法");
        ThrowUtils.throwIf(req == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        ThrowUtils.throwIf(req.getVersion() == null || req.getVersion() <= 0, ErrorCode.PARAMS_ERROR, "version非法");
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在"));
        requireProjectOwner(userId, doc.getProjectId());
        ThrowUtils.throwIf(doc.getDocumentType() != DocumentType.MARKDOWN,
                ErrorCode.PARAMS_ERROR, "当前文档类型不支持草稿编辑");
        String draft = normalizeDraft(req.getDraftContent());
        OffsetDateTime now = OffsetDateTime.now();
        boolean ok = documentRepository.updateDraft(documentId, req.getVersion(), draft, now, userId);
        if (!ok) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "版本冲突：草稿已被更新，请刷新后重试");
        }
        Document updated = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在"));
        return toDto(updated, List.of());
    }

    @Override
    @Transactional
    public DocumentDTO publish(Long userId, Long documentId, DocumentPublishRequest req) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(documentId == null || documentId <= 0, ErrorCode.PARAMS_ERROR, "documentId非法");
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在"));
        requireProjectOwner(userId, doc.getProjectId());
        Integer expected = req == null ? null : req.getExpectedVersion();
        if (expected != null && !expected.equals(doc.getVersion())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "版本冲突：文档已变化，请刷新后重试");
        }
        ThrowUtils.throwIf(doc.getDocumentType() != DocumentType.MARKDOWN,
                ErrorCode.PARAMS_ERROR, "当前文档类型不支持发布");
        String draft = doc.getDraftContent();
        ThrowUtils.throwIf(draft == null || draft.isBlank(), ErrorCode.PARAMS_ERROR, "草稿内容为空，无法发布");
        byte[] bytes = draft.getBytes(StandardCharsets.UTF_8);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file", buildTextDocumentFilename(doc), "text/markdown", bytes);

        // 先获取版本号，publish 专用上传需要 documentId + versionNo 生成唯一 source_key
        int versionNo = documentVersionRepository.nextVersionNo(documentId);

        // publish 专用：source_type='publish'，不走上传去重，不触发自动创建 document
        FileUploadCacheResponse uploaded = fileService.uploadWithBytesForDocumentPublish(
                userId, file, doc.getProjectId(), bytes, documentId, versionNo);
        Long fileId = uploaded == null ? null : uploaded.getSourceFileId();
        ThrowUtils.throwIf(fileId == null || fileId <= 0, ErrorCode.SYSTEM_ERROR, "发布失败：未生成文件记录");

        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(documentId);
        v.setVersionNo(versionNo);
        v.setFileId(fileId);
        v.setMessage(req == null ? null : req.getMessage());
        v.setCreatedBy(userId);
        documentVersionRepository.insert(v);

        // 更新 documents.current_file_id（乐观锁）
        int currentDocVersion = doc.getVersion() == null ? 1 : doc.getVersion();
        OffsetDateTime now = OffsetDateTime.now();
        boolean ok = documentRepository.updateCurrentFile(documentId, currentDocVersion, fileId, now, userId);
        if (!ok) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "版本冲突：发布时文档已变化，请重试");
        }

        return get(userId, documentId, true);
    }

    @Override
    @Transactional
    public boolean delete(Long userId, Long documentId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(documentId == null || documentId <= 0, ErrorCode.PARAMS_ERROR, "documentId非法");

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在"));
        requireProjectOwner(userId, doc.getProjectId());

        boolean deleted = documentRepository.softDelete(documentId, OffsetDateTime.now(), userId);
        return deleted;
    }

    @Override
    @Transactional
    public void updateSummary(Long userId, Long documentId, String summary) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(documentId == null || documentId <= 0, ErrorCode.PARAMS_ERROR, "documentId非法");
        if (summary != null && summary.length() > 2000) {
            // 防御性截断，虽然工具层有限制
            summary = summary.substring(0, 2000);
        }

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文档不存在"));

        requireProjectOwner(userId, doc.getProjectId());

        int docVersion = doc.getVersion() == null ? 1 : doc.getVersion();
        documentRepository.updateSummary(documentId, summary, docVersion, OffsetDateTime.now(), userId);
    }

    @Override
    public List<Document> listByProjectAndCurrentFileIds(Long projectId, java.util.Collection<Long> currentFileIds) {
        if (projectId == null || projectId <= 0 || currentFileIds == null || currentFileIds.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        // 注意：此方法仅返回 Entity，主要用于内部批量查询优化。调用方需确保权限。
        return documentRepository.listByProjectAndCurrentFileIds(projectId, currentFileIds);
    }

    private String normalizeDraft(String draft) {
        if (draft == null) {
            return null;
        }
        // 防炸：避免极端长文本导致 DB/上下文压力（与 FileServiceImpl 一致量级）
        if (draft.length() > MAX_DRAFT_CHARS) {
            return draft.substring(0, MAX_DRAFT_CHARS);
        }
        return draft;
    }

    private DocumentType normalizeRequestedDocumentType(DocumentType requestedType) {
        if (requestedType == null) {
            return DocumentType.MARKDOWN;
        }
        ThrowUtils.throwIf(requestedType != DocumentType.MARKDOWN,
                ErrorCode.PARAMS_ERROR, "当前仅支持 Markdown 文档");
        return requestedType;
    }

    private String buildTextDocumentFilename(Document doc) {
        String title = doc == null ? null : doc.getTitle();
        Long documentId = doc == null ? null : doc.getId();
        String base = (title == null || title.isBlank())
                ? ("document-" + (documentId == null ? "unknown" : documentId))
                : title.trim();
        // 简单清理：避免路径/特殊字符
        base = base.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").trim();
        base = base.replaceAll("\\s{2,}", " ");
        if (base.isBlank()) {
            base = "document-" + (documentId == null ? "unknown" : documentId);
        }
        String expectedSuffix = ".md";
        if (!base.toLowerCase(Locale.ROOT).endsWith(expectedSuffix)) {
            base = base + expectedSuffix;
        }
        if (base.length() > 200) {
            int keep = 200 - expectedSuffix.length();
            base = base.substring(0, keep) + expectedSuffix;
        }
        return base;
    }

    private void requireProjectOwner(Long userId, Long projectId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目ID非法");

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在"));
        if (Boolean.TRUE.equals(project.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        }

        ThrowUtils.throwIf(project.getOwnerId() == null || !project.getOwnerId().equals(userId),
                ErrorCode.NO_AUTH_ERROR, "无权限");
    }

    private DocumentDTO toDto(Document d, List<DocumentVersionDTO> versions) {
        if (d == null) {
            return null;
        }
        DocumentDTO dto = new DocumentDTO();
        dto.setId(d.getId());
        dto.setProjectId(d.getProjectId());
        dto.setTitle(d.getTitle());
        dto.setDocumentType(d.getDocumentType());
        dto.setCurrentFileId(d.getCurrentFileId());
        dto.setDraftContent(d.getDraftContent());
        dto.setDraftUpdatedAt(d.getDraftUpdatedAt());
        dto.setVersion(d.getVersion());
        dto.setCreatedAt(d.getCreatedAt());
        dto.setUpdatedAt(d.getUpdatedAt());
        dto.setVersions(versions);
        return dto;
    }

    private DocumentVersionDTO toDto(DocumentVersion v) {
        if (v == null) {
            return null;
        }
        DocumentVersionDTO dto = new DocumentVersionDTO();
        dto.setId(v.getId());
        dto.setDocumentId(v.getDocumentId());
        dto.setVersionNo(v.getVersionNo());
        dto.setFileId(v.getFileId());
        dto.setMessage(v.getMessage());
        dto.setCreatedBy(v.getCreatedBy());
        dto.setCreatedAt(v.getCreatedAt());
        return dto;
    }
}
