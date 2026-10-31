package com.xiaoyuan.paperpuppybackendintl.config;


import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class OkHttpClientConfig {

    @Bean
    @Primary
    public OkHttpClient okHttpClient(OpenAlexProps props) {
        // 复用 props 的超时与默认配置（若需要，可以在 OpenAlexProps 中再补充超时配置）
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(5_000))
                .readTimeout(Duration.ofMillis(10_000))
                .writeTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(60))
                .build();
    }
}
