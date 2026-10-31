package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.UserRulesRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserRule;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRuleRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.UserRules.USER_RULES;

@Repository
@Primary
@RequiredArgsConstructor
public class UserRuleRepositoryJooqImpl extends BaseJooqRepository<UserRulesRecord, UserRule>
        implements UserRuleRepository {

    private final DSLContext dsl;

    @Override
    public List<UserRule> listByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return dsl.selectFrom(USER_RULES)
                .where(USER_RULES.USER_ID.eq(userId)
                        .and(USER_RULES.IS_DELETED.eq(false)))
                .orderBy(USER_RULES.CREATED_AT.asc())
                .fetch(this::map);
    }

    @Override
    public List<UserRule> listEnabledByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return dsl.selectFrom(USER_RULES)
                .where(USER_RULES.USER_ID.eq(userId)
                        .and(USER_RULES.ENABLED.eq(true))
                        .and(USER_RULES.IS_DELETED.eq(false)))
                .orderBy(USER_RULES.CREATED_AT.asc())
                .fetch(this::map);
    }

    @Override
    public Optional<UserRule> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(USER_RULES)
                .where(USER_RULES.ID.eq(id)
                        .and(USER_RULES.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public UserRule insert(UserRule rule) {
        UserRulesRecord r = dsl.insertInto(USER_RULES)
                .set(USER_RULES.USER_ID, rule.getUserId())
                .set(USER_RULES.CONTENT, rule.getContent())
                .set(USER_RULES.ENABLED, rule.getEnabled() != null ? rule.getEnabled() : true)
                .set(USER_RULES.SOURCE, rule.getSource() != null ? rule.getSource() : "user")
                .set(USER_RULES.IS_DELETED, false)
                .returning()
                .fetchOne();
        return map(r);
    }

    @Override
    public boolean updateContent(Long id, String content) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_RULES)
                .set(USER_RULES.CONTENT, content)
                .where(USER_RULES.ID.eq(id)
                        .and(USER_RULES.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean setEnabled(Long id, boolean enabled) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_RULES)
                .set(USER_RULES.ENABLED, enabled)
                .where(USER_RULES.ID.eq(id)
                        .and(USER_RULES.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean softDelete(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_RULES)
                .set(USER_RULES.IS_DELETED, true)
                .where(USER_RULES.ID.eq(id)
                        .and(USER_RULES.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public int countByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        return dsl.fetchCount(
                dsl.selectFrom(USER_RULES)
                        .where(USER_RULES.USER_ID.eq(userId)
                                .and(USER_RULES.IS_DELETED.eq(false))));
    }

    @Override
    protected UserRule map(UserRulesRecord record) {
        if (record == null) {
            return null;
        }
        UserRule r = new UserRule();
        r.setId(record.getId());
        r.setUserId(record.getUserId());
        r.setContent(record.getContent());
        r.setEnabled(record.getEnabled());
        r.setSource(record.getSource());
        r.setCreatedAt(record.getCreatedAt());
        r.setUpdatedAt(record.getUpdatedAt());
        r.setDeleted(record.getIsDeleted());
        return r;
    }
}
