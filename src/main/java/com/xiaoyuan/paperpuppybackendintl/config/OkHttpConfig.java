package com.xiaoyuan.paperpuppybackendintl.config;

import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OkHttpConfig {

    @Bean("okHttpClientAlt")
    @ConditionalOnMissingBean(OkHttpClient.class)
    public OkHttpClient okHttpClientAlt() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // 连接超时
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // 读取超时
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // 写入超时
                .build();
    }

}
