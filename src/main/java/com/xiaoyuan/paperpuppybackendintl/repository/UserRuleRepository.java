package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.UserRule;

import java.util.List;
import java.util.Optional;

/**
 * 用户自定义规则 Repository
 */
public interface UserRuleRepository {

    /** 查询用户所有未删除的规则 */
    List<UserRule> listByUserId(Long userId);

    /** 查询用户所有启用的规则（用于注入 AI） */
    List<UserRule> listEnabledByUserId(Long userId);

    /** 按 ID 查询 */
    Optional<UserRule> findById(Long id);

    /** 新增规则，返回含自增 ID 的实体 */
    UserRule insert(UserRule rule);

    /** 更新规则内容 */
    boolean updateContent(Long id, String content);

    /** 启用/禁用 */
    boolean setEnabled(Long id, boolean enabled);

    /** 软删除 */
    boolean softDelete(Long id);

    /** 统计用户未删除的规则数（用于限额） */
    int countByUserId(Long userId);
}
