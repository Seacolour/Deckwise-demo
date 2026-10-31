// src/main/java/com/xiaoyuan/scidemo/config/OpenAlexProps.java
package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "paper.openalex")
public class OpenAlexProps {
    private String baseUrl;
    private List<String> mailtoPool;
    private String userAgent;
    private Integer defaultPerPage;
    /** 默认 select 精简字段，减少流量 */
    private String defaultSelect;
    /** Sci-Hub 仅字符串展示 */
    private String sciHubBase;
    /** 重试次数/基础退避（毫秒） */
    private int maxAttempts;
    private long baseBackoffMs;

}
