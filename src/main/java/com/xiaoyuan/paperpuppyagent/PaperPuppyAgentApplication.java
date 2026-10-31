package com.xiaoyuan.paperpuppyagent;

import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import com.xiaoyuan.paperpuppyagent.grpc.config.BackendToolGrpcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OpenAiProperties.class, BackendToolGrpcProperties.class})
public class PaperPuppyAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperPuppyAgentApplication.class, args);
    }

}
