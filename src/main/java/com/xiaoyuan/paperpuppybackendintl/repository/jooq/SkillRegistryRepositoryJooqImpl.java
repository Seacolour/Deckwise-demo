package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.records.SkillRegistryRecord;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SkillRegistryItem;
import com.xiaoyuan.paperpuppybackendintl.repository.SkillRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.SkillRegistry.SKILL_REGISTRY;

@Repository
@Primary
@RequiredArgsConstructor
public class SkillRegistryRepositoryJooqImpl extends BaseJooqRepository<SkillRegistryRecord, SkillRegistryItem>
        implements SkillRegistryRepository {

    private final DSLContext dsl;

    @Override
    public List<SkillRegistryItem> listAll() {
        return dsl.selectFrom(SKILL_REGISTRY)
                .where(SKILL_REGISTRY.IS_DELETED.eq(false))
                .orderBy(SKILL_REGISTRY.DISPLAY_ORDER.asc(), SKILL_REGISTRY.SKILL_NAME.asc())
                .fetch(this::map);
    }

    @Override
    public List<SkillRegistryItem> listConfigurable() {
        return dsl.selectFrom(SKILL_REGISTRY)
                .where(SKILL_REGISTRY.IS_DELETED.eq(false)
                        .and(SKILL_REGISTRY.SYSTEM_LOCKED.eq(false)))
                .orderBy(SKILL_REGISTRY.DISPLAY_ORDER.asc(), SKILL_REGISTRY.SKILL_NAME.asc())
                .fetch(this::map);
    }

    @Override
    public Optional<SkillRegistryItem> findBySkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return Optional.empty();
        }
        return dsl.selectFrom(SKILL_REGISTRY)
                .where(SKILL_REGISTRY.SKILL_NAME.eq(skillName)
                        .and(SKILL_REGISTRY.IS_DELETED.eq(false)))
                .fetchOptional(this::map);
    }

    @Override
    public boolean isSystemLocked(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return false;
        }
        return dsl.fetchExists(
                dsl.selectFrom(SKILL_REGISTRY)
                        .where(SKILL_REGISTRY.SKILL_NAME.eq(skillName)
                                .and(SKILL_REGISTRY.SYSTEM_LOCKED.eq(true))
                                .and(SKILL_REGISTRY.IS_DELETED.eq(false))));
    }

    @Override
    protected SkillRegistryItem map(SkillRegistryRecord record) {
        if (record == null) {
            return null;
        }
        SkillRegistryItem item = new SkillRegistryItem();
        item.setId(record.getId());
        item.setSkillName(record.getSkillName());
        item.setDisplayName(record.getDisplayName());
        item.setDescription(record.getDescription());
        item.setCategory(record.getCategory());
        item.setSystemLocked(record.getSystemLocked());
        item.setDefaultEnabled(record.getDefaultEnabled());
        item.setDisplayOrder(record.getDisplayOrder());
        item.setIcon(record.getIcon());
        item.setCreatedAt(record.getCreatedAt());
        item.setUpdatedAt(record.getUpdatedAt());
        item.setDeleted(record.getIsDeleted());
        return item;
    }
}
