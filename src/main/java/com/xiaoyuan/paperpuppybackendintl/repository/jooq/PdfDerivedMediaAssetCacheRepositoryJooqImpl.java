package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.model.entity.PdfDerivedMediaAssetCacheEntry;
import com.xiaoyuan.paperpuppybackendintl.repository.PdfDerivedMediaAssetCacheRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ObjectStorageFiles.OBJECT_STORAGE_FILES;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDF_DERIVED_MEDIA_ASSET_CACHE;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_ASSET_INDEX;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_BBOX;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_CAPTION;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_CONTENT_HASH;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_CREATED_AT;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_DERIVED_FILE_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_DESCRIPTION;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_ERROR_MESSAGE;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_EXTRACTOR_SIGNATURE;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_IS_DELETED;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_KIND;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_METADATA;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_OWNER_USER_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_PAGE_NUMBER;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_PROVIDER;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_SCORE;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_SOURCE_CONTENT_HASH;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_SOURCE_FILE_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_STATUS;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PDMAC_UPDATED_AT;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_CACHE_ASSET_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_CREATED_BY;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_IS_DELETED;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_PROJECT_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PPDMAL_SOURCE_FILE_ID;
import static com.xiaoyuan.paperpuppybackendintl.repository.jooq.BackendDataJooqTables.PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS;

@Repository
@Primary
@RequiredArgsConstructor
public class PdfDerivedMediaAssetCacheRepositoryJooqImpl implements PdfDerivedMediaAssetCacheRepository {

    private static final JSONB EMPTY_JSON = JSONB.valueOf("{}");

    private final DSLContext dsl;

    @Override
    public List<PdfDerivedMediaAssetCacheEntry> findAvailableByOwnerHashAndSignature(Long ownerUserId,
                                                                                     String sourceContentHash,
                                                                                     String extractorSignature,
                                                                                     int limit) {
        if (ownerUserId == null || ownerUserId <= 0
                || isBlank(sourceContentHash)
                || isBlank(extractorSignature)) {
            return List.of();
        }
        int cappedLimit = Math.max(1, Math.min(limit, 50));
        return baseSelect()
                .where(PDMAC_OWNER_USER_ID.eq(ownerUserId)
                        .and(PDMAC_SOURCE_CONTENT_HASH.eq(sourceContentHash))
                        .and(PDMAC_EXTRACTOR_SIGNATURE.eq(extractorSignature))
                        .and(PDMAC_STATUS.eq("available"))
                        .and(PDMAC_IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available")))
                .orderBy(PDMAC_SCORE.desc().nullsLast(), PDMAC_PAGE_NUMBER.asc(), PDMAC_ASSET_INDEX.asc(), PDMAC_ID.asc())
                .limit(cappedLimit)
                .fetch(this::map);
    }

    @Override
    public Optional<PdfDerivedMediaAssetCacheEntry> insertAvailable(PdfDerivedMediaAssetCacheEntry entry) {
        if (!isValidEntry(entry)) {
            return Optional.empty();
        }
        dsl.insertInto(PDF_DERIVED_MEDIA_ASSET_CACHE)
                .set(PDMAC_OWNER_USER_ID, entry.getOwnerUserId())
                .set(PDMAC_SOURCE_CONTENT_HASH, entry.getSourceContentHash())
                .set(PDMAC_SOURCE_FILE_ID, entry.getSourceFileId())
                .set(PDMAC_EXTRACTOR_SIGNATURE, entry.getExtractorSignature())
                .set(PDMAC_DERIVED_FILE_ID, entry.getDerivedFileId())
                .set(PDMAC_KIND, blankToDefault(entry.getKind(), "pdf_image_block"))
                .set(PDMAC_PROVIDER, blankToDefault(entry.getProvider(), "jgda"))
                .set(PDMAC_PAGE_NUMBER, entry.getPageNumber())
                .set(PDMAC_ASSET_INDEX, entry.getAssetIndex())
                .set(PDMAC_BBOX, entry.getBbox())
                .set(PDMAC_CAPTION, entry.getCaption())
                .set(PDMAC_DESCRIPTION, entry.getDescription())
                .set(PDMAC_CONTENT_HASH, entry.getContentHash())
                .set(PDMAC_SCORE, entry.getScore())
                .set(PDMAC_STATUS, "available")
                .set(PDMAC_ERROR_MESSAGE, entry.getErrorMessage())
                .set(PDMAC_METADATA, entry.getMetadata() == null ? EMPTY_JSON : entry.getMetadata())
                .set(PDMAC_IS_DELETED, false)
                .onConflictDoNothing()
                .execute();
        return findActiveSlot(entry);
    }

    @Override
    public int linkAssetsToProject(Long projectId,
                                   Long sourceFileId,
                                   Long createdBy,
                                   Collection<Long> cacheAssetIds) {
        List<Long> ids = normalizeIds(cacheAssetIds);
        if (projectId == null || projectId <= 0 || sourceFileId == null || sourceFileId <= 0 || ids.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (Long cacheAssetId : ids) {
            affected += dsl.insertInto(PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS)
                    .set(PPDMAL_PROJECT_ID, projectId)
                    .set(PPDMAL_SOURCE_FILE_ID, sourceFileId)
                    .set(PPDMAL_CACHE_ASSET_ID, cacheAssetId)
                    .set(PPDMAL_CREATED_BY, createdBy)
                    .set(PPDMAL_IS_DELETED, false)
                    .onConflictDoNothing()
                    .execute();
        }
        touchAvailableAssets(ids);
        return affected;
    }

    @Override
    public int softDeleteProjectLinksByProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return 0;
        }
        return dsl.update(PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS)
                .set(PPDMAL_IS_DELETED, true)
                .where(PPDMAL_PROJECT_ID.eq(projectId)
                        .and(PPDMAL_IS_DELETED.eq(false)))
                .execute();
    }

    private int touchAvailableAssets(Collection<Long> cacheAssetIds) {
        List<Long> ids = normalizeIds(cacheAssetIds);
        if (ids.isEmpty()) {
            return 0;
        }
        return dsl.update(PDF_DERIVED_MEDIA_ASSET_CACHE)
                .set(PDMAC_UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(PDMAC_ID.in(ids)
                        .and(PDMAC_STATUS.eq("available"))
                        .and(PDMAC_IS_DELETED.eq(false)))
                .execute();
    }

    private org.jooq.SelectOnConditionStep<Record> baseSelect() {
        return dsl.select(
                        PDMAC_ID,
                        PDMAC_OWNER_USER_ID,
                        PDMAC_SOURCE_CONTENT_HASH,
                        PDMAC_SOURCE_FILE_ID,
                        PDMAC_EXTRACTOR_SIGNATURE,
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
                        OBJECT_STORAGE_FILES.PUBLIC_DOMAIN.as("derived_public_domain"),
                        OBJECT_STORAGE_FILES.PUBLIC_PATH.as("derived_public_path"),
                        OBJECT_STORAGE_FILES.OBJECT_KEY.as("derived_object_key")
                )
                .from(PDF_DERIVED_MEDIA_ASSET_CACHE)
                .join(OBJECT_STORAGE_FILES).on(PDMAC_DERIVED_FILE_ID.eq(OBJECT_STORAGE_FILES.ID));
    }

    private Optional<PdfDerivedMediaAssetCacheEntry> findActiveSlot(PdfDerivedMediaAssetCacheEntry entry) {
        return baseSelect()
                .where(PDMAC_OWNER_USER_ID.eq(entry.getOwnerUserId())
                        .and(PDMAC_SOURCE_CONTENT_HASH.eq(entry.getSourceContentHash()))
                        .and(PDMAC_EXTRACTOR_SIGNATURE.eq(entry.getExtractorSignature()))
                        .and(PDMAC_KIND.eq(blankToDefault(entry.getKind(), "pdf_image_block")))
                        .and(PDMAC_PAGE_NUMBER.eq(entry.getPageNumber()))
                        .and(PDMAC_ASSET_INDEX.eq(entry.getAssetIndex()))
                        .and(PDMAC_STATUS.eq("available"))
                        .and(PDMAC_IS_DELETED.eq(false)))
                .orderBy(PDMAC_ID.asc())
                .limit(1)
                .fetchOptional(this::map);
    }

    private boolean isValidEntry(PdfDerivedMediaAssetCacheEntry entry) {
        return entry != null
                && entry.getOwnerUserId() != null && entry.getOwnerUserId() > 0
                && !isBlank(entry.getSourceContentHash())
                && !isBlank(entry.getExtractorSignature())
                && entry.getDerivedFileId() != null && entry.getDerivedFileId() > 0
                && entry.getPageNumber() != null && entry.getPageNumber() > 0
                && entry.getAssetIndex() != null && entry.getAssetIndex() > 0;
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }

    private PdfDerivedMediaAssetCacheEntry map(Record record) {
        PdfDerivedMediaAssetCacheEntry entry = new PdfDerivedMediaAssetCacheEntry();
        entry.setId(record.get(PDMAC_ID));
        entry.setOwnerUserId(record.get(PDMAC_OWNER_USER_ID));
        entry.setSourceContentHash(record.get(PDMAC_SOURCE_CONTENT_HASH));
        entry.setSourceFileId(record.get(PDMAC_SOURCE_FILE_ID));
        entry.setExtractorSignature(record.get(PDMAC_EXTRACTOR_SIGNATURE));
        entry.setDerivedFileId(record.get(PDMAC_DERIVED_FILE_ID));
        entry.setKind(record.get(PDMAC_KIND));
        entry.setProvider(record.get(PDMAC_PROVIDER));
        entry.setPageNumber(record.get(PDMAC_PAGE_NUMBER));
        entry.setAssetIndex(record.get(PDMAC_ASSET_INDEX));
        entry.setBbox(record.get(PDMAC_BBOX));
        entry.setCaption(record.get(PDMAC_CAPTION));
        entry.setDescription(record.get(PDMAC_DESCRIPTION));
        entry.setContentHash(record.get(PDMAC_CONTENT_HASH));
        entry.setScore(record.get(PDMAC_SCORE));
        entry.setStatus(record.get(PDMAC_STATUS));
        entry.setErrorMessage(record.get(PDMAC_ERROR_MESSAGE));
        entry.setMetadata(record.get(PDMAC_METADATA));
        entry.setCreatedAt(record.get(PDMAC_CREATED_AT));
        entry.setUpdatedAt(record.get(PDMAC_UPDATED_AT));
        entry.setDeleted(record.get(PDMAC_IS_DELETED));
        entry.setDerivedFileName(record.get("derived_file_name", String.class));
        entry.setDerivedMimeType(record.get("derived_mime_type", String.class));
        entry.setDerivedFileExt(record.get("derived_file_ext", String.class));
        entry.setDerivedSizeBytes(record.get("derived_size_bytes", Long.class));
        entry.setDerivedContentHash(record.get("derived_content_hash", String.class));
        entry.setDerivedPublicDomain(record.get("derived_public_domain", String.class));
        entry.setDerivedPublicPath(record.get("derived_public_path", String.class));
        entry.setDerivedObjectKey(record.get("derived_object_key", String.class));
        return entry;
    }

    private String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
