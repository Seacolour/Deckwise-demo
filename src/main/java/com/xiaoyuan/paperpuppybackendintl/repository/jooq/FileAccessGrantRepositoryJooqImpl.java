package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.Collection;

import static org.jooq.impl.DSL.*;

/**
 * File access grants (project-scoped authorization)
 * <p>
 * 设计原则：
 * - 不依赖 jOOQ codegen（避免建表顺序问题）
 * - 明确区分 table column 与 EXCLUDED column（Postgres upsert 安全）
 * - 仅支持 project 维度永久授权
 */
@Repository
@Primary
@RequiredArgsConstructor
public class FileAccessGrantRepositoryJooqImpl implements FileAccessGrantRepository {

    private final DSLContext dsl;

    /* =========================
     * Table & Columns
     * ========================= */

    private static final Table<?> T =
            table(name("backend_data", "file_access_grants"));

    /**
     * 重要：
     * - 这里的 Table 是用 DSL.table(name(...)) 动态构造的，默认不携带字段元数据；
     * - 如果用 T.field(name("col")) 取字段，极易得到 null，导致 insert/update 被"静默跳过"。
     * <p>
     * 因此列定义必须用 DSL.field(...) 显式构造。
     */
    private static final Field<Long> FILE_ID = field(name("file_id"), Long.class);
    private static final Field<Long> USER_ID = field(name("user_id"), Long.class);
    private static final Field<Long> PROJECT_ID = field(name("project_id"), Long.class);
    private static final Field<Boolean> IS_DELETED = field(name("is_deleted"), Boolean.class);

    /* =========================
     * Public APIs
     * ========================= */

    @Override
    public void upsertLink(Long userId, Long fileId, Long projectId) {
        if (validIds(userId, fileId)) {
            return;
        }
        boolean hasProject = projectId != null && projectId > 0;
        if (!hasProject) {
            return;
        }
        upsertProjectLink(userId, fileId, projectId);
    }

    @Override
    public void upsertProjectLinks(Long userId, Long projectId, Collection<Long> fileIds) {
        if (userId == null || userId <= 0 || projectId == null || projectId <= 0 || fileIds == null
                || fileIds.isEmpty()) {
            return;
        }

        // multi-values insert + onConflict doUpdate
        var insert = dsl.insertInto(T)
                .columns(USER_ID, FILE_ID, PROJECT_ID, IS_DELETED);

        boolean hasAny = false;
        for (Long fileId : fileIds) {
            if (fileId == null || fileId <= 0) {
                continue;
            }
            insert = insert.values(userId, fileId, projectId, false);
            hasAny = true;
        }
        if (!hasAny) {
            return;
        }

        insert.onConflict(FILE_ID, USER_ID, PROJECT_ID)
                .where(IS_DELETED.eq(false).and(PROJECT_ID.isNotNull()))
                .doUpdate()
                .set(IS_DELETED, false)
                .execute();
    }

    @Override
    public boolean hasValidLink(Long userId, Long fileId, Long projectId) {
        if (validIds(userId, fileId)) {
            return false;
        }
        if (projectId != null && projectId > 0) {
            return existsProjectLink(userId, fileId, projectId);
        }
        return false;
    }

    @Override
    public int softDeleteByProjectId(Long userId, Long projectId) {
        if (userId == null || userId <= 0 || projectId == null || projectId <= 0) {
            return 0;
        }
        return dsl.update(T)
                .set(IS_DELETED, true)
                .where(USER_ID.eq(userId))
                .and(PROJECT_ID.eq(projectId))
                .and(IS_DELETED.eq(false))
                .execute();
    }

    /* =========================
     * Internal helpers
     * ========================= */

    private void upsertProjectLink(Long userId, Long fileId, Long projectId) {
        dsl.insertInto(T)
                .set(USER_ID, userId)
                .set(FILE_ID, fileId)
                .set(PROJECT_ID, projectId)
                .set(IS_DELETED, false)
                .onConflict(FILE_ID, USER_ID, PROJECT_ID)
                .where(IS_DELETED.eq(false).and(PROJECT_ID.isNotNull()))
                .doUpdate()
                .set(IS_DELETED, false)
                .execute();
    }

    private boolean existsProjectLink(Long userId, Long fileId, Long projectId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(T)
                        .where(USER_ID.eq(userId))
                        .and(FILE_ID.eq(fileId))
                        .and(IS_DELETED.eq(false))
                        .and(PROJECT_ID.eq(projectId))
        );
    }

    /* =========================
     * Utilities
     * ========================= */

    private static boolean validIds(Long userId, Long fileId) {
        return userId == null || userId <= 0
                || fileId == null || fileId <= 0;
    }
}
