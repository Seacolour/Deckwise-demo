package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ToggleSkillRequest {

    @NotBlank(message = "skillName 不能为空")
    @Size(max = 64, message = "skillName 不能超过 64 字符")
    private String skillName;

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
