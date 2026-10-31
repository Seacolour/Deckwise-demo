package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.UserSkillSetting;

import java.util.List;
import java.util.Optional;

/**
 * 用户技能开关设置 Repository
 */
public interface UserSkillSettingRepository {

    /** 查询用户所有未删除的技能设置 */
    List<UserSkillSetting> listByUserId(Long userId);

    /** 按 userId + skillName 查询（唯一） */
    Optional<UserSkillSetting> findByUserIdAndSkillName(Long userId, String skillName);

    /** 插入或更新（upsert）：如果已存在则更新 enabled */
    UserSkillSetting upsert(Long userId, String skillName, boolean enabled);

    /** 批量查询用户已关闭的 skill 名称列表（用于会话启动时快速过滤） */
    List<String> listDisabledSkillNames(Long userId);

    /** 软删除（恢复为默认状态） */
    boolean softDelete(Long userId, String skillName);
}
