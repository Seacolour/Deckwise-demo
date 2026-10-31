package com.xiaoyuan.paperpuppybackendintl.infrastructure.r2;

import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * @author Seacolour
 * @date 2025/12/17 17:46
 * @description:
 */
@Configuration
@RequiredArgsConstructor
public class R2S3ClientConfig {

    private final StorageR2Properties props;

    @Bean
    public S3Client r2S3Client() {
        ThrowUtils.throwIf(props == null, ErrorCode.SYSTEM_ERROR, "R2 配置缺失");
        StorageR2Properties.Node node = props.effectiveNode();
        ThrowUtils.throwIf(node == null, ErrorCode.SYSTEM_ERROR, "storage.r2 节点配置缺失");
        return buildClient(node, "storage.r2");
    }

    @Bean
    public S3Presigner r2S3Presigner() {
        ThrowUtils.throwIf(props == null, ErrorCode.SYSTEM_ERROR, "R2 配置缺失");
        StorageR2Properties.Node node = props.effectiveNode();
        ThrowUtils.throwIf(node == null, ErrorCode.SYSTEM_ERROR, "storage.r2 节点配置缺失");
        return buildPresigner(node, "storage.r2");
    }

    static S3Client buildClient(StorageR2Properties.Node node, String configPath) {
        ThrowUtils.throwIf(node == null, ErrorCode.SYSTEM_ERROR, configPath + " 节点为空");
        ThrowUtils.throwIf(isBlank(node.getEndpoint()), ErrorCode.SYSTEM_ERROR, configPath + ".endpoint 未配置");
        ThrowUtils.throwIf(isBlank(node.getBucket()), ErrorCode.SYSTEM_ERROR, configPath + ".bucket 未配置");
        ThrowUtils.throwIf(isBlank(node.getAccessKey()), ErrorCode.SYSTEM_ERROR, configPath + ".access-key 未配置");
        ThrowUtils.throwIf(isBlank(node.getSecretKey()), ErrorCode.SYSTEM_ERROR, configPath + ".secret-key 未配置");

        String region = isBlank(node.getRegion()) ? "auto" : node.getRegion().trim();

        return S3Client.builder()
                .endpointOverride(URI.create(node.getEndpoint().trim()))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        node.getAccessKey().trim(),
                                        node.getSecretKey().trim()
                                )
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .chunkedEncodingEnabled(false)
                                .build()
                )
                .build();
    }

    static S3Presigner buildPresigner(StorageR2Properties.Node node, String configPath) {
        ThrowUtils.throwIf(node == null, ErrorCode.SYSTEM_ERROR, configPath + " 节点为空");
        ThrowUtils.throwIf(isBlank(node.getEndpoint()), ErrorCode.SYSTEM_ERROR, configPath + ".endpoint 未配置");
        ThrowUtils.throwIf(isBlank(node.getBucket()), ErrorCode.SYSTEM_ERROR, configPath + ".bucket 未配置");
        ThrowUtils.throwIf(isBlank(node.getAccessKey()), ErrorCode.SYSTEM_ERROR, configPath + ".access-key 未配置");
        ThrowUtils.throwIf(isBlank(node.getSecretKey()), ErrorCode.SYSTEM_ERROR, configPath + ".secret-key 未配置");

        String region = isBlank(node.getRegion()) ? "auto" : node.getRegion().trim();
        return S3Presigner.builder()
                .endpointOverride(URI.create(node.getEndpoint().trim()))
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        node.getAccessKey().trim(),
                                        node.getSecretKey().trim()
                                )
                        )
                )
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .chunkedEncodingEnabled(false)
                                .build()
                )
                .build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
