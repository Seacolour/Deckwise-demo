package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.UserMemoriesRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserMemory;
import com.xiaoyuan.paperpuppybackendintl.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.UserMemories.USER_MEMORIES;

@Repository
@Primary
@RequiredArgsConstructor
public class UserMemoryRepositoryJooqImpl extends BaseJooqRepository<UserMemoriesRecord, UserMemory>
        implements UserMemoryRepository {

    private final DSLContext dsl;

    @Override
    public List<UserMemory> listByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return dsl.selectFrom(USER_MEMORIES)
                .where(USER_MEMORIES.USER_ID.eq(userId)
                        .and(USER_MEMORIES.IS_DELETED.eq(false)))
                .orderBy(USER_MEMORIES.UPDATED_AT.desc())
                .fetch(this::map);
    }

    @Override
    public Optional<UserMemory> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(USER_MEMORIES)
                .where(USER_MEMORIES.ID.eq(id)
                        .and(USER_MEMORIES.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public Optional<UserMemory> findByUserIdAndTitleLike(Long userId, String titleKeyword) {
        if (userId == null || userId <= 0 || titleKeyword == null || titleKeyword.isBlank()) {
            return Optional.empty();
        }
        return dsl.selectFrom(USER_MEMORIES)
                .where(USER_MEMORIES.USER_ID.eq(userId)
                        .and(USER_MEMORIES.TITLE.likeIgnoreCase("%" + titleKeyword + "%"))
                        .and(USER_MEMORIES.IS_DELETED.eq(false)))
                .orderBy(USER_MEMORIES.UPDATED_AT.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    @Override
    public UserMemory insert(UserMemory memory) {
        UserMemoriesRecord r = dsl.insertInto(USER_MEMORIES)
                .set(USER_MEMORIES.USER_ID, memory.getUserId())
                .set(USER_MEMORIES.TITLE, memory.getTitle())
                .set(USER_MEMORIES.CONTENT, memory.getContent())
                .set(USER_MEMORIES.SOURCE, memory.getSource() != null ? memory.getSource() : "ai")
                .set(USER_MEMORIES.IS_DELETED, false)
                .returning()
                .fetchOne();
        return map(r);
    }

    @Override
    public boolean updateContent(Long id, String title, String content) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_MEMORIES)
                .set(USER_MEMORIES.TITLE, title)
                .set(USER_MEMORIES.CONTENT, content)
                .where(USER_MEMORIES.ID.eq(id)
                        .and(USER_MEMORIES.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean softDelete(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_MEMORIES)
                .set(USER_MEMORIES.IS_DELETED, true)
                .where(USER_MEMORIES.ID.eq(id)
                        .and(USER_MEMORIES.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public int countByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        return dsl.fetchCount(
                dsl.selectFrom(USER_MEMORIES)
                        .where(USER_MEMORIES.USER_ID.eq(userId)
                                .and(USER_MEMORIES.IS_DELETED.eq(false))));
    }

    @Override
    protected UserMemory map(UserMemoriesRecord record) {
        if (record == null) {
            return null;
        }
        UserMemory m = new UserMemory();
        m.setId(record.getId());
        m.setUserId(record.getUserId());
        m.setTitle(record.getTitle());
        m.setContent(record.getContent());
        m.setSource(record.getSource());
        m.setCreatedAt(record.getCreatedAt());
        m.setUpdatedAt(record.getUpdatedAt());
        m.setDeleted(record.getIsDeleted());
        return m;
    }
}
