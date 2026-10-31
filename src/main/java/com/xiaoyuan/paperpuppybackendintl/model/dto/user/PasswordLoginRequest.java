package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

@Data
public class PasswordLoginRequest {
    private String email;
    private String password;
}
