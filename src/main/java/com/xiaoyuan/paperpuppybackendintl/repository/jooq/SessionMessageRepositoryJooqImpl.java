package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.SessionMessagesRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Sessions.SESSIONS;
import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.SessionMessages.SESSION_MESSAGES;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.max;

/**
 * SessionMessage 仓储 jOOQ 实现
 */
@Repository
@RequiredArgsConstructor
public class SessionMessageRepositoryJooqImpl extends BaseJooqRepository<SessionMessagesRecord, SessionMessage> implements SessionMessageRepository {

    private final DSLContext dsl;

    @Override
    public int nextSeq(Long sessionId) {
        Integer v = dsl.select(coalesce(max(SESSION_MESSAGES.SEQ), 0).add(1))
                .from(SESSION_MESSAGES)
                .where(SESSION_MESSAGES.SESSION_ID.eq(sessionId))
                .fetchOneInto(Integer.class);
        return v == null ? 1 : v;
    }

    @Override
    public SessionMessage insert(SessionMessage message) {
        SessionMessagesRecord record = dsl.insertInto(SESSION_MESSAGES)
                .set(SESSION_MESSAGES.SESSION_ID, message.getSessionId())
                .set(SESSION_MESSAGES.SEQ, message.getSeq())
                .set(SESSION_MESSAGES.ROLE, message.getRole() != null ? message.getRole().toDatabaseValue() : SessionMessageRole.USER.toDatabaseValue())
                .set(SESSION_MESSAGES.TYPE, message.getType() != null ? message.getType().toDatabaseValue() : SessionMessageType.TEXT.toDatabaseValue())
                .set(SESSION_MESSAGES.CONTENT, message.getContent())
                .set(SESSION_MESSAGES.RAW_CONTENT, message.getRawContent())
                .set(SESSION_MESSAGES.PARTS, message.getParts())
                .set(SESSION_MESSAGES.NAME, message.getName())
                .set(SESSION_MESSAGES.TOOL_CALL_ID, message.getToolCallId())
                .set(SESSION_MESSAGES.METADATA, message.getMetadata())
                .set(SESSION_MESSAGES.STATUS, message.getStatus() != null ? message.getStatus().toDatabaseValue() : SessionMessageStatus.FINAL.toDatabaseValue())
                .set(SESSION_MESSAGES.TOKEN_COUNT, message.getTokenCount())
                .set(SESSION_MESSAGES.IS_DELETED, message.getDeleted() != null ? message.getDeleted() : false)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    @Transactional
    public SessionMessage insertAutoSeq(SessionMessage message) {
        if (message == null || message.getSessionId() == null) {
            throw new IllegalArgumentException("message/sessionId 不能为空");
        }

        // 串行化同一 session 的消息写入：对 sessions 行加 FOR UPDATE 锁（短事务）
        // 注意：ChatService 是异步线程调用，这里用 Spring 事务确保锁与后续 insert 在同一连接中生效。
        dsl.select(SESSIONS.ID)
                .from(SESSIONS)
                .where(SESSIONS.ID.eq(message.getSessionId()))
                .forUpdate()
                .fetchOne();

        int seq = nextSeq(message.getSessionId());
        message.setSeq(seq);
        return insert(message);
    }

    @Override
    @Transactional
    public List<SessionMessage> insertBatchAutoSeq(Long sessionId, List<SessionMessage> messages) {
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        // 串行化同一 session 的消息写入：对 sessions 行加 FOR UPDATE 锁（短事务）
        dsl.select(SESSIONS.ID)
                .from(SESSIONS)
                .where(SESSIONS.ID.eq(sessionId))
                .forUpdate()
                .fetchOne();

        int seq = nextSeq(sessionId);
        int firstSeq = seq;

        List<SessionMessagesRecord> records = new ArrayList<>(messages.size());
        for (SessionMessage m : messages) {
            if (m == null) {
                continue;
            }
            // 强制同一 session
            if (m.getSessionId() == null) {
                m.setSessionId(sessionId);
            }
            if (!Objects.equals(sessionId, m.getSessionId())) {
                throw new IllegalArgumentException("batch insert 仅支持同一 sessionId");
            }
            m.setSeq(seq++);

            SessionMessagesRecord r = dsl.newRecord(SESSION_MESSAGES);
            r.setSessionId(m.getSessionId());
            r.setSeq(m.getSeq());
            r.setRole(m.getRole() != null ? m.getRole().toDatabaseValue() : SessionMessageRole.USER.toDatabaseValue());
            r.setType(m.getType() != null ? m.getType().toDatabaseValue() : SessionMessageType.TEXT.toDatabaseValue());
            r.setContent(m.getContent());
            r.setRawContent(m.getRawContent());
            r.setParts(m.getParts());
            r.setName(m.getName());
            r.setToolCallId(m.getToolCallId());
            r.setMetadata(m.getMetadata());
            r.setStatus(m.getStatus() != null ? m.getStatus().toDatabaseValue() : SessionMessageStatus.FINAL.toDatabaseValue());
            r.setTokenCount(m.getTokenCount());
            r.setIsDeleted(m.getDeleted() != null ? m.getDeleted() : false);
            records.add(r);
        }

        if (records.isEmpty()) {
            return List.of();
        }
        dsl.batchInsert(records).execute();
        int lastSeq = seq - 1;
        return dsl.selectFrom(SESSION_MESSAGES)
                .where(SESSION_MESSAGES.SESSION_ID.eq(sessionId)
                        .and(SESSION_MESSAGES.SEQ.between(firstSeq, lastSeq))
                        .and(SESSION_MESSAGES.IS_DELETED.eq(false)))
                .orderBy(SESSION_MESSAGES.SEQ.asc())
                .fetch(this::map);
    }

    @Override
    public List<SessionMessage> listBySession(Long sessionId, int offset, int limit) {
        int realOffset = Math.max(offset, 0);
        int realLimit = limit > 0 ? limit : 200;
        return dsl.selectFrom(SESSION_MESSAGES)
                .where(SESSION_MESSAGES.SESSION_ID.eq(sessionId)
                        .and(SESSION_MESSAGES.IS_DELETED.eq(false)))
                .orderBy(SESSION_MESSAGES.SEQ.asc())
                .offset(realOffset)
                .limit(realLimit)
                .fetch(this::map);
    }

    @Override
    public List<SessionMessage> listRecentBySession(Long sessionId, int limit) {
        int realLimit = limit > 0 ? limit : 200;
        // 先取最新 N 条 final 消息（seq desc），再反转为 seq asc，保证返回顺序是“从旧到新”。
        // ABORTED / ERROR 等非最终状态仅用于回放排障，不应重新注入下一轮模型上下文。
        List<SessionMessage> out = new ArrayList<>(dsl.selectFrom(SESSION_MESSAGES)
                .where(SESSION_MESSAGES.SESSION_ID.eq(sessionId)
                        .and(SESSION_MESSAGES.IS_DELETED.eq(false))
                        .and(SESSION_MESSAGES.STATUS.isNull()
                                .or(SESSION_MESSAGES.STATUS.eq(SessionMessageStatus.FINAL.toDatabaseValue()))))
                .orderBy(SESSION_MESSAGES.SEQ.desc())
                .limit(realLimit)
                .fetch(this::map));
        Collections.reverse(out);
        return out;
    }

    @Override
    public List<SessionMessage> listBySessionAfterSeq(Long sessionId, int afterSeq, int limit) {
        int realAfterSeq = Math.max(afterSeq, 0);
        int realLimit = limit > 0 ? limit : 200;
        return dsl.selectFrom(SESSION_MESSAGES)
                .where(SESSION_MESSAGES.SESSION_ID.eq(sessionId)
                        .and(SESSION_MESSAGES.IS_DELETED.eq(false))
                        .and(SESSION_MESSAGES.SEQ.gt(realAfterSeq)))
                .orderBy(SESSION_MESSAGES.SEQ.asc())
                .limit(realLimit)
                .fetch(this::map);
    }

    @Override
    public int countBySession(Long sessionId) {
        Integer count = dsl.selectCount()
                .from(SESSION_MESSAGES)
                .where(SESSION_MESSAGES.SESSION_ID.eq(sessionId)
                        .and(SESSION_MESSAGES.IS_DELETED.eq(false)))
                .fetchOneInto(Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public int countBySessionAndRoleTypeStatus(Long sessionId,
                                              SessionMessageRole role,
                                              SessionMessageType type,
                                              SessionMessageStatus status) {
        if (sessionId == null || sessionId <= 0) {
            return 0;
        }
        if (role == null || type == null || status == null) {
            return 0;
        }
        Integer count = dsl.selectCount()
                .from(SESSION_MESSAGES)
                .where(SESSION_MESSAGES.SESSION_ID.eq(sessionId)
                        .and(SESSION_MESSAGES.IS_DELETED.eq(false))
                        .and(SESSION_MESSAGES.ROLE.eq(role.toDatabaseValue()))
                        .and(SESSION_MESSAGES.TYPE.eq(type.toDatabaseValue()))
                        .and(SESSION_MESSAGES.STATUS.eq(status.toDatabaseValue())))
                .fetchOneInto(Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    protected SessionMessage map(SessionMessagesRecord record) {
        if (record == null) {
            return null;
        }
        SessionMessage m = new SessionMessage();
        m.setId(record.getId());
        m.setSessionId(record.getSessionId());
        m.setSeq(record.getSeq());
        m.setRole(SessionMessageRole.fromString(record.getRole()));
        m.setType(SessionMessageType.fromString(record.getType()));
        m.setContent(record.getContent());
        m.setRawContent(record.getRawContent());
        m.setParts(record.getParts());
        m.setName(record.getName());
        m.setToolCallId(record.getToolCallId());
        m.setMetadata(record.getMetadata());
        m.setStatus(SessionMessageStatus.fromString(record.getStatus()));
        m.setTokenCount(record.getTokenCount());
        m.setCreatedAt(record.getCreatedAt());
        m.setUpdatedAt(record.getUpdatedAt());
        m.setDeleted(record.getIsDeleted());
        return m;
    }
}


