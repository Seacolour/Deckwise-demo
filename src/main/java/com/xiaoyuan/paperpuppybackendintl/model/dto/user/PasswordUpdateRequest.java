package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import lombok.Data;

@Data
public class PasswordUpdateRequest {
    private String email;
    private String code;
    private String newPassword;
}
