package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.SkillRegistryItem;

import java.util.List;
import java.util.Optional;

/**
 * 技能注册表 Repository
 */
public interface SkillRegistryRepository {

    /** 查询所有未删除的注册技能（按 display_order 排序） */
    List<SkillRegistryItem> listAll();

    /** 查询所有用户可配置的技能（排除 system_locked） */
    List<SkillRegistryItem> listConfigurable();

    /** 按 skill_name 查询 */
    Optional<SkillRegistryItem> findBySkillName(String skillName);

    /** 判断某个 skill 是否是系统锁定（不可关闭） */
    boolean isSystemLocked(String skillName);
}
