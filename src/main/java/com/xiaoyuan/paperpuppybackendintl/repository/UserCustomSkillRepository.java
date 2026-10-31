package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.UserCustomSkill;

import java.util.List;
import java.util.Optional;

/**
 * 用户自定义 PromptSkill Repository
 */
public interface UserCustomSkillRepository {

    /** 查询用户所有未删除的自定义技能 */
    List<UserCustomSkill> listByUserId(Long userId);

    /** 查询用户所有已启用的自定义技能（用于会话时拼接 prompt） */
    List<UserCustomSkill> listEnabledByUserId(Long userId);

    /** 按 ID 查询 */
    Optional<UserCustomSkill> findById(Long id);

    /** 按 userId + skillName 查询（唯一） */
    Optional<UserCustomSkill> findByUserIdAndSkillName(Long userId, String skillName);

    /** 插入新技能 */
    UserCustomSkill insert(UserCustomSkill skill);

    /** 更新技能内容/元数据 */
    boolean update(Long id, String displayName, String description, String content, String version);

    /** 切换启用/禁用 */
    boolean setEnabled(Long id, boolean enabled);

    /** 软删除 */
    boolean softDelete(Long id);

    /** 统计用户已有技能数量（用于限额校验） */
    int countByUserId(Long userId);
}
