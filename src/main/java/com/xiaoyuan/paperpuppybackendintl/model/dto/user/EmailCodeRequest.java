package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

/**
 * 发送邮箱验证码请求体
 */
@Data
public class EmailCodeRequest {

    private String email;
}


