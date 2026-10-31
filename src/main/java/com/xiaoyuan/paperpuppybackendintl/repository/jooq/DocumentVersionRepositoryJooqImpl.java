package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.DocumentVersionsRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.DocumentVersion;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.DocumentVersions.DOCUMENT_VERSIONS;

@Repository
@Primary
@RequiredArgsConstructor
public class DocumentVersionRepositoryJooqImpl extends BaseJooqRepository<DocumentVersionsRecord, DocumentVersion>
        implements DocumentVersionRepository {

    private final DSLContext dsl;

    @Override
    public int nextVersionNo(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return 1;
        }
        Integer max = dsl.select(org.jooq.impl.DSL.max(DOCUMENT_VERSIONS.VERSION_NO))
                .from(DOCUMENT_VERSIONS)
                .where(DOCUMENT_VERSIONS.DOCUMENT_ID.eq(documentId)
                        .and(DOCUMENT_VERSIONS.IS_DELETED.eq(false)))
                .fetchOne(0, Integer.class);
        return (max == null ? 0 : max) + 1;
    }

    @Override
    public DocumentVersion insert(DocumentVersion v) {
        DocumentVersionsRecord r = dsl.insertInto(DOCUMENT_VERSIONS)
                .set(DOCUMENT_VERSIONS.DOCUMENT_ID, v.getDocumentId())
                .set(DOCUMENT_VERSIONS.VERSION_NO, v.getVersionNo())
                .set(DOCUMENT_VERSIONS.FILE_ID, v.getFileId())
                .set(DOCUMENT_VERSIONS.MESSAGE, v.getMessage())
                .set(DOCUMENT_VERSIONS.CREATED_BY, v.getCreatedBy())
                .set(DOCUMENT_VERSIONS.IS_DELETED, false)
                .returning()
                .fetchOne();
        return map(r);
    }

    @Override
    public DocumentVersion insertIfAbsent(DocumentVersion v) {
        DocumentVersionsRecord r = dsl.insertInto(DOCUMENT_VERSIONS)
                .set(DOCUMENT_VERSIONS.DOCUMENT_ID, v.getDocumentId())
                .set(DOCUMENT_VERSIONS.VERSION_NO, v.getVersionNo())
                .set(DOCUMENT_VERSIONS.FILE_ID, v.getFileId())
                .set(DOCUMENT_VERSIONS.MESSAGE, v.getMessage())
                .set(DOCUMENT_VERSIONS.CREATED_BY, v.getCreatedBy())
                .set(DOCUMENT_VERSIONS.IS_DELETED, false)
                .onConflict(DOCUMENT_VERSIONS.DOCUMENT_ID, DOCUMENT_VERSIONS.VERSION_NO)
                .doNothing()
                .returning()
                .fetchOne();
        return map(r);
    }

    @Override
    public List<DocumentVersion> listByDocumentId(Long documentId, int limit) {
        if (documentId == null || documentId <= 0) {
            return List.of();
        }
        int lim = limit <= 0 ? 20 : Math.min(limit, 200);
        return dsl.selectFrom(DOCUMENT_VERSIONS)
                .where(DOCUMENT_VERSIONS.DOCUMENT_ID.eq(documentId)
                        .and(DOCUMENT_VERSIONS.IS_DELETED.eq(false)))
                .orderBy(DOCUMENT_VERSIONS.VERSION_NO.desc())
                .limit(lim)
                .fetch(this::map);
    }

    @Override
    protected DocumentVersion map(DocumentVersionsRecord record) {
        if (record == null) {
            return null;
        }
        DocumentVersion v = new DocumentVersion();
        v.setId(record.getId());
        v.setDocumentId(record.getDocumentId());
        v.setVersionNo(record.getVersionNo());
        v.setFileId(record.getFileId());
        v.setMessage(record.getMessage());
        v.setCreatedBy(record.getCreatedBy());
        v.setCreatedAt(record.getCreatedAt());
        v.setDeleted(record.getIsDeleted());
        return v;
    }
}

