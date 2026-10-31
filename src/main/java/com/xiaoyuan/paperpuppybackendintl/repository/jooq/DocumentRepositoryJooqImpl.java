package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.DocumentsRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.DocumentVersions.DOCUMENT_VERSIONS;
import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Documents.DOCUMENTS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Repository
@Primary
@RequiredArgsConstructor
public class DocumentRepositoryJooqImpl extends BaseJooqRepository<DocumentsRecord, Document>
        implements DocumentRepository {

    private final DSLContext dsl;

    @Override
    public Optional<Document> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(DOCUMENTS)
                .where(DOCUMENTS.ID.eq(id)
                        .and(DOCUMENTS.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public Optional<Document> findByProjectAndCurrentFileId(Long projectId, Long currentFileId) {
        if (projectId == null || projectId <= 0 || currentFileId == null || currentFileId <= 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(DOCUMENTS)
                .where(DOCUMENTS.PROJECT_ID.eq(projectId)
                        .and(DOCUMENTS.CURRENT_FILE_ID.eq(currentFileId))
                        .and(DOCUMENTS.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public List<Document> listByProjectAndCurrentFileIds(Long projectId, Collection<Long> currentFileIds) {
        if (projectId == null || projectId <= 0 || currentFileIds == null || currentFileIds.isEmpty()) {
            return List.of();
        }
        return listByProjectsAndCurrentFileIds(List.of(projectId), currentFileIds);
    }

    @Override
    public List<Document> listByProjectsAndCurrentFileIds(Collection<Long> projectIds,
                                                          Collection<Long> currentFileIds) {
        if (projectIds == null || projectIds.isEmpty() || currentFileIds == null || currentFileIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedProjectIds = projectIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        List<Long> normalizedFileIds = currentFileIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (normalizedProjectIds.isEmpty() || normalizedFileIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(DOCUMENTS)
                .where(DOCUMENTS.PROJECT_ID.in(normalizedProjectIds)
                        .and(DOCUMENTS.CURRENT_FILE_ID.in(normalizedFileIds))
                        .and(DOCUMENTS.IS_DELETED.eq(false)))
                .fetch(this::map);
    }

    @Override
    public List<Long> listSupersededVersionFileIdsByProject(Long projectId,
                                                            Collection<Long> linkedFileIds) {
        if (projectId == null || projectId <= 0 || linkedFileIds == null || linkedFileIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedFileIds = linkedFileIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        if (normalizedFileIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectDistinct(DOCUMENT_VERSIONS.FILE_ID)
                .from(DOCUMENT_VERSIONS)
                .join(DOCUMENTS).on(DOCUMENTS.ID.eq(DOCUMENT_VERSIONS.DOCUMENT_ID))
                .where(DOCUMENTS.PROJECT_ID.eq(projectId)
                        .and(DOCUMENTS.CURRENT_FILE_ID.in(normalizedFileIds))
                        .and(DOCUMENTS.IS_DELETED.eq(false))
                        .and(DOCUMENT_VERSIONS.FILE_ID.in(normalizedFileIds))
                        .and(DOCUMENT_VERSIONS.FILE_ID.ne(DOCUMENTS.CURRENT_FILE_ID))
                        .and(DOCUMENT_VERSIONS.IS_DELETED.eq(false)))
                .fetch(DOCUMENT_VERSIONS.FILE_ID);
    }

    @Override
    public List<Document> listByProjectId(Long projectId, int offset, int limit) {
        if (projectId == null || projectId <= 0) {
            return List.of();
        }
        int off = Math.max(offset, 0);
        int lim = limit <= 0 ? 20 : Math.min(limit, 200);
        return dsl.selectFrom(DOCUMENTS)
                .where(DOCUMENTS.PROJECT_ID.eq(projectId)
                        .and(DOCUMENTS.IS_DELETED.eq(false)))
                .orderBy(DOCUMENTS.UPDATED_AT.desc())
                .offset(off)
                .limit(lim)
                .fetch(this::map);
    }

    @Override
    public Document insert(Document doc) {
        DocumentsRecord r = dsl.insertInto(DOCUMENTS)
                .set(DOCUMENTS.PROJECT_ID, doc.getProjectId())
                .set(DOCUMENTS.TITLE, doc.getTitle())
                .set(DOCUMENTS.DOCUMENT_TYPE, doc.getDocumentType() == null
                        ? DocumentType.MARKDOWN.toDatabaseValue()
                        : doc.getDocumentType().toDatabaseValue())
                .set(DOCUMENTS.CURRENT_FILE_ID, doc.getCurrentFileId())
                .set(DOCUMENTS.DRAFT_CONTENT, doc.getDraftContent())
                .set(DOCUMENTS.DRAFT_UPDATED_AT, doc.getDraftUpdatedAt())
                .set(DOCUMENTS.VERSION, doc.getVersion() == null ? 1 : doc.getVersion())
                .set(DOCUMENTS.CREATED_BY, doc.getCreatedBy())
                .set(DOCUMENTS.UPDATED_BY, doc.getUpdatedBy())
                .set(DOCUMENTS.IS_DELETED, false)
                .returning()
                .fetchOne();
        return map(r);
    }

    @Override
    public boolean updateDraft(Long id, int expectedVersion, String draftContent, OffsetDateTime now, Long updatedBy) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(DOCUMENTS)
                .set(DOCUMENTS.DRAFT_CONTENT, draftContent)
                .set(DOCUMENTS.DRAFT_UPDATED_AT, now)
                .set(DOCUMENTS.UPDATED_BY, updatedBy)
                .set(DOCUMENTS.UPDATED_AT, now)
                .set(DOCUMENTS.VERSION, expectedVersion + 1)
                .where(DOCUMENTS.ID.eq(id)
                        .and(DOCUMENTS.IS_DELETED.eq(false))
                        .and(DOCUMENTS.VERSION.eq(expectedVersion)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean updateCurrentFile(Long id, int expectedVersion, Long currentFileId, OffsetDateTime now,
            Long updatedBy) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(DOCUMENTS)
                .set(DOCUMENTS.CURRENT_FILE_ID, currentFileId)
                .set(DOCUMENTS.UPDATED_BY, updatedBy)
                .set(DOCUMENTS.UPDATED_AT, now)
                .set(DOCUMENTS.VERSION, expectedVersion + 1)
                .where(DOCUMENTS.ID.eq(id)
                        .and(DOCUMENTS.IS_DELETED.eq(false))
                        .and(DOCUMENTS.VERSION.eq(expectedVersion)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean softDelete(Long id, OffsetDateTime now, Long updatedBy) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(DOCUMENTS)
                .set(DOCUMENTS.IS_DELETED, true)
                .set(DOCUMENTS.UPDATED_BY, updatedBy)
                .set(DOCUMENTS.UPDATED_AT, now)
                .where(DOCUMENTS.ID.eq(id)
                        .and(DOCUMENTS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public int softDeleteByProjectId(Long projectId, OffsetDateTime now, Long updatedBy) {
        if (projectId == null || projectId <= 0) {
            return 0;
        }
        OffsetDateTime effectiveNow = now == null ? OffsetDateTime.now() : now;
        return dsl.update(DOCUMENTS)
                .set(DOCUMENTS.IS_DELETED, true)
                .set(DOCUMENTS.UPDATED_BY, updatedBy)
                .set(DOCUMENTS.UPDATED_AT, effectiveNow)
                .where(DOCUMENTS.PROJECT_ID.eq(projectId)
                        .and(DOCUMENTS.IS_DELETED.eq(false)))
                .execute();
    }

    @Override
    public int hardDeleteByProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return 0;
        }
        var projectDocumentIds = dsl.select(DOCUMENTS.ID)
                .from(DOCUMENTS)
                .where(DOCUMENTS.PROJECT_ID.eq(projectId));
        var documentId = field(name("document_id"), Long.class);
        var projectIdField = field(name("project_id"), Long.class);

        dsl.deleteFrom(table(name("backend_data", "agent_artifact_views")))
                .where(projectIdField.eq(projectId)
                        .or(documentId.in(projectDocumentIds)))
                .execute();
        dsl.deleteFrom(table(name("backend_data", "agent_artifact_projection")))
                .where(projectIdField.eq(projectId)
                        .or(documentId.in(projectDocumentIds)))
                .execute();
        dsl.deleteFrom(table(name("backend_data", "document_versions")))
                .where(documentId.in(projectDocumentIds))
                .execute();

        return dsl.deleteFrom(DOCUMENTS)
                .where(DOCUMENTS.PROJECT_ID.eq(projectId))
                .execute();
    }

    @Override
    public boolean updateSummary(Long id, String summary, Integer summaryVersion, OffsetDateTime now, Long updatedBy) {
        if (id == null || id <= 0) {
            return false;
        }
        var u = dsl.update(DOCUMENTS)
                .set(DOCUMENTS.SUMMARY, summary)
                .set(DOCUMENTS.SUMMARY_VERSION, summaryVersion)
                .set(DOCUMENTS.UPDATED_AT, now);
        if (updatedBy != null) {
            u = u.set(DOCUMENTS.UPDATED_BY, updatedBy);
        }
        int affected = u.where(DOCUMENTS.ID.eq(id)
                        .and(DOCUMENTS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    protected Document map(DocumentsRecord record) {
        if (record == null) {
            return null;
        }
        Document d = new Document();
        d.setId(record.getId());
        d.setProjectId(record.getProjectId());
        d.setTitle(record.getTitle());
        d.setDocumentType(DocumentType.fromString(record.getDocumentType()));
        d.setCurrentFileId(record.getCurrentFileId());
        d.setDraftContent(record.getDraftContent());
        d.setDraftUpdatedAt(record.getDraftUpdatedAt());
        d.setSummary(record.getSummary());
        d.setSummaryVersion(record.getSummaryVersion());
        d.setVersion(record.getVersion());
        d.setCreatedBy(record.getCreatedBy());
        d.setUpdatedBy(record.getUpdatedBy());
        d.setCreatedAt(record.getCreatedAt());
        d.setUpdatedAt(record.getUpdatedAt());
        d.setDeleted(record.getIsDeleted());
        return d;
    }
}
