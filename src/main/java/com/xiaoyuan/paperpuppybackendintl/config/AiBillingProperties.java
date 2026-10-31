package com.xiaoyuan.paperpuppybackendintl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI usage 计费配置。
 *
 * <p>模型价格来自 model-catalog.json，避免和可选模型列表漂移。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.ai-billing")
public class AiBillingProperties {

    /**
     * 总开关。关闭时仍会记录 usage 账本，但不会计算价格快照。
     */
    private boolean enabled = false;

    /**
     * 币种，仅用于账本快照展示。
     */
    private String currency = "USD";

    /**
     * 当前默认供应商。
     */
    private String provider = "openai";
}
