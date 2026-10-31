package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkUpsertItem;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep6;
import org.jooq.Record;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ObjectStorageFiles.OBJECT_STORAGE_FILES;
import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ProjectFileLinks.PROJECT_FILE_LINKS;
import static org.jooq.impl.DSL.excluded;

@Repository
@Primary
@RequiredArgsConstructor
public class ProjectFileLinkRepositoryJooqImpl implements ProjectFileLinkRepository {

    private final DSLContext dsl;

    @Override
    public void upsertLink(Long projectId, Long fileId, Long addedBy, String displayName, String kind) {
        dsl.insertInto(PROJECT_FILE_LINKS)
                .set(PROJECT_FILE_LINKS.PROJECT_ID, projectId)
                .set(PROJECT_FILE_LINKS.FILE_ID, fileId)
                .set(PROJECT_FILE_LINKS.ADDED_BY, addedBy)
                .set(PROJECT_FILE_LINKS.DISPLAY_NAME, displayName)
                .set(PROJECT_FILE_LINKS.KIND, kind)
                .set(PROJECT_FILE_LINKS.IS_DELETED, false)
                .onConflict(PROJECT_FILE_LINKS.PROJECT_ID, PROJECT_FILE_LINKS.FILE_ID)
                .doUpdate()
                .set(PROJECT_FILE_LINKS.DISPLAY_NAME, displayName)
                .set(PROJECT_FILE_LINKS.KIND, kind)
                .set(PROJECT_FILE_LINKS.IS_DELETED, false)
                .set(PROJECT_FILE_LINKS.UPDATED_AT, OffsetDateTime.now())
                .execute();
    }

    @Override
    public void upsertLinks(Long projectId, Long addedBy, List<ProjectFileLinkUpsertItem> items) {
        if (projectId == null || projectId <= 0 || items == null || items.isEmpty()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();

        InsertValuesStep6<?, Long, Long, Long, String, String, Boolean> insert = null;
        for (ProjectFileLinkUpsertItem it : items) {
            if (it == null || it.getFileId() == null || it.getFileId() <= 0) {
                continue;
            }
            if (insert == null) {
                insert = dsl.insertInto(PROJECT_FILE_LINKS,
                                PROJECT_FILE_LINKS.PROJECT_ID,
                                PROJECT_FILE_LINKS.FILE_ID,
                                PROJECT_FILE_LINKS.ADDED_BY,
                                PROJECT_FILE_LINKS.DISPLAY_NAME,
                                PROJECT_FILE_LINKS.KIND,
                                PROJECT_FILE_LINKS.IS_DELETED)
                        .values(projectId, it.getFileId(), addedBy, it.getDisplayName(), it.getKind(), false);
            } else {
                insert.values(projectId, it.getFileId(), addedBy, it.getDisplayName(), it.getKind(), false);
            }
        }
        if (insert != null) {
            insert.onConflict(PROJECT_FILE_LINKS.PROJECT_ID, PROJECT_FILE_LINKS.FILE_ID)
                    .doUpdate()
                    .set(PROJECT_FILE_LINKS.DISPLAY_NAME, excluded(PROJECT_FILE_LINKS.DISPLAY_NAME))
                    .set(PROJECT_FILE_LINKS.KIND, excluded(PROJECT_FILE_LINKS.KIND))
                    .set(PROJECT_FILE_LINKS.IS_DELETED, false)
                    .set(PROJECT_FILE_LINKS.UPDATED_AT, now)
                    .execute();
        }
    }

    @Override
    public boolean softDeleteLink(Long projectId, Long fileId) {
        int affected = dsl.update(PROJECT_FILE_LINKS)
                .set(PROJECT_FILE_LINKS.IS_DELETED, true)
                .set(PROJECT_FILE_LINKS.UPDATED_AT, OffsetDateTime.now())
                .where(PROJECT_FILE_LINKS.PROJECT_ID.eq(projectId)
                        .and(PROJECT_FILE_LINKS.FILE_ID.eq(fileId))
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public int softDeleteByProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return 0;
        }
        return dsl.update(PROJECT_FILE_LINKS)
                .set(PROJECT_FILE_LINKS.IS_DELETED, true)
                .set(PROJECT_FILE_LINKS.UPDATED_AT, OffsetDateTime.now())
                .where(PROJECT_FILE_LINKS.PROJECT_ID.eq(projectId)
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false)))
                .execute();
    }

    @Override
    public List<ProjectFileLinkView> listProjectFiles(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return List.of();
        }
        return fetchProjectFiles(PROJECT_FILE_LINKS.PROJECT_ID.eq(projectId));
    }

    private List<ProjectFileLinkView> fetchProjectFiles(Condition projectCondition) {
        return dsl.select(
                PROJECT_FILE_LINKS.ID.as("link_id"),
                PROJECT_FILE_LINKS.PROJECT_ID,
                PROJECT_FILE_LINKS.FILE_ID,
                PROJECT_FILE_LINKS.ADDED_BY, // ✅ 上传者ID
                PROJECT_FILE_LINKS.CREATED_AT.as("added_at"),
                PROJECT_FILE_LINKS.DISPLAY_NAME,
                PROJECT_FILE_LINKS.KIND,
                OBJECT_STORAGE_FILES.SOURCE_TYPE,
                OBJECT_STORAGE_FILES.SOURCE_KEY.as("cache_key"),
                OBJECT_STORAGE_FILES.SOURCE_ID,
                OBJECT_STORAGE_FILES.MIME_TYPE,
                OBJECT_STORAGE_FILES.FILE_EXT,
                OBJECT_STORAGE_FILES.SIZE_BYTES,
                OBJECT_STORAGE_FILES.STORAGE_CHANNEL,
                OBJECT_STORAGE_FILES.VISIBILITY,
                OBJECT_STORAGE_FILES.BUCKET,
                OBJECT_STORAGE_FILES.PUBLIC_DOMAIN,
                OBJECT_STORAGE_FILES.PUBLIC_PATH,
                OBJECT_STORAGE_FILES.OBJECT_KEY,
                OBJECT_STORAGE_FILES.METADATA // ⭐ metadata字段
        )
                .from(PROJECT_FILE_LINKS)
                .join(OBJECT_STORAGE_FILES).on(PROJECT_FILE_LINKS.FILE_ID.eq(OBJECT_STORAGE_FILES.ID))
                .where(projectCondition
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available")))
                .orderBy(PROJECT_FILE_LINKS.PROJECT_ID.asc(), PROJECT_FILE_LINKS.CREATED_AT.desc())
                .fetch(this::mapProjectFileLinkView);
    }

    private ProjectFileLinkView mapProjectFileLinkView(Record r) {
        ProjectFileLinkView v = new ProjectFileLinkView();
        v.setLinkId(r.get("link_id", Long.class));
        v.setProjectId(r.get(PROJECT_FILE_LINKS.PROJECT_ID));
        v.setFileId(r.get(PROJECT_FILE_LINKS.FILE_ID));
        v.setAddedBy(r.get(PROJECT_FILE_LINKS.ADDED_BY)); // ✅ 上传者ID
        v.setAddedAt(r.get("added_at", OffsetDateTime.class));
        v.setDisplayName(r.get(PROJECT_FILE_LINKS.DISPLAY_NAME));
        v.setKind(r.get(PROJECT_FILE_LINKS.KIND));
        v.setSourceType(r.get(OBJECT_STORAGE_FILES.SOURCE_TYPE));
        v.setCacheKey(r.get("cache_key", String.class));
        v.setSourceId(r.get(OBJECT_STORAGE_FILES.SOURCE_ID));
        v.setMimeType(r.get(OBJECT_STORAGE_FILES.MIME_TYPE));
        v.setFileExt(r.get(OBJECT_STORAGE_FILES.FILE_EXT));
        v.setSizeBytes(r.get(OBJECT_STORAGE_FILES.SIZE_BYTES));
        v.setStorageChannel(r.get(OBJECT_STORAGE_FILES.STORAGE_CHANNEL));
        v.setVisibility(r.get(OBJECT_STORAGE_FILES.VISIBILITY));
        v.setBucket(r.get(OBJECT_STORAGE_FILES.BUCKET));
        v.setPublicDomain(r.get(OBJECT_STORAGE_FILES.PUBLIC_DOMAIN));
        v.setPublicPath(r.get(OBJECT_STORAGE_FILES.PUBLIC_PATH));
        v.setObjectKey(r.get(OBJECT_STORAGE_FILES.OBJECT_KEY));
        v.setMetadata(r.get(OBJECT_STORAGE_FILES.METADATA)); // ⭐ metadata
        return v;
    }

    @Override
    public Optional<Long> findActiveFileIdByProjectAndCacheKey(Long projectId, String cacheKey) {
        if (projectId == null || projectId <= 0 || cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        return dsl.select(PROJECT_FILE_LINKS.FILE_ID)
                .from(PROJECT_FILE_LINKS)
                .join(OBJECT_STORAGE_FILES).on(PROJECT_FILE_LINKS.FILE_ID.eq(OBJECT_STORAGE_FILES.ID))
                .where(PROJECT_FILE_LINKS.PROJECT_ID.eq(projectId)
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.SOURCE_KEY.eq(cacheKey))
                        .and(OBJECT_STORAGE_FILES.IS_DELETED.eq(false))
                        .and(OBJECT_STORAGE_FILES.STATUS.eq("available")))
                .orderBy(PROJECT_FILE_LINKS.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(PROJECT_FILE_LINKS.FILE_ID);
    }

    @Override
    public boolean updateDisplayName(Long projectId, Long fileId, String newDisplayName) {
        int affected = dsl.update(PROJECT_FILE_LINKS)
                .set(PROJECT_FILE_LINKS.DISPLAY_NAME, newDisplayName)
                .set(PROJECT_FILE_LINKS.UPDATED_AT, OffsetDateTime.now())
                .where(PROJECT_FILE_LINKS.PROJECT_ID.eq(projectId)
                        .and(PROJECT_FILE_LINKS.FILE_ID.eq(fileId))
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean existsActiveLink(Long projectId, Long fileId) {
        if (projectId == null || projectId <= 0 || fileId == null || fileId <= 0) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(PROJECT_FILE_LINKS)
                        .where(PROJECT_FILE_LINKS.PROJECT_ID.eq(projectId)
                                .and(PROJECT_FILE_LINKS.FILE_ID.eq(fileId))
                                .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false))));
    }
}
