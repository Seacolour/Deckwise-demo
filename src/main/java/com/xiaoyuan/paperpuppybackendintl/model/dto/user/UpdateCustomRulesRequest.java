package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户自定义规则请求
 */
@Data
public class UpdateCustomRulesRequest {

    /**
     * 自定义规则内容（可为空，最多 2000 字符）
     */
    @Size(max = 2000, message = "自定义规则最多 2000 字符")
    private String customRules;
}
