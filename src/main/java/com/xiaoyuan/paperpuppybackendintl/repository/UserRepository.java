package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * User 持久化接口
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    User save(User user);

    /**
     * 批量查询（避免 N+1）。
     */
    List<User> findByIds(List<Long> ids);

}


