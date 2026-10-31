package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRuleRequest {
    @NotBlank(message = "规则内容不能为空")
    @Size(max = 2000, message = "规则内容不能超过2000字符")
    private String content;
}
