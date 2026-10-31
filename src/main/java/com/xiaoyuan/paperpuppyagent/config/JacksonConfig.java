package com.xiaoyuan.paperpuppyagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MVP：显式提供 ObjectMapper。
 *
 * <p>注意：当前项目依赖了 webmvc 但未必引入完整的 jackson auto-config starter，因此这里手动提供。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

