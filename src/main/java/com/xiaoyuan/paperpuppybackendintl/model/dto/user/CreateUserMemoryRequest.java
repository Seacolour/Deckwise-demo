package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserMemoryRequest {
    @NotBlank(message = "记忆标题不能为空")
    @Size(max = 200, message = "记忆标题不能超过200字符")
    private String title;

    @NotBlank(message = "记忆内容不能为空")
    @Size(max = 5000, message = "记忆内容不能超过5000字符")
    private String content;
}
