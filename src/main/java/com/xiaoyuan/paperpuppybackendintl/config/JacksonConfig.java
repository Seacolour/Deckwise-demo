package com.xiaoyuan.paperpuppybackendintl.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.Map;

/**
 * Jackson ObjectMapper 配置。
 *
 * <p>Spring Boot 4.0 / Spring Framework 7 的 HTTP message converter 使用 Jackson 3.x（tools.jackson）。
 * 但部分第三方库（如 Nimbus JOSE）仍依赖 Jackson 2.x（com.fasterxml.jackson），
 * 因此项目中两个版本共存。</p>
 *
 * <p>本 Bean 提供 Jackson 2.x 的 ObjectMapper，供业务层 / 第三方协议层注入使用。
 * 注入时请使用 {@code @Qualifier("jackson2Mapper")}，以明确区分版本。</p>
 *
 * <p>HTTP 响应仍由 Spring Boot 的 Jackson 3.x 序列化。为避免 Jackson 2 的 {@link JsonNode}
 * 在响应中被 Jackson 3 当作普通 Java bean 展开，本配置同时注册一个 Jackson 3 模块，
 * 专门把 Jackson 2 JsonNode 按真实 JSON 树写出。</p>
 *
 * <p>TODO(tech-debt): 升级 {@code com.openai:openai-java} 时，重新检查其核心依赖是否已完全切换到
 * Jackson 3（tools.jackson）。若 OpenAI SDK 不再依赖 Jackson 2.x，可评估删除本配置并将项目内
 * JSON 处理统一收敛到 Jackson 3。</p>
 *
 * <p>注意：Redis 序列化使用自己的 ObjectMapper（带 default typing），不要复用这里的配置。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * Jackson 2.x ObjectMapper — 供内部业务代码和第三方 SDK 交互使用。
     * <p>不标记 @Primary，避免与 Spring 自动配置的 Jackson 3.x ObjectMapper 冲突。</p>
     */
    @Bean
    @Qualifier("jackson2Mapper")
    public ObjectMapper jackson2Mapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }

    @Bean
    public SimpleModule jackson2JsonNodeBridgeModule() {
        SimpleModule module = new SimpleModule("jackson2-json-node-bridge");
        module.addSerializer(JsonNode.class, new Jackson2JsonNodeSerializer());
        return module;
    }

    private static final class Jackson2JsonNodeSerializer extends StdSerializer<JsonNode> {

        private Jackson2JsonNodeSerializer() {
            super(JsonNode.class);
        }

        @Override
        public void serialize(JsonNode value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            writeNode(value, gen);
        }

        private void writeNode(JsonNode node, JsonGenerator gen) throws JacksonException {
            if (node == null || node.isNull() || node.isMissingNode()) {
                gen.writeNull();
                return;
            }
            if (node.isObject()) {
                gen.writeStartObject();
                for (Map.Entry<String, JsonNode> entry : node.properties()) {
                    gen.writeName(entry.getKey());
                    writeNode(entry.getValue(), gen);
                }
                gen.writeEndObject();
                return;
            }
            if (node.isArray()) {
                gen.writeStartArray();
                for (JsonNode child : node) {
                    writeNode(child, gen);
                }
                gen.writeEndArray();
                return;
            }
            if (node.isTextual()) {
                gen.writeString(node.textValue());
                return;
            }
            if (node.isBoolean()) {
                gen.writeBoolean(node.booleanValue());
                return;
            }
            if (node.isIntegralNumber()) {
                if (node.canConvertToInt()) {
                    gen.writeNumber(node.intValue());
                } else if (node.canConvertToLong()) {
                    gen.writeNumber(node.longValue());
                } else {
                    gen.writeNumber(node.bigIntegerValue());
                }
                return;
            }
            if (node.isBigDecimal()) {
                gen.writeNumber(node.decimalValue());
                return;
            }
            if (node.isFloatingPointNumber()) {
                gen.writeNumber(node.doubleValue());
                return;
            }
            if (node.isBinary()) {
                gen.writeString(node.asText());
                return;
            }
            gen.writeString(node.asText());
        }
    }
}


