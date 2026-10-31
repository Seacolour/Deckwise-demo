package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.model-catalog")
public class ModelCatalogProperties {

    private boolean enabled = true;

    private String location = "classpath:model-catalog.json";
}
