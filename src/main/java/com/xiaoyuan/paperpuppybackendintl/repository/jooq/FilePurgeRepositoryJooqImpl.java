package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ObjectStorageFileSourceType;
import com.xiaoyuan.paperpuppybackendintl.repository.FilePurgeRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ObjectStorageFiles.OBJECT_STORAGE_FILES;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.notExists;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.table;
import static org.jooq.impl.DSL.trueCondition;

@Repository
@Primary
@RequiredArgsConstructor
public class FilePurgeRepositoryJooqImpl implements FilePurgeRepository {

    private static final Table<?> PROJECT_FILE_LINKS = table(name("backend_data", "project_file_links"));
    private static final Table<?> DOCUMENTS = table(name("backend_data", "documents"));
    private static final Table<?> DOCUMENT_VERSIONS = table(name("backend_data", "document_versions"));
    private static final Table<?> PROJECTS = table(name("backend_data", "projects"));
    private static final Table<?> FILE_ACCESS_GRANTS = table(name("backend_data", "file_access_grants"));
    private static final Table<?> FILE_TEXT_FRAGMENTS = table(name("backend_data", "file_text_fragments"));
    private static final Table<?> FILE_DERIVATIVES = table(name("backend_data", "file_derivatives"));
    private static final Table<?> PDF_DERIVED_MEDIA_ASSET_CACHE =
            table(name("backend_data", "pdf_derived_media_asset_cache"));
    private static final Table<?> PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS =
            table(name("backend_data", "project_pdf_derived_media_asset_links"));

    private final DSLContext dsl;

    @Override
    public Optional<ObjectStorageFile> findOwnedFileForUpdate(Long userId, Long fileId) {
        if (userId == null || userId <= 0 || fileId == null || fileId <= 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.ID.eq(fileId)
                        .and(OBJECT_STORAGE_FILES.OWNER_USER_ID.eq(userId)))
                .forUpdate()
                .fetchOptional(record -> {
                    ObjectStorageFile file = new ObjectStorageFile();
                    file.setId(record.getId());
                    file.setOwnerUserId(record.getOwnerUserId());
                    file.setSourceType(record.getSourceType());
                    file.setSourceKey(record.getSourceKey());
                    file.setStorageType(record.getStorageType());
                    file.setStorageChannel(record.getStorageChannel());
                    file.setVisibility(record.getVisibility());
                    file.setBucket(record.getBucket());
                    file.setObjectKey(record.getObjectKey());
                    file.setPublicDomain(record.getPublicDomain());
                    file.setPublicPath(record.getPublicPath());
                    file.setMimeType(record.getMimeType());
                    file.setFileExt(record.getFileExt());
                    file.setSizeBytes(record.getSizeBytes());
                    file.setOriginalName(record.getOriginalName());
                    file.setStatus(record.getStatus());
                    file.setDeleted(record.getIsDeleted());
                    return file;
                });
    }

    @Override
    public boolean existsProjectHistoryLink(Long projectId, Long fileId) {
        if (projectId == null || projectId <= 0 || fileId == null || fileId <= 0) {
            return false;
        }
        return dsl.fetchExists(dsl.selectOne()
                .from(PROJECT_FILE_LINKS)
                .where(longField("project_id").eq(projectId)
                        .and(longField("file_id").eq(fileId))));
    }

    @Override
    public FilePurgeUsageSnapshot collectUsageSnapshot(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return new FilePurgeUsageSnapshot(0, 0, 0, 0, 0);
        }
        return new FilePurgeUsageSnapshot(
                countActiveProjectLinks(fileId),
                countActiveDocuments(fileId),
                countActiveDocumentVersions(fileId),
                countActiveDerivedMediaSourceReferences(fileId),
                countActiveDerivedMediaFileReferences(fileId));
    }

    @Override
    public long countRetainedDerivedMediaCacheReferences(Long fileId, OffsetDateTime retainedAfter) {
        if (fileId == null || fileId <= 0 || retainedAfter == null) {
            return 0;
        }
        return fetchCount(PDF_DERIVED_MEDIA_ASSET_CACHE,
                longField("derived_file_id").eq(fileId)
                        .and(stringField("status").eq("available"))
                        .and(booleanField("is_deleted").eq(false))
                        .and(field(name("updated_at"), OffsetDateTime.class).gt(retainedAfter)));
    }

    @Override
    public List<FileGcCandidate> listOrphanGarbageCollectionCandidates(int limit,
                                                                       OffsetDateTime updatedBefore,
                                                                       OffsetDateTime retainedDerivedCacheUpdatedAfter) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        OffsetDateTime cutoff = updatedBefore == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : updatedBefore;
        Condition eligibleLifecycle = OBJECT_STORAGE_FILES.IS_DELETED.eq(false)
                .and(OBJECT_STORAGE_FILES.UPDATED_AT.le(cutoff))
                .and(OBJECT_STORAGE_FILES.STATUS.in("available", "stale"))
                .and(OBJECT_STORAGE_FILES.STATUS.eq("stale")
                        .or(OBJECT_STORAGE_FILES.SOURCE_TYPE.in(
                                ObjectStorageFileSourceType.projectDeleteAutoPurgeValues())));

        return dsl.select(OBJECT_STORAGE_FILES.OWNER_USER_ID,
                        OBJECT_STORAGE_FILES.ID,
                        OBJECT_STORAGE_FILES.SOURCE_TYPE,
                        OBJECT_STORAGE_FILES.STATUS)
                .from(OBJECT_STORAGE_FILES)
                .where(eligibleLifecycle
                        .and(hasNoActiveReferences(OBJECT_STORAGE_FILES.ID))
                        .and(hasNoRetainedDerivedMediaCacheReference(
                                OBJECT_STORAGE_FILES.ID,
                                retainedDerivedCacheUpdatedAfter)))
                .orderBy(OBJECT_STORAGE_FILES.UPDATED_AT.asc(), OBJECT_STORAGE_FILES.ID.asc())
                .limit(safeLimit)
                .fetch(record -> new FileGcCandidate(
                        record.get(OBJECT_STORAGE_FILES.OWNER_USER_ID),
                        record.get(OBJECT_STORAGE_FILES.ID),
                        record.get(OBJECT_STORAGE_FILES.SOURCE_TYPE),
                        record.get(OBJECT_STORAGE_FILES.STATUS)));
    }

    @Override
    public List<String> listDerivativeObjectKeys(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return List.of();
        }
        return dsl.select(stringField("object_key"))
                .from(FILE_DERIVATIVES)
                .where(longField("file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(false))
                        .and(stringField("object_key").isNotNull())
                        .and(stringField("object_key").ne("")))
                .fetch(stringField("object_key"));
    }

    @Override
    public int revokeAccessGrants(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return 0;
        }
        return dsl.update(FILE_ACCESS_GRANTS)
                .set(booleanField("is_deleted"), true)
                .where(longField("file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(false)))
                .execute();
    }

    @Override
    public int softDeleteTextFragments(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return 0;
        }
        return dsl.update(FILE_TEXT_FRAGMENTS)
                .set(booleanField("is_deleted"), true)
                .where(longField("file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(false)))
                .execute();
    }

    @Override
    public int softDeleteDerivatives(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return 0;
        }
        return dsl.update(FILE_DERIVATIVES)
                .set(booleanField("is_deleted"), true)
                .where(longField("file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(false)))
                .execute();
    }

    @Override
    public int deleteInactiveReferencesForHardDelete(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return 0;
        }
        int affected = 0;
        affected += deleteInactiveDocumentVersions(fileId);
        affected += clearDeletedDocumentCurrentFileReferences(fileId);
        affected += markUnlinkedDerivedMediaCacheFileReferencesDeleted(fileId);
        return affected;
    }

    @Override
    public boolean hardDeleteFileRecord(Long userId, Long fileId) {
        if (userId == null || userId <= 0 || fileId == null || fileId <= 0) {
            return false;
        }
        int deleted = dsl.deleteFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.ID.eq(fileId)
                        .and(OBJECT_STORAGE_FILES.OWNER_USER_ID.eq(userId))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false)))
                .execute();
        return deleted > 0;
    }

    private int deleteInactiveDocumentVersions(Long fileId) {
        Condition deletedDocument = longField("document_id").in(
                dsl.select(longField("id"))
                        .from(DOCUMENTS)
                        .where(booleanField("is_deleted").eq(true)));
        return dsl.deleteFrom(DOCUMENT_VERSIONS)
                .where(longField("file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(true).or(deletedDocument)))
                .execute();
    }

    private int clearDeletedDocumentCurrentFileReferences(Long fileId) {
        return dsl.update(DOCUMENTS)
                .set(longField("current_file_id"), (Long) null)
                .where(longField("current_file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(true)))
                .execute();
    }



    private int markUnlinkedDerivedMediaCacheFileReferencesDeleted(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return 0;
        }
        Table<?> link = PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS.as("pdm_link_delete");
        Table<?> project = PROJECTS.as("pdm_project_delete");
        return dsl.update(PDF_DERIVED_MEDIA_ASSET_CACHE)
                .set(stringField("status"), "deleted")
                .set(booleanField("is_deleted"), true)
                .set(longField("derived_file_id"), (Long) null)
                .where(longField("derived_file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(false))
                        .and(notExists(selectOne()
                                .from(link)
                                .join(project).on(field(name("pdm_link_delete", "project_id"), Long.class)
                                        .eq(field(name("pdm_project_delete", "id"), Long.class)))
                                .where(field(name("pdm_link_delete", "cache_asset_id"), Long.class)
                                        .eq(field(name("pdf_derived_media_asset_cache", "id"), Long.class))
                                        .and(field(name("pdm_link_delete", "is_deleted"), Boolean.class).eq(false))
                                        .and(field(name("pdm_project_delete", "is_deleted"), Boolean.class).eq(false))))))
                .execute();
    }

    private long countActiveProjectLinks(Long fileId) {
        return fetchCount(PROJECT_FILE_LINKS,
                longField("file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(false)));
    }

    private long countActiveDocuments(Long fileId) {
        return fetchCount(DOCUMENTS,
                longField("current_file_id").eq(fileId)
                        .and(booleanField("is_deleted").eq(false)));
    }

    private long countActiveDocumentVersions(Long fileId) {
        Table<?> dv = DOCUMENT_VERSIONS.as("dv");
        Table<?> d = DOCUMENTS.as("d");
        return fetchJoinedCount(dv, d,
                field(name("dv", "document_id"), Long.class).eq(field(name("d", "id"), Long.class)),
                field(name("dv", "file_id"), Long.class).eq(fileId)
                        .and(field(name("dv", "is_deleted"), Boolean.class).eq(false))
                        .and(field(name("d", "is_deleted"), Boolean.class).eq(false)));
    }



    private long countActiveDerivedMediaSourceReferences(Long fileId) {
        return countActiveCacheLinkSourceReferences(fileId);
    }

    private long countActiveDerivedMediaFileReferences(Long fileId) {
        return countActiveCacheLinkDerivedFileReferences(fileId);
    }

    private long countActiveCacheLinkSourceReferences(Long fileId) {
        Table<?> link = PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS.as("pdm_link_src");
        Table<?> cache = PDF_DERIVED_MEDIA_ASSET_CACHE.as("pdm_cache_src");
        Table<?> project = PROJECTS.as("pdm_project_src");
        Integer value = dsl.selectCount()
                .from(link)
                .join(cache).on(field(name("pdm_link_src", "cache_asset_id"), Long.class)
                        .eq(field(name("pdm_cache_src", "id"), Long.class)))
                .join(project).on(field(name("pdm_link_src", "project_id"), Long.class)
                        .eq(field(name("pdm_project_src", "id"), Long.class)))
                .where(field(name("pdm_link_src", "source_file_id"), Long.class).eq(fileId)
                        .and(field(name("pdm_link_src", "is_deleted"), Boolean.class).eq(false))
                        .and(field(name("pdm_cache_src", "is_deleted"), Boolean.class).eq(false))
                        .and(field(name("pdm_cache_src", "status"), String.class).in("processing", "available"))
                        .and(field(name("pdm_project_src", "is_deleted"), Boolean.class).eq(false)))
                .fetchOne(0, Integer.class);
        return value == null ? 0 : value;
    }

    private long countActiveCacheLinkDerivedFileReferences(Long fileId) {
        Table<?> link = PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS.as("pdm_link_file");
        Table<?> cache = PDF_DERIVED_MEDIA_ASSET_CACHE.as("pdm_cache_file");
        Table<?> project = PROJECTS.as("pdm_project_file");
        Integer value = dsl.selectCount()
                .from(link)
                .join(cache).on(field(name("pdm_link_file", "cache_asset_id"), Long.class)
                        .eq(field(name("pdm_cache_file", "id"), Long.class)))
                .join(project).on(field(name("pdm_link_file", "project_id"), Long.class)
                        .eq(field(name("pdm_project_file", "id"), Long.class)))
                .where(field(name("pdm_cache_file", "derived_file_id"), Long.class).eq(fileId)
                        .and(field(name("pdm_link_file", "is_deleted"), Boolean.class).eq(false))
                        .and(field(name("pdm_cache_file", "is_deleted"), Boolean.class).eq(false))
                        .and(field(name("pdm_cache_file", "status"), String.class).in("processing", "available"))
                        .and(field(name("pdm_project_file", "is_deleted"), Boolean.class).eq(false)))
                .fetchOne(0, Integer.class);
        return value == null ? 0 : value;
    }

    private Condition hasNoActiveReferences(Field<Long> fileIdField) {
        Table<?> dv = DOCUMENT_VERSIONS.as("dv_gc");
        Table<?> dvDoc = DOCUMENTS.as("dv_doc_gc");
        Table<?> cacheLinkSource = PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS.as("pdm_link_src_gc");
        Table<?> cacheSource = PDF_DERIVED_MEDIA_ASSET_CACHE.as("pdm_cache_src_gc");
        Table<?> cacheLinkSourceProject = PROJECTS.as("pdm_project_src_gc");
        Table<?> cacheLinkFile = PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS.as("pdm_link_file_gc");
        Table<?> cacheFile = PDF_DERIVED_MEDIA_ASSET_CACHE.as("pdm_cache_file_gc");
        Table<?> cacheLinkFileProject = PROJECTS.as("pdm_project_file_gc");

        return notExists(selectOne()
                .from(PROJECT_FILE_LINKS)
                .where(longField("file_id").eq(fileIdField)
                        .and(booleanField("is_deleted").eq(false))))
                .and(notExists(selectOne()
                        .from(DOCUMENTS)
                        .where(longField("current_file_id").eq(fileIdField)
                                .and(booleanField("is_deleted").eq(false)))))
                .and(notExists(selectOne()
                        .from(dv)
                        .join(dvDoc).on(field(name("dv_gc", "document_id"), Long.class)
                                .eq(field(name("dv_doc_gc", "id"), Long.class)))
                        .where(field(name("dv_gc", "file_id"), Long.class).eq(fileIdField)
                                .and(field(name("dv_gc", "is_deleted"), Boolean.class).eq(false))
                                .and(field(name("dv_doc_gc", "is_deleted"), Boolean.class).eq(false)))))
                .and(notExists(selectOne()
                        .from(cacheLinkSource)
                        .join(cacheSource).on(field(name("pdm_link_src_gc", "cache_asset_id"), Long.class)
                                .eq(field(name("pdm_cache_src_gc", "id"), Long.class)))
                        .join(cacheLinkSourceProject).on(field(name("pdm_link_src_gc", "project_id"), Long.class)
                                .eq(field(name("pdm_project_src_gc", "id"), Long.class)))
                        .where(field(name("pdm_link_src_gc", "source_file_id"), Long.class).eq(fileIdField)
                                .and(field(name("pdm_link_src_gc", "is_deleted"), Boolean.class).eq(false))
                                .and(field(name("pdm_cache_src_gc", "is_deleted"), Boolean.class).eq(false))
                                .and(field(name("pdm_cache_src_gc", "status"), String.class).in("processing", "available"))
                                .and(field(name("pdm_project_src_gc", "is_deleted"), Boolean.class).eq(false)))))
                .and(notExists(selectOne()
                        .from(cacheLinkFile)
                        .join(cacheFile).on(field(name("pdm_link_file_gc", "cache_asset_id"), Long.class)
                                .eq(field(name("pdm_cache_file_gc", "id"), Long.class)))
                        .join(cacheLinkFileProject).on(field(name("pdm_link_file_gc", "project_id"), Long.class)
                                .eq(field(name("pdm_project_file_gc", "id"), Long.class)))
                        .where(field(name("pdm_cache_file_gc", "derived_file_id"), Long.class).eq(fileIdField)
                                .and(field(name("pdm_link_file_gc", "is_deleted"), Boolean.class).eq(false))
                                .and(field(name("pdm_cache_file_gc", "is_deleted"), Boolean.class).eq(false))
                                .and(field(name("pdm_cache_file_gc", "status"), String.class).in("processing", "available"))
                                .and(field(name("pdm_project_file_gc", "is_deleted"), Boolean.class).eq(false)))));
    }

    private Condition hasNoRetainedDerivedMediaCacheReference(Field<Long> fileIdField, OffsetDateTime retainedAfter) {
        if (retainedAfter == null) {
            return trueCondition();
        }
        Table<?> cache = PDF_DERIVED_MEDIA_ASSET_CACHE.as("pdm_cache_retained_gc");
        return notExists(selectOne()
                .from(cache)
                .where(field(name("pdm_cache_retained_gc", "derived_file_id"), Long.class).eq(fileIdField)
                        .and(field(name("pdm_cache_retained_gc", "status"), String.class).eq("available"))
                        .and(field(name("pdm_cache_retained_gc", "is_deleted"), Boolean.class).eq(false))
                        .and(field(name("pdm_cache_retained_gc", "updated_at"), OffsetDateTime.class).gt(retainedAfter))));
    }

    private long fetchCount(Table<?> table, org.jooq.Condition condition) {
        Integer value = dsl.select(count())
                .from(table)
                .where(condition)
                .fetchOne(0, Integer.class);
        return value == null ? 0 : value;
    }

    private long fetchJoinedCount(Table<?> left,
            Table<?> right,
            org.jooq.Condition joinCondition,
            org.jooq.Condition whereCondition) {
        Integer value = dsl.selectCount()
                .from(left)
                .join(right).on(joinCondition)
                .where(whereCondition)
                .fetchOne(0, Integer.class);
        return value == null ? 0 : value;
    }

    private Field<Long> longField(String fieldName) {
        return field(name(fieldName), Long.class);
    }

    private Field<String> stringField(String fieldName) {
        return field(name(fieldName), String.class);
    }

    private Field<Boolean> booleanField(String fieldName) {
        return field(name(fieldName), Boolean.class);
    }
}
