package com.xiaoyuan.paperpuppybackendintl.service.document.internal;

import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.entity.DocumentVersion;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentVersionRepository;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiDocumentSummaryProperties;
import com.xiaoyuan.paperpuppybackendintl.service.document.summary.DocumentSummaryJobQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * 供 FileService 侧调用的“自动建文档”服务，避免与 DocumentService / FileService 形成循环依赖。
 *
 * 语义：当用户上传文本型协作文档资源且属于 project 文件时，
 * 自动创建一个可编辑 document 并绑定到该 file_id。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAutoCreateService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentSummaryJobQueue documentSummaryJobQueue;
    private final OpenAiDocumentSummaryProperties documentSummaryProperties;

    /**
     * 幂等：若已存在 (projectId,currentFileId) 的 document，则直接返回其 id。
     */
    public Long ensureCreatedForUploadedMarkdown(Long userId,
                                                 Long projectId,
                                                 Long currentFileId,
                                                 String originalFilename,
                                                 String draftContent) {
        return ensureCreatedForUploadedTextDocument(
                userId,
                projectId,
                currentFileId,
                originalFilename,
                draftContent,
                DocumentType.MARKDOWN
        );
    }



    public Long ensureCreatedForUploadedTextDocument(Long userId,
                                                     Long projectId,
                                                     Long currentFileId,
                                                     String originalFilename,
                                                     String draftContent,
                                                     DocumentType documentType) {
        if (userId == null || userId <= 0) {
            return null;
        }
        if (projectId == null || projectId <= 0) {
            return null;
        }
        if (currentFileId == null || currentFileId <= 0) {
            return null;
        }
        if (documentType != DocumentType.MARKDOWN) {
            throw new IllegalArgumentException("Only markdown text documents are supported");
        }

        Document existing = documentRepository.findByProjectAndCurrentFileId(projectId, currentFileId).orElse(null);
        if (existing != null && existing.getId() != null && existing.getId() > 0) {
            return existing.getId();
        }

        OffsetDateTime now = OffsetDateTime.now();

        Document d = new Document();
        d.setProjectId(projectId);
        d.setTitle(deriveTitle(originalFilename, currentFileId));
        d.setDocumentType(documentType);
        d.setCurrentFileId(currentFileId);
        d.setDraftContent(draftContent);
        d.setDraftUpdatedAt(draftContent == null ? null : now);
        d.setVersion(1);
        d.setCreatedBy(userId);
        d.setUpdatedBy(userId);

        Document inserted = documentRepository.insert(d);
        if (inserted == null || inserted.getId() == null || inserted.getId() <= 0) {
            return null;
        }

        insertInitialVersionIfAbsent(inserted, currentFileId, userId);

        tryEnqueueSummary(inserted);
        return inserted.getId();
    }

    private void tryEnqueueSummary(Document inserted) {
        // 异步生成摘要（事务提交后入队，避免 DB 未提交导致 worker 读不到）
        try {
            if (documentSummaryProperties == null || !documentSummaryProperties.enabled()) {
                log.info("[DocumentAutoCreate] Summary generation disabled, skip enqueue: documentId={}", inserted.getId());
            } else {
                int v = inserted.getVersion() == null ? 1 : inserted.getVersion();
                documentSummaryJobQueue.enqueueAfterCommit(new DocumentSummaryJobQueue.Job(inserted.getId(), v, 0));
                log.info("[DocumentAutoCreate] Summary job enqueued(afterCommit): documentId={}, expectedVersion={}", inserted.getId(), v);
            }
        } catch (Exception e) {
            log.warn("[DocumentAutoCreate] Failed to enqueue summary job, documentId={}", inserted.getId(), e);
        }
    }

    private void insertInitialVersionIfAbsent(Document inserted, Long currentFileId, Long userId) {
        DocumentVersion v1 = new DocumentVersion();
        v1.setDocumentId(inserted.getId());
        v1.setVersionNo(1);
        v1.setFileId(currentFileId);
        v1.setMessage("initial upload");
        v1.setCreatedBy(userId);
        documentVersionRepository.insertIfAbsent(v1);
    }

    private String deriveTitle(String originalFilename, Long fallbackId) {
        String t = originalFilename == null ? "" : originalFilename.trim();
        if (t.isBlank()) {
            return "document-" + (fallbackId == null ? "unknown" : fallbackId);
        }
        // 去掉路径
        t = t.replace("\\", "/");
        int slash = t.lastIndexOf('/');
        if (slash >= 0 && slash < t.length() - 1) {
            t = t.substring(slash + 1);
        }
        // 去掉已知文本协作文档后缀
        if (t.toLowerCase(Locale.ROOT).endsWith(".md")) {
            t = t.substring(0, t.length() - 3);
        }
        t = t.trim();
        if (t.isBlank()) {
            t = "document-" + (fallbackId == null ? "unknown" : fallbackId);
        }
        if (t.length() > 200) {
            t = t.substring(0, 200);
        }
        return t;
    }
}

