package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.ObjectStorageFilesRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ObjectStorageFileSourceType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageVisibility;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ObjectStorageFiles.OBJECT_STORAGE_FILES;

@Repository
@Primary
@RequiredArgsConstructor
public class ObjectStorageFileRepositoryJooqImpl extends BaseJooqRepository<ObjectStorageFilesRecord, ObjectStorageFile>
        implements ObjectStorageFileRepository {

    private final DSLContext dsl;

    @Override
    public Optional<ObjectStorageFile> findAvailableByOwnerAndSource(Long ownerUserId, String sourceType, String sourceKey) {
        return dsl.selectFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.OWNER_USER_ID.eq(ownerUserId)
                        .and(OBJECT_STORAGE_FILES.SOURCE_TYPE.eq(sourceType))
                        .and(OBJECT_STORAGE_FILES.SOURCE_KEY.eq(sourceKey))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available")))
                .orderBy(OBJECT_STORAGE_FILES.UPDATED_AT.desc().nullsLast(), OBJECT_STORAGE_FILES.ID.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    @Override
    public Optional<ObjectStorageFile> findAvailableByOwnerSourceAndChannel(Long ownerUserId,
                                                                            String sourceType,
                                                                            String sourceKey,
                                                                            String storageChannel) {
        return dsl.selectFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.OWNER_USER_ID.eq(ownerUserId)
                        .and(OBJECT_STORAGE_FILES.SOURCE_TYPE.eq(sourceType))
                        .and(OBJECT_STORAGE_FILES.SOURCE_KEY.eq(sourceKey))
                        .and(OBJECT_STORAGE_FILES.STORAGE_CHANNEL.eq(normalizedStorageChannel(storageChannel)))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available")))
                .orderBy(OBJECT_STORAGE_FILES.UPDATED_AT.desc().nullsLast(), OBJECT_STORAGE_FILES.ID.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    @Override
    public Optional<ObjectStorageFile> findByOwnerAndSource(Long ownerUserId, String sourceType, String sourceKey) {
        return dsl.selectFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.OWNER_USER_ID.eq(ownerUserId)
                        .and(OBJECT_STORAGE_FILES.SOURCE_TYPE.eq(sourceType))
                        .and(OBJECT_STORAGE_FILES.SOURCE_KEY.eq(sourceKey)))
                .orderBy(OBJECT_STORAGE_FILES.UPDATED_AT.desc().nullsLast(), OBJECT_STORAGE_FILES.ID.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    @Override
    public Optional<ObjectStorageFile> findByOwnerSourceAndChannel(Long ownerUserId,
                                                                   String sourceType,
                                                                   String sourceKey,
                                                                   String storageChannel) {
        return dsl.selectFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.OWNER_USER_ID.eq(ownerUserId)
                        .and(OBJECT_STORAGE_FILES.SOURCE_TYPE.eq(sourceType))
                        .and(OBJECT_STORAGE_FILES.SOURCE_KEY.eq(sourceKey))
                        .and(OBJECT_STORAGE_FILES.STORAGE_CHANNEL.eq(normalizedStorageChannel(storageChannel))))
                .orderBy(OBJECT_STORAGE_FILES.UPDATED_AT.desc().nullsLast(), OBJECT_STORAGE_FILES.ID.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    @Override
    public Optional<ObjectStorageFile> findById(Long id) {
        return dsl.selectFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.ID.eq(id))
                .fetchOptional(this::map);
    }

    @Override
    public List<ObjectStorageFile> listRecentUserUploadedFiles(Long ownerUserId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return dsl.selectFrom(OBJECT_STORAGE_FILES)
                .where(OBJECT_STORAGE_FILES.OWNER_USER_ID.eq(ownerUserId)
                        .and(OBJECT_STORAGE_FILES.SOURCE_TYPE.eq(ObjectStorageFileSourceType.BYTES.value()))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available")))
                .orderBy(OBJECT_STORAGE_FILES.CREATED_AT.desc(), OBJECT_STORAGE_FILES.ID.desc())
                .limit(safeLimit)
                .fetch(this::map);
    }

    @Override
    public ObjectStorageFile insert(ObjectStorageFile file) {
        String sourceType = requireKnownSourceType(file == null ? null : file.getSourceType());
        // ON CONFLICT DO NOTHING: concurrent inserts of the same source tuple should not abort the transaction.
        ObjectStorageFilesRecord record = dsl.insertInto(OBJECT_STORAGE_FILES)
                .set(OBJECT_STORAGE_FILES.OWNER_USER_ID, file.getOwnerUserId())
                .set(OBJECT_STORAGE_FILES.SOURCE_TYPE, sourceType)
                .set(OBJECT_STORAGE_FILES.SOURCE_KEY, file.getSourceKey())
                .set(OBJECT_STORAGE_FILES.STORAGE_TYPE, file.getStorageType())
                .set(OBJECT_STORAGE_FILES.STORAGE_CHANNEL, normalizedStorageChannel(file.getStorageChannel()))
                .set(OBJECT_STORAGE_FILES.VISIBILITY, normalizedStorageVisibility(file.getVisibility()))
                .set(OBJECT_STORAGE_FILES.BUCKET, file.getBucket())
                .set(OBJECT_STORAGE_FILES.OBJECT_KEY, file.getObjectKey())
                .set(OBJECT_STORAGE_FILES.PUBLIC_DOMAIN, file.getPublicDomain())
                .set(OBJECT_STORAGE_FILES.PUBLIC_PATH, file.getPublicPath())
                .set(OBJECT_STORAGE_FILES.MIME_TYPE, file.getMimeType())
                .set(OBJECT_STORAGE_FILES.FILE_EXT, file.getFileExt())
                .set(OBJECT_STORAGE_FILES.SIZE_BYTES, file.getSizeBytes())
                .set(OBJECT_STORAGE_FILES.ORIGINAL_NAME, file.getOriginalName())
                .set(OBJECT_STORAGE_FILES.ETAG, file.getEtag())
                .set(OBJECT_STORAGE_FILES.VERSION_ID, file.getVersionId())
                .set(OBJECT_STORAGE_FILES.LAST_MODIFIED, file.getLastModified())
                .set(OBJECT_STORAGE_FILES.STATUS, file.getStatus())
                .set(OBJECT_STORAGE_FILES.SOURCE_ID, file.getSourceId())
                .set(OBJECT_STORAGE_FILES.METADATA, file.getMetadata())
                .set(OBJECT_STORAGE_FILES.IS_DELETED, Boolean.FALSE)
                .onConflict(OBJECT_STORAGE_FILES.OWNER_USER_ID,
                        OBJECT_STORAGE_FILES.SOURCE_TYPE,
                        OBJECT_STORAGE_FILES.SOURCE_KEY,
                        OBJECT_STORAGE_FILES.STORAGE_CHANNEL)
                .doNothing()
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public boolean markStale(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return false;
        }
        int updated = dsl.update(OBJECT_STORAGE_FILES)
                .set(OBJECT_STORAGE_FILES.STATUS, "stale")
                .where(OBJECT_STORAGE_FILES.ID.eq(fileId)
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available")))
                .execute();
        return updated > 0;
    }

    @Override
    public boolean updateSourceId(Long fileId, String sourceId) {
        if (fileId == null || fileId <= 0) {
            return false;
        }
        if (sourceId == null || sourceId.isBlank()) {
            return false;
        }
        int updated = dsl.update(OBJECT_STORAGE_FILES)
                .set(OBJECT_STORAGE_FILES.SOURCE_ID, sourceId)
                .where(OBJECT_STORAGE_FILES.ID.eq(fileId)
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false)))
                .execute();
        return updated > 0;
    }

    @Override
    public void update(ObjectStorageFile file) {
        if (file == null || file.getId() == null || file.getId() <= 0) {
            return;
        }
        String sourceType = requireKnownSourceType(file.getSourceType());

        var update = dsl.update(OBJECT_STORAGE_FILES)
                .set(OBJECT_STORAGE_FILES.OWNER_USER_ID, file.getOwnerUserId())
                .set(OBJECT_STORAGE_FILES.SOURCE_TYPE, sourceType)
                .set(OBJECT_STORAGE_FILES.SOURCE_KEY, file.getSourceKey())
                .set(OBJECT_STORAGE_FILES.STORAGE_TYPE, file.getStorageType())
                .set(OBJECT_STORAGE_FILES.STORAGE_CHANNEL, normalizedStorageChannel(file.getStorageChannel()))
                .set(OBJECT_STORAGE_FILES.VISIBILITY, normalizedStorageVisibility(file.getVisibility()))
                .set(OBJECT_STORAGE_FILES.BUCKET, file.getBucket())
                .set(OBJECT_STORAGE_FILES.OBJECT_KEY, file.getObjectKey())
                .set(OBJECT_STORAGE_FILES.PUBLIC_DOMAIN, file.getPublicDomain())
                .set(OBJECT_STORAGE_FILES.PUBLIC_PATH, file.getPublicPath())
                .set(OBJECT_STORAGE_FILES.MIME_TYPE, file.getMimeType())
                .set(OBJECT_STORAGE_FILES.FILE_EXT, file.getFileExt())
                .set(OBJECT_STORAGE_FILES.SIZE_BYTES, file.getSizeBytes())
                .set(OBJECT_STORAGE_FILES.ORIGINAL_NAME, file.getOriginalName())
                .set(OBJECT_STORAGE_FILES.ETAG, file.getEtag())
                .set(OBJECT_STORAGE_FILES.VERSION_ID, file.getVersionId())
                .set(OBJECT_STORAGE_FILES.LAST_MODIFIED, file.getLastModified())
                .set(OBJECT_STORAGE_FILES.STATUS, file.getStatus())
                .set(OBJECT_STORAGE_FILES.SOURCE_ID, file.getSourceId())
                .set(OBJECT_STORAGE_FILES.METADATA, file.getMetadata());
        if (file.getDeleted() != null) {
            update = update.set(OBJECT_STORAGE_FILES.IS_DELETED, file.getDeleted());
        }
        update.where(OBJECT_STORAGE_FILES.ID.eq(file.getId()))
                .execute();
    }

    private String requireKnownSourceType(String sourceType) {
        String normalized = ObjectStorageFileSourceType.normalize(sourceType);
        if (!ObjectStorageFileSourceType.isKnown(normalized)) {
            throw new IllegalArgumentException("Unsupported object_storage_files source_type: " + sourceType);
        }
        return normalized;
    }

    @Override
    protected ObjectStorageFile map(ObjectStorageFilesRecord record) {
        if (record == null) {
            return null;
        }
        ObjectStorageFile f = new ObjectStorageFile();
        f.setId(record.getId());
        f.setOwnerUserId(record.getOwnerUserId());
        f.setSourceType(record.getSourceType());
        f.setSourceKey(record.getSourceKey());
        f.setStorageType(record.getStorageType());
        f.setStorageChannel(record.getStorageChannel());
        f.setVisibility(record.getVisibility());
        f.setBucket(record.getBucket());
        f.setObjectKey(record.getObjectKey());
        f.setPublicDomain(record.getPublicDomain());
        f.setPublicPath(record.getPublicPath());
        f.setMimeType(record.getMimeType());
        f.setFileExt(record.getFileExt());
        f.setSizeBytes(record.getSizeBytes());
        f.setOriginalName(record.getOriginalName());
        f.setEtag(record.getEtag());
        f.setVersionId(record.getVersionId());
        f.setLastModified(record.getLastModified());
        f.setStatus(record.getStatus());
        f.setSourceId(record.getSourceId());
        f.setMetadata(record.getMetadata());
        f.setCreatedAt(record.getCreatedAt());
        f.setUpdatedAt(record.getUpdatedAt());
        f.setDeleted(record.getIsDeleted());
        return f;
    }

    private String normalizedStorageChannel(String value) {
        String normalized = StorageChannel.normalize(value);
        return StorageChannel.isKnown(normalized) ? normalized : StorageChannel.PUBLIC.value();
    }

    private String normalizedStorageVisibility(String value) {
        String normalized = StorageVisibility.normalize(value);
        return StorageVisibility.isKnown(normalized) ? normalized : StorageVisibility.PUBLIC.value();
    }
}
