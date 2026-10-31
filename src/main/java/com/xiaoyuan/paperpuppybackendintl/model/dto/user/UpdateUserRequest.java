package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

/**
 * 更新当前用户信息请求体
 */
@Data
public class UpdateUserRequest {

    /**
     * 昵称 / 展示名称
     */
    private String name;

}


