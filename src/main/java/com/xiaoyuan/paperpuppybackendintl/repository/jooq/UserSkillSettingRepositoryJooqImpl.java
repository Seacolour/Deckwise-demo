package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.UserSkillSettingsRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserSkillSetting;
import com.xiaoyuan.paperpuppybackendintl.repository.UserSkillSettingRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.UserSkillSettings.USER_SKILL_SETTINGS;

@Repository
@Primary
@RequiredArgsConstructor
public class UserSkillSettingRepositoryJooqImpl extends BaseJooqRepository<UserSkillSettingsRecord, UserSkillSetting>
        implements UserSkillSettingRepository {

    private final DSLContext dsl;

    @Override
    public List<UserSkillSetting> listByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return dsl.selectFrom(USER_SKILL_SETTINGS)
                .where(USER_SKILL_SETTINGS.USER_ID.eq(userId)
                        .and(USER_SKILL_SETTINGS.IS_DELETED.eq(false)))
                .orderBy(USER_SKILL_SETTINGS.SKILL_NAME.asc())
                .fetch(this::map);
    }

    @Override
    public Optional<UserSkillSetting> findByUserIdAndSkillName(Long userId, String skillName) {
        if (userId == null || userId <= 0 || skillName == null || skillName.isBlank()) {
            return Optional.empty();
        }
        return dsl.selectFrom(USER_SKILL_SETTINGS)
                .where(USER_SKILL_SETTINGS.USER_ID.eq(userId)
                        .and(USER_SKILL_SETTINGS.SKILL_NAME.eq(skillName))
                        .and(USER_SKILL_SETTINGS.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public UserSkillSetting upsert(Long userId, String skillName, boolean enabled) {
        // ON CONFLICT on (user_id, skill_name) WHERE NOT is_deleted
        // jOOQ 对 partial unique index 的 ON CONFLICT 支持有限，改用 select + insert/update
        var existing = dsl.selectFrom(USER_SKILL_SETTINGS)
                .where(USER_SKILL_SETTINGS.USER_ID.eq(userId)
                        .and(USER_SKILL_SETTINGS.SKILL_NAME.eq(skillName))
                        .and(USER_SKILL_SETTINGS.IS_DELETED.eq(false)))
                .fetchOne();

        if (existing != null) {
            dsl.update(USER_SKILL_SETTINGS)
                    .set(USER_SKILL_SETTINGS.ENABLED, enabled)
                    .where(USER_SKILL_SETTINGS.ID.eq(existing.getId()))
                    .execute();
            existing.setEnabled(enabled);
            return map(existing);
        }

        UserSkillSettingsRecord r = dsl.insertInto(USER_SKILL_SETTINGS)
                .set(USER_SKILL_SETTINGS.USER_ID, userId)
                .set(USER_SKILL_SETTINGS.SKILL_NAME, skillName)
                .set(USER_SKILL_SETTINGS.ENABLED, enabled)
                .set(USER_SKILL_SETTINGS.IS_DELETED, false)
                .returning()
                .fetchOne();
        return map(r);
    }

    @Override
    public List<String> listDisabledSkillNames(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return dsl.select(USER_SKILL_SETTINGS.SKILL_NAME)
                .from(USER_SKILL_SETTINGS)
                .where(USER_SKILL_SETTINGS.USER_ID.eq(userId)
                        .and(USER_SKILL_SETTINGS.ENABLED.eq(false))
                        .and(USER_SKILL_SETTINGS.IS_DELETED.eq(false)))
                .fetch(USER_SKILL_SETTINGS.SKILL_NAME);
    }

    @Override
    public boolean softDelete(Long userId, String skillName) {
        if (userId == null || userId <= 0 || skillName == null || skillName.isBlank()) {
            return false;
        }
        int affected = dsl.update(USER_SKILL_SETTINGS)
                .set(USER_SKILL_SETTINGS.IS_DELETED, true)
                .where(USER_SKILL_SETTINGS.USER_ID.eq(userId)
                        .and(USER_SKILL_SETTINGS.SKILL_NAME.eq(skillName))
                        .and(USER_SKILL_SETTINGS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    protected UserSkillSetting map(UserSkillSettingsRecord record) {
        if (record == null) {
            return null;
        }
        UserSkillSetting s = new UserSkillSetting();
        s.setId(record.getId());
        s.setUserId(record.getUserId());
        s.setSkillName(record.getSkillName());
        s.setEnabled(record.getEnabled());
        s.setCreatedAt(record.getCreatedAt());
        s.setUpdatedAt(record.getUpdatedAt());
        s.setDeleted(record.getIsDeleted());
        return s;
    }
}
