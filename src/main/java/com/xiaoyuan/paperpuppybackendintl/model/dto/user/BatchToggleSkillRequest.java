package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchToggleSkillRequest {

    @NotNull(message = "skills 不能为空")
    @Size(min = 1, max = 50, message = "skills 数量需在 1-50 之间")
    private List<Item> skills;

    @Data
    public static class Item {
        @NotBlank(message = "skillName 不能为空")
        @Size(max = 64)
        private String skillName;

        @NotNull(message = "enabled 不能为空")
        private Boolean enabled;
    }
}
