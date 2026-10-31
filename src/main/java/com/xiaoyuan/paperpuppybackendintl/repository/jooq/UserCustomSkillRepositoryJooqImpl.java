package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.UserCustomSkillsRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCustomSkill;
import com.xiaoyuan.paperpuppybackendintl.repository.UserCustomSkillRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.UserCustomSkills.USER_CUSTOM_SKILLS;

@Repository
@Primary
@RequiredArgsConstructor
public class UserCustomSkillRepositoryJooqImpl extends BaseJooqRepository<UserCustomSkillsRecord, UserCustomSkill>
        implements UserCustomSkillRepository {

    private final DSLContext dsl;

    @Override
    public List<UserCustomSkill> listByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return dsl.selectFrom(USER_CUSTOM_SKILLS)
                .where(USER_CUSTOM_SKILLS.USER_ID.eq(userId)
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .orderBy(USER_CUSTOM_SKILLS.CREATED_AT.desc())
                .fetch(this::map);
    }

    @Override
    public List<UserCustomSkill> listEnabledByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return dsl.selectFrom(USER_CUSTOM_SKILLS)
                .where(USER_CUSTOM_SKILLS.USER_ID.eq(userId)
                        .and(USER_CUSTOM_SKILLS.ENABLED.eq(true))
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .orderBy(USER_CUSTOM_SKILLS.CREATED_AT.desc())
                .fetch(this::map);
    }

    @Override
    public Optional<UserCustomSkill> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return dsl.selectFrom(USER_CUSTOM_SKILLS)
                .where(USER_CUSTOM_SKILLS.ID.eq(id)
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public Optional<UserCustomSkill> findByUserIdAndSkillName(Long userId, String skillName) {
        if (userId == null || userId <= 0 || skillName == null || skillName.isBlank()) {
            return Optional.empty();
        }
        return dsl.selectFrom(USER_CUSTOM_SKILLS)
                .where(USER_CUSTOM_SKILLS.USER_ID.eq(userId)
                        .and(USER_CUSTOM_SKILLS.SKILL_NAME.eq(skillName))
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public UserCustomSkill insert(UserCustomSkill skill) {
        UserCustomSkillsRecord r = dsl.insertInto(USER_CUSTOM_SKILLS)
                .set(USER_CUSTOM_SKILLS.USER_ID, skill.getUserId())
                .set(USER_CUSTOM_SKILLS.SKILL_NAME, skill.getSkillName())
                .set(USER_CUSTOM_SKILLS.DISPLAY_NAME, skill.getDisplayName())
                .set(USER_CUSTOM_SKILLS.DESCRIPTION, skill.getDescription())
                .set(USER_CUSTOM_SKILLS.CONTENT, skill.getContent())
                .set(USER_CUSTOM_SKILLS.VERSION, skill.getVersion())
                .set(USER_CUSTOM_SKILLS.SOURCE_TYPE, skill.getSourceType())
                .set(USER_CUSTOM_SKILLS.ENABLED, skill.getEnabled() != null ? skill.getEnabled() : true)
                .set(USER_CUSTOM_SKILLS.IS_DELETED, false)
                .returning()
                .fetchOne();
        return map(r);
    }

    @Override
    public boolean update(Long id, String displayName, String description, String content, String version) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_CUSTOM_SKILLS)
                .set(USER_CUSTOM_SKILLS.DISPLAY_NAME, displayName)
                .set(USER_CUSTOM_SKILLS.DESCRIPTION, description)
                .set(USER_CUSTOM_SKILLS.CONTENT, content)
                .set(USER_CUSTOM_SKILLS.VERSION, version)
                .where(USER_CUSTOM_SKILLS.ID.eq(id)
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean setEnabled(Long id, boolean enabled) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_CUSTOM_SKILLS)
                .set(USER_CUSTOM_SKILLS.ENABLED, enabled)
                .where(USER_CUSTOM_SKILLS.ID.eq(id)
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public boolean softDelete(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        int affected = dsl.update(USER_CUSTOM_SKILLS)
                .set(USER_CUSTOM_SKILLS.IS_DELETED, true)
                .where(USER_CUSTOM_SKILLS.ID.eq(id)
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .execute();
        return affected > 0;
    }

    @Override
    public int countByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        return dsl.selectCount()
                .from(USER_CUSTOM_SKILLS)
                .where(USER_CUSTOM_SKILLS.USER_ID.eq(userId)
                        .and(USER_CUSTOM_SKILLS.IS_DELETED.eq(false)))
                .fetchOne(0, int.class);
    }

    @Override
    protected UserCustomSkill map(UserCustomSkillsRecord record) {
        if (record == null) {
            return null;
        }
        UserCustomSkill s = new UserCustomSkill();
        s.setId(record.getId());
        s.setUserId(record.getUserId());
        s.setSkillName(record.getSkillName());
        s.setDisplayName(record.getDisplayName());
        s.setDescription(record.getDescription());
        s.setContent(record.getContent());
        s.setVersion(record.getVersion());
        s.setSourceType(record.getSourceType());
        s.setEnabled(record.getEnabled());
        s.setCreatedAt(record.getCreatedAt());
        s.setUpdatedAt(record.getUpdatedAt());
        s.setDeleted(record.getIsDeleted());
        return s;
    }
}
