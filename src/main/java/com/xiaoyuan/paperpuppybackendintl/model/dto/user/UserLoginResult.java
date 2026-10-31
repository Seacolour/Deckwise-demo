package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

/**
 * 用户登录结果（包含用户信息与登录 token）
 */
@Data
public class UserLoginResult {
    private UserDTO user;
    private String userToken;
}


