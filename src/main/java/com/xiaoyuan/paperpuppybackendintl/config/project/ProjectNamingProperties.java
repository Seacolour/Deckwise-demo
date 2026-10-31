package com.xiaoyuan.paperpuppybackendintl.config.project;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 项目命名配置：只在项目名仍为占位符时允许系统自动替换。
 */
@ConfigurationProperties(prefix = "app.project-naming")
public record ProjectNamingProperties(
        List<String> placeholderNames
) {
    public ProjectNamingProperties {
        if (placeholderNames == null || placeholderNames.isEmpty()) {
            placeholderNames = List.of("New Project");
        }
    }
}
