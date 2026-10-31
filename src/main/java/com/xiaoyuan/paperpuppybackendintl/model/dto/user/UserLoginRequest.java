package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

/**
 * 用户登录请求体
 */
@Data
public class UserLoginRequest {

    private String email;

    /**
     * 邮箱验证码
     */
    private String code;

    /**
     * 可选：客户端语言偏好，例如 "zh-CN" / "en-US"。
     */
    private String locale;

    /**
     * 可选：客户端国家/地区二字码，例如 "CN" / "US"。
     */
    private String country;

    /**
     * 可选：IANA 时区名，例如 "Asia/Shanghai"。
     */
    private String timeZone;
}


