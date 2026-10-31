package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新自定义技能内容/元数据请求
 */
@Data
public class UpdateCustomSkillRequest {

    @Size(max = 256, message = "displayName 不能超过 256 字符")
    private String displayName;

    @Size(max = 1024, message = "description 不能超过 1024 字符")
    private String description;

    @NotBlank(message = "content 不能为空")
    private String content;

    @Size(max = 32, message = "version 不能超过 32 字符")
    private String version;
}
