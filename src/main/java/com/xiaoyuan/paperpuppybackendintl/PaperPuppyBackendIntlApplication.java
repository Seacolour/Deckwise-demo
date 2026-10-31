package com.xiaoyuan.paperpuppybackendintl;

import com.xiaoyuan.paperpuppybackendintl.config.*;
import com.xiaoyuan.paperpuppybackendintl.grpc.config.AgentGrpcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AgentGrpcProperties.class,
        TokenSecurityProperties.class,
        StorageR2Properties.class, ChatAgentProperties.class})
@ConfigurationPropertiesScan
public class PaperPuppyBackendIntlApplication {

    public static void main(String[] args) {
        if (Boolean.parseBoolean(System.getenv().getOrDefault("PAPERPUPPY_PROXY_CHECK", "false"))) {
            try {
                var proxies = ProxySelector.getDefault().select(new URI("https://www.google.com"));
                System.out.println("[Proxy] Using: " + (proxies.isEmpty() ? "DIRECT" : proxies.getFirst()));
            } catch (Exception e) {
                System.err.println("[Proxy] Failed to detect proxy: " + e.getMessage());
            }

            try {
                URL url = new URI("https://api.ipify.org").toURL();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    System.out.println("[Proxy] Public IP: " + in.readLine());
                }
            } catch (Exception e) {
                System.err.println("[Proxy] Failed to fetch public IP: " + e.getMessage());
            }
        }
        SpringApplication.run(PaperPuppyBackendIntlApplication.class, args);
    }

}
