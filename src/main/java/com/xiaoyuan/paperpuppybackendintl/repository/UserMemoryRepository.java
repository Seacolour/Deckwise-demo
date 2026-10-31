package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.UserMemory;

import java.util.List;
import java.util.Optional;

/**
 * 用户记忆 Repository
 */
public interface UserMemoryRepository {

    /** 查询用户所有未删除的记忆 */
    List<UserMemory> listByUserId(Long userId);

    /** 按 ID 查询 */
    Optional<UserMemory> findById(Long id);

    /** 按 title 模糊查找（用于 AI 去重） */
    Optional<UserMemory> findByUserIdAndTitleLike(Long userId, String titleKeyword);

    /** 新增记忆，返回含自增 ID 的实体 */
    UserMemory insert(UserMemory memory);

    /** 更新记忆的标题和内容 */
    boolean updateContent(Long id, String title, String content);

    /** 软删除 */
    boolean softDelete(Long id);

    /** 统计用户未删除的记忆数（用于限额） */
    int countByUserId(Long userId);
}
