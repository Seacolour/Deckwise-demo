package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.SessionsRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Sessions.SESSIONS;

/**
 * Session 仓储 jOOQ 实现
 */
@Repository
@RequiredArgsConstructor
public class SessionRepositoryJooqImpl extends BaseJooqRepository<SessionsRecord, Session> implements SessionRepository {

    private final DSLContext dsl;

    @Override
    public Optional<Session> findById(Long id) {
        return toOptional(dsl.selectFrom(SESSIONS)
                .where(SESSIONS.ID.eq(id))
                .fetchOne());
    }

    @Override
    public List<Session> listByProject(Long projectId, Boolean archived) {
        return listByProject(projectId, archived, 0, 200, null, null);
    }

    @Override
    public List<Session> listByProject(Long projectId, Boolean archived, int offset, int limit, String sortField, String sortOrder) {
        var condition = SESSIONS.PROJECT_ID.eq(projectId)
                .and(SESSIONS.IS_DELETED.eq(false));
        if (archived != null) {
            condition = condition.and(SESSIONS.ARCHIVED.eq(archived));
        }
        int realOffset = Math.max(offset, 0);
        int realLimit = limit > 0 ? limit : 20;

        var orderField = SESSIONS.UPDATED_AT;
        if (sortField != null && !sortField.isBlank()) {
            String sf = sortField.trim().toLowerCase(Locale.ROOT);
            orderField = switch (sf) {
                case "createdat", "created_at" -> SESSIONS.CREATED_AT;
                case "lastactiveat", "last_active_at" -> SESSIONS.LAST_ACTIVE_AT;
                default -> SESSIONS.UPDATED_AT;
            };
        }
        boolean asc = sortOrder != null && sortOrder.equalsIgnoreCase("asc");
        return dsl.selectFrom(SESSIONS)
                .where(condition)
                .orderBy(asc ? orderField.asc() : orderField.desc())
                .offset(realOffset)
                .limit(realLimit)
                .fetch(this::map);
    }

    @Override
    public int countByProject(Long projectId, Boolean archived) {
        var condition = SESSIONS.PROJECT_ID.eq(projectId)
                .and(SESSIONS.IS_DELETED.eq(false));
        if (archived != null) {
            condition = condition.and(SESSIONS.ARCHIVED.eq(archived));
        }
        Integer c = dsl.selectCount()
                .from(SESSIONS)
                .where(condition)
                .fetchOneInto(Integer.class);
        return c == null ? 0 : c;
    }

    @Override
    public Session save(Session session) {
        if (session.getId() == null) {
            SessionsRecord record = dsl.insertInto(SESSIONS)
                    .set(SESSIONS.PROJECT_ID, session.getProjectId())
                    .set(SESSIONS.CREATED_BY, session.getCreatedBy())
                    .set(SESSIONS.TITLE, session.getTitle())
                    .set(SESSIONS.LAST_USER_MESSAGE, session.getLastUserMessage())
                    .set(SESSIONS.LAST_ACTIVE_AT, session.getLastActiveAt() != null ? session.getLastActiveAt() : OffsetDateTime.now())
                    .set(SESSIONS.ARCHIVED, session.getArchived() != null ? session.getArchived() : false)
                    .set(SESSIONS.IS_DELETED, session.getDeleted() != null ? session.getDeleted() : false)
                    .returning()
                    .fetchOne();
            return map(record);
        }

        dsl.update(SESSIONS)
                .set(SESSIONS.TITLE, session.getTitle())
                .set(SESSIONS.LAST_USER_MESSAGE, session.getLastUserMessage())
                .set(SESSIONS.LAST_ACTIVE_AT, session.getLastActiveAt())
                .set(SESSIONS.ARCHIVED, session.getArchived())
                .set(SESSIONS.IS_DELETED, session.getDeleted())
                .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
                .where(SESSIONS.ID.eq(session.getId()))
                .execute();

        return findById(session.getId()).orElse(session);
    }
    @Override
    public String findTitleById(Long id) {
        return dsl.select(SESSIONS.TITLE)
                .from(SESSIONS)
                .where(SESSIONS.ID.eq(id))
                .fetchOneInto(String.class);
    }

    @Override
    public boolean updateTitleIfIn(Long sessionId, Collection<String> expectedTitles, String newTitle) {
        if (sessionId == null || sessionId <= 0) {
            return false;
        }
        if (newTitle == null || newTitle.isBlank()) {
            return false;
        }
        if (expectedTitles == null || expectedTitles.isEmpty()) {
            return false;
        }
        int updated = dsl.update(SESSIONS)
                .set(SESSIONS.TITLE, newTitle)
                .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
                .where(SESSIONS.ID.eq(sessionId)
                        .and(SESSIONS.IS_DELETED.eq(false))
                        .and(SESSIONS.TITLE.in(expectedTitles)))
                .execute();
        return updated > 0;
    }
    @Override
    public boolean updateTitle(Long sessionId, String newTitle) {
        if (sessionId == null || sessionId <= 0) {
            return false;
        }
        if (newTitle == null || newTitle.isBlank()) {
            return false;
        }
        int updated = dsl.update(SESSIONS)
                .set(SESSIONS.TITLE, newTitle)
                .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
                .where(SESSIONS.ID.eq(sessionId)
                        .and(SESSIONS.IS_DELETED.eq(false)))
                .execute();
        return updated > 0;
    }

    @Override
    public boolean softDelete(Long id) {
        int execute = dsl.update(SESSIONS)
                .set(SESSIONS.IS_DELETED, true)
                .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
                .where(SESSIONS.ID.eq(id))
                .execute();
        return execute > 0;

    }

    @Override
    protected Session map(SessionsRecord record) {
        if (record == null) {
            return null;
        }
        Session s = new Session();
        s.setId(record.getId());
        s.setProjectId(record.getProjectId());
        s.setCreatedBy(record.getCreatedBy());
        s.setTitle(record.getTitle());
        s.setLastUserMessage(record.getLastUserMessage());
        s.setLastActiveAt(record.getLastActiveAt());
        s.setArchived(record.getArchived());
        s.setCreatedAt(record.getCreatedAt());
        s.setUpdatedAt(record.getUpdatedAt());
        s.setDeleted(record.getIsDeleted());
        return s;
    }
}


