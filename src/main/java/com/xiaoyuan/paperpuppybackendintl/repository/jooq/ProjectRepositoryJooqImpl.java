package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.ProjectsRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Documents.DOCUMENTS;
import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.ProjectFileLinks.PROJECT_FILE_LINKS;
import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Projects.PROJECTS;
import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Sessions.SESSIONS;

/**
 * Project 仓储 jOOQ 实现
 */
@Repository
@Primary
@RequiredArgsConstructor
public class ProjectRepositoryJooqImpl extends BaseJooqRepository<ProjectsRecord, Project>
        implements ProjectRepository {

    private final DSLContext dsl;

    @Override
    public Optional<Project> findById(Long id) {
        return dsl.selectFrom(PROJECTS)
                .where(PROJECTS.ID.eq(id))
                .fetchOptional(this::map);
    }

    @Override
    public List<Project> listByOwner(Long ownerId, Boolean archived) {
        return listByOwner(ownerId, archived, 0, 200, null, null);
    }

    @Override
    public List<Project> listByOwner(Long ownerId, Boolean archived, int offset, int limit, String sortField,
                                     String sortOrder) {
        var condition = PROJECTS.OWNER_ID.eq(ownerId)
                .and(PROJECTS.IS_DELETED.eq(false));
        if (archived != null) {
            condition = condition.and(PROJECTS.ARCHIVED.eq(archived));
        }
        int realOffset = Math.max(offset, 0);
        int realLimit = limit > 0 ? limit : 20;

        var orderField = getProjectsRecordOffsetDateTimeTableField(sortField);
        boolean asc = sortOrder != null && sortOrder.equalsIgnoreCase("asc");
        return dsl.selectFrom(PROJECTS)
                .where(condition)
                .orderBy(asc ? orderField.asc() : orderField.desc())
                .offset(realOffset)
                .limit(realLimit)
                .fetch(this::map);
    }

    private static TableField<ProjectsRecord, OffsetDateTime> getProjectsRecordOffsetDateTimeTableField(String sortField) {
        var orderField = PROJECTS.UPDATED_AT;
        if (sortField != null && !sortField.isBlank()) {
            String sf = sortField.trim().toLowerCase(Locale.ROOT);
            // 白名单：避免“任意字段排序”导致的 SQL 注入/性能问题
            orderField = switch (sf) {
                case "createdat", "created_at" -> PROJECTS.CREATED_AT;
                case "lastactiveat", "last_active_at" -> PROJECTS.LAST_ACTIVE_AT;
                default -> PROJECTS.UPDATED_AT;
            };
        }
        return orderField;
    }

    @Override
    public int countByOwner(Long ownerId, Boolean archived) {
        var condition = PROJECTS.OWNER_ID.eq(ownerId)
                .and(PROJECTS.IS_DELETED.eq(false));
        if (archived != null) {
            condition = condition.and(PROJECTS.ARCHIVED.eq(archived));
        }
        Integer c = dsl.selectCount()
                .from(PROJECTS)
                .where(condition)
                .fetchOneInto(Integer.class);
        return c == null ? 0 : c;
    }

    @Override
    public List<Project> listByUser(Long userId, Boolean archived, int offset, int limit, String sortField,
                                    String sortOrder) {
        return listByOwner(userId, archived, offset, limit, sortField, sortOrder);
    }

    @Override
    public int countByUser(Long userId, Boolean archived) {
        return countByOwner(userId, archived);
    }

    @Override
    public List<Project> listEmptyProjectShellGcCandidates(
            int limit,
            OffsetDateTime updatedBefore,
            long maxCreatedUpdatedDeltaSeconds) {
        if (updatedBefore == null) {
            return List.of();
        }

        Field<Double> createdUpdatedDeltaSeconds = createdUpdatedDeltaSeconds();
        Condition condition = PROJECTS.IS_DELETED.eq(false)
                .and(PROJECTS.UPDATED_AT.le(updatedBefore))
                .and(PROJECTS.LAST_ACTIVE_AT.isNull().or(PROJECTS.LAST_ACTIVE_AT.le(updatedBefore)))
                .and(createdUpdatedDeltaSeconds.le((double) Math.max(0, maxCreatedUpdatedDeltaSeconds)))
                .and(hasNoActiveDocuments())
                .and(hasNoActiveProjectFileLinks())
                .and(hasNoActiveSessions());

        int safeLimit = Math.max(1, Math.min(limit, 500));
        return dsl.selectFrom(PROJECTS)
                .where(condition)
                .orderBy(PROJECTS.UPDATED_AT.asc(), PROJECTS.ID.asc())
                .limit(safeLimit)
                .fetch(this::map);
    }

    private Field<Double> createdUpdatedDeltaSeconds() {
        return DSL.field(
                "ABS(EXTRACT(EPOCH FROM ({0} - {1})))",
                Double.class,
                PROJECTS.UPDATED_AT,
                PROJECTS.CREATED_AT);
    }


    private Condition hasNoActiveDocuments() {
        return DSL.notExists(DSL.selectOne()
                .from(DOCUMENTS)
                .where(DOCUMENTS.PROJECT_ID.eq(PROJECTS.ID)
                        .and(DOCUMENTS.IS_DELETED.eq(false))));
    }

    private Condition hasNoActiveProjectFileLinks() {
        return DSL.notExists(DSL.selectOne()
                .from(PROJECT_FILE_LINKS)
                .where(PROJECT_FILE_LINKS.PROJECT_ID.eq(PROJECTS.ID)
                        .and(PROJECT_FILE_LINKS.IS_DELETED.eq(false))));
    }

    private Condition hasNoActiveSessions() {
        return DSL.notExists(DSL.selectOne()
                .from(SESSIONS)
                .where(SESSIONS.PROJECT_ID.eq(PROJECTS.ID)
                        .and(SESSIONS.IS_DELETED.eq(false))));
    }


    @Override
    public Project save(Project project) {
        if (project.getId() == null) {
            // insert
            ProjectsRecord record = dsl.insertInto(PROJECTS)
                    .set(PROJECTS.OWNER_ID, project.getOwnerId())
                    .set(PROJECTS.NAME, project.getName())
                    .set(PROJECTS.DESCRIPTION, project.getDescription())
                    .set(PROJECTS.COVER_IMAGE, project.getCoverImage())
                    .set(PROJECTS.TAGS, project.getTags() != null ? project.getTags().toArray(String[]::new) : null)
                    .set(PROJECTS.ARCHIVED, project.getArchived() != null ? project.getArchived() : Boolean.FALSE)
                    .set(PROJECTS.LAST_ACTIVE_AT,
                            project.getLastActiveAt() != null ? project.getLastActiveAt() : OffsetDateTime.now())
                    .set(PROJECTS.IS_DELETED, project.getDeleted() != null ? project.getDeleted() : Boolean.FALSE)
                    .returning()
                    .fetchOne();
            return map(record);
        } else {
            // update
            dsl.update(PROJECTS)
                    .set(PROJECTS.NAME, project.getName())
                    .set(PROJECTS.DESCRIPTION, project.getDescription())
                    .set(PROJECTS.COVER_IMAGE, project.getCoverImage())
                    .set(PROJECTS.TAGS, project.getTags() != null ? project.getTags().toArray(String[]::new) : null)
                    .set(PROJECTS.ARCHIVED, project.getArchived() != null ? project.getArchived() : Boolean.FALSE)
                    .set(PROJECTS.LAST_ACTIVE_AT, project.getLastActiveAt())
                    .set(PROJECTS.IS_DELETED, project.getDeleted() != null ? project.getDeleted() : Boolean.FALSE)
                    .where(PROJECTS.ID.eq(project.getId()))
                    .execute();
            return project;
        }
    }

    @Override
    public boolean updateNameIfIn(Long projectId, Long ownerId, Collection<String> expectedNames, String newName) {
        if (projectId == null || projectId <= 0) {
            return false;
        }
        if (ownerId == null || ownerId <= 0) {
            return false;
        }
        if (expectedNames == null || expectedNames.isEmpty()) {
            return false;
        }
        if (newName == null || newName.isBlank()) {
            return false;
        }

        int affected = dsl.update(PROJECTS)
                .set(PROJECTS.NAME, newName)
                .set(PROJECTS.UPDATED_AT, OffsetDateTime.now())
                .where(PROJECTS.ID.eq(projectId)
                        .and(PROJECTS.OWNER_ID.eq(ownerId))
                        .and(PROJECTS.IS_DELETED.eq(false))
                        .and(PROJECTS.NAME.in(expectedNames)))
                .execute();
        return affected > 0;
    }






    @Override
    public boolean softDelete(Long projectId) {
        int affected = dsl.update(PROJECTS)
                .set(PROJECTS.IS_DELETED, true)
                .set(PROJECTS.UPDATED_AT, OffsetDateTime.now())
                .where(PROJECTS.ID.eq(projectId))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean updateLastActiveAt(Long projectId, OffsetDateTime time) {
        if (projectId == null || projectId <= 0) {
            return false;
        }
        OffsetDateTime updateTime = time != null ? time : OffsetDateTime.now();
        int affected = dsl.update(PROJECTS)
                .set(PROJECTS.LAST_ACTIVE_AT, updateTime)
                .where(PROJECTS.ID.eq(projectId))
                .and(PROJECTS.IS_DELETED.eq(false))
                .execute();
        return affected > 0;
    }

    @Override
    protected Project map(ProjectsRecord record) {
        if (record == null) {
            return null;
        }
        Project p = new Project();
        p.setId(record.getId());
        p.setOwnerId(record.getOwnerId());
        p.setName(record.getName());
        p.setDescription(record.getDescription());
        p.setCoverImage(record.getCoverImage());
        String[] tagsArray = record.getTags();
        p.setTags(tagsArray != null ? Arrays.asList(tagsArray) : null);
        p.setArchived(record.getArchived());
        p.setLastActiveAt(record.getLastActiveAt());
        p.setCreatedAt(record.getCreatedAt());
        p.setUpdatedAt(record.getUpdatedAt());
        p.setDeleted(record.getIsDeleted());
        return p;
    }

}
