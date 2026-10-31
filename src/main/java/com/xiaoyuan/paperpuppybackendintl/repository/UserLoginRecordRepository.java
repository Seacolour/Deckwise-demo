package com.xiaoyuan.paperpuppybackendintl.repository;

import com.xiaoyuan.paperpuppybackendintl.model.entity.UserLoginRecord;

/**
 * 用户登录记录持久化接口
 */
public interface UserLoginRecordRepository {
    UserLoginRecord save(UserLoginRecord record);
}


