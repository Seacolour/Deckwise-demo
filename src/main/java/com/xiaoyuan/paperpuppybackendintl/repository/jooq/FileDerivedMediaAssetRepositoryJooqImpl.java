package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.model.entity.FileDerivedMediaAsset;
import com.xiaoyuan.paperpuppybackendintl.repository.FileDerivedMediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ObjectStorageFiles.OBJECT_STORAGE_FILES;
import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ProjectFileLinks.PROJECT_FILE_LINKS;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDF_DERIVED_MEDIA_ASSET_CACHE;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_ASSET_INDEX;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_BBOX;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_CAPTION;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_CONTENT_HASH;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_CREATED_AT;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_DERIVED_FILE_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_DESCRIPTION;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_ERROR_MESSAGE;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_IS_DELETED;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_KIND;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_METADATA;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_PAGE_NUMBER;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_PROVIDER;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_SCORE;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_STATUS;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_UPDATED_AT;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_CACHE_ASSET_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_IS_DELETED;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_PROJECT_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_SOURCE_FILE_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS;
import static org.jooq.impl.DSL.count;

@Repository
@Primary
@RequiredArgsConstructor
public class FileDerivedMediaAssetRepositoryJooqImpl implements FileDerivedMediaAssetRepository {

    private final DSLContext dsl;

    @Override
    public List<FileDerivedMediaAsset> findAvailableByProjectAndSourceFileIds(Long projectId,
                                                                              Collection<Long> sourceFileIds,
                                                                              int limit) {
        List<Long> sourceIds = normalizeSourceFileIds(sourceFileIds);
        if (projectId == null || projectId <= 0 || sourceIds.isEmpty()) {
            return List.of();
        }
        int cappedLimit = Math.max(1, Math.min(limit, 50));
        return findAvailableCacheLinks(projectId, sourceIds, cappedLimit);
    }

    private List<FileDerivedMediaAsset> findAvailableCacheLinks(Long projectId,
                                                               List<Long> sourceIds,
                                                               int cappedLimit) {
        return dsl.select(
                        PDMAC_ID,
                        PPDMAL_PROJECT_ID,
                        PPDMAL_SOURCE_FILE_ID,
                        PDMAC_DERIVED_FILE_ID,
                        PDMAC_KIND,
                        PDMAC_PROVIDER,
                        PDMAC_PAGE_NUMBER,
                        PDMAC_ASSET_INDEX,
                        PDMAC_BBOX,
                        PDMAC_CAPTION,
                        PDMAC_DESCRIPTION,
                        PDMAC_CONTENT_HASH,
                        PDMAC_SCORE,
                        PDMAC_STATUS,
                        PDMAC_ERROR_MESSAGE,
                        PDMAC_METADATA,
                        PDMAC_CREATED_AT,
                        PDMAC_UPDATED_AT,
                        PDMAC_IS_DELETED,
                        OBJECT_STORAGE_FILES.ORIGINAL_NAME.as("derived_file_name"),
                        OBJECT_STORAGE_FILES.MIME_TYPE.as("derived_mime_type"),
                        OBJECT_STORAGE_FILES.FILE_EXT.as("derived_file_ext"),
                        OBJECT_STORAGE_FILES.SIZE_BYTES.as("derived_size_bytes"),
                        OBJECT_STORAGE_FILES.SOURCE_KEY.as("derived_content_hash"),
                        OBJECT_STORAGE_FILES.STORAGE_CHANNEL.as("derived_storage_channel"),
                        OBJECT_STORAGE_FILES.VISIBILITY.as("derived_visibility"),
                        OBJECT_STORAGE_FILES.BUCKET.as("derived_bucket"),
                        OBJECT_STORAGE_FILES.PUBLIC_DOMAIN.as("derived_public_domain"),
                        OBJECT_STORAGE_FILES.PUBLIC_PATH.as("derived_public_path"),
                        OBJECT_STORAGE_FILES.OBJECT_KEY.as("derived_object_key")
                )
                .from(PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS)
                .join(PDF_DERIVED_MEDIA_ASSET_CACHE).on(PPDMAL_CACHE_ASSET_ID.eq(PDMAC_ID))
                .join(OBJECT_STORAGE_FILES).on(PDMAC_DERIVED_FILE_ID.eq(OBJECT_STORAGE_FILES.ID))
                .join(PROJECT_FILE_LINKS).on(PROJECT_FILE_LINKS.PROJECT_ID.eq(PPDMAL_PROJECT_ID)
                        .and(PROJECT_FILE_LINKS.FILE_ID.eq(PDMAC_DERIVED_FILE_ID)))
                .where(PPDMAL_PROJECT_ID.eq(projectId)
                        .and(PPDMAL_SOURCE_FILE_ID.in(sourceIds))
                        .and(PPDMAL_IS_DELETED.eq(false))
                        .and(PDMAC_STATUS.eq("available"))
                        .and(PDMAC_IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available"))
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false)))
                .orderBy(PDMAC_SCORE.desc().nullsLast(), PDMAC_PAGE_NUMBER.asc(), PDMAC_ASSET_INDEX.asc(), PDMAC_ID.asc())
                .limit(cappedLimit)
                .fetch(this::mapCacheLink);
    }

    @Override
    public int countAvailableByProjectAndSourceFileId(Long projectId, Long sourceFileId) {
        if (projectId == null || projectId <= 0 || sourceFileId == null || sourceFileId <= 0) {
            return 0;
        }
        Integer value = dsl.select(count())
                .from(PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS)
                .join(PDF_DERIVED_MEDIA_ASSET_CACHE).on(PPDMAL_CACHE_ASSET_ID.eq(PDMAC_ID))
                .join(OBJECT_STORAGE_FILES).on(PDMAC_DERIVED_FILE_ID.eq(OBJECT_STORAGE_FILES.ID))
                .join(PROJECT_FILE_LINKS).on(PROJECT_FILE_LINKS.PROJECT_ID.eq(PPDMAL_PROJECT_ID)
                        .and(PROJECT_FILE_LINKS.FILE_ID.eq(PDMAC_DERIVED_FILE_ID)))
                .where(PPDMAL_PROJECT_ID.eq(projectId)
                        .and(PPDMAL_SOURCE_FILE_ID.eq(sourceFileId))
                        .and(PPDMAL_IS_DELETED.eq(false))
                        .and(PDMAC_STATUS.eq("available"))
                        .and(PDMAC_IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available"))
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false)))
                .fetchOne(0, Integer.class);
        return value == null ? 0 : value;
    }

    private List<Long> normalizeSourceFileIds(Collection<Long> sourceFileIds) {
        if (sourceFileIds == null || sourceFileIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : sourceFileIds) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private FileDerivedMediaAsset mapCacheLink(Record record) {
        FileDerivedMediaAsset asset = new FileDerivedMediaAsset();
        asset.setId(record.get(PDMAC_ID));
        asset.setProjectId(record.get(PPDMAL_PROJECT_ID));
        asset.setSourceFileId(record.get(PPDMAL_SOURCE_FILE_ID));
        asset.setDerivedFileId(record.get(PDMAC_DERIVED_FILE_ID));
        asset.setKind(record.get(PDMAC_KIND));
        asset.setProvider(record.get(PDMAC_PROVIDER));
        asset.setPageNumber(record.get(PDMAC_PAGE_NUMBER));
        asset.setAssetIndex(record.get(PDMAC_ASSET_INDEX));
        asset.setBbox(record.get(PDMAC_BBOX));
        asset.setCaption(record.get(PDMAC_CAPTION));
        asset.setDescription(record.get(PDMAC_DESCRIPTION));
        asset.setContentHash(record.get(PDMAC_CONTENT_HASH));
        asset.setScore(record.get(PDMAC_SCORE));
        asset.setStatus(record.get(PDMAC_STATUS));
        asset.setErrorMessage(record.get(PDMAC_ERROR_MESSAGE));
        asset.setMetadata(record.get(PDMAC_METADATA));
        asset.setCreatedAt(record.get(PDMAC_CREATED_AT));
        asset.setUpdatedAt(record.get(PDMAC_UPDATED_AT));
        asset.setDeleted(record.get(PDMAC_IS_DELETED));
        asset.setDerivedFileName(record.get("derived_file_name", String.class));
        asset.setDerivedMimeType(record.get("derived_mime_type", String.class));
        asset.setDerivedFileExt(record.get("derived_file_ext", String.class));
        asset.setDerivedSizeBytes(record.get("derived_size_bytes", Long.class));
        asset.setDerivedContentHash(record.get("derived_content_hash", String.class));
        asset.setDerivedStorageChannel(record.get("derived_storage_channel", String.class));
        asset.setDerivedVisibility(record.get("derived_visibility", String.class));
        asset.setDerivedBucket(record.get("derived_bucket", String.class));
        asset.setDerivedPublicDomain(record.get("derived_public_domain", String.class));
        asset.setDerivedPublicPath(record.get("derived_public_path", String.class));
        asset.setDerivedObjectKey(record.get("derived_object_key", String.class));
        return asset;
    }
}
