package com.xiaoyuan.paperpuppybackendintl.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JacksonConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(JacksonConfig.class);

    @Test
    void jackson3SerializesJackson2JsonNodeAsJsonTree() throws Exception {
        JacksonConfig config = new JacksonConfig();
        com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper = config.jackson2Mapper();
        tools.jackson.databind.ObjectMapper springHttpMapper = tools.jackson.databind.json.JsonMapper.builder()
                .addModule(config.jackson2JsonNodeBridgeModule())
                .build();

        JsonNode model = jackson2Mapper.readTree("""
                {
                  "format": "deckwise-project",
                  "slides": [
                    {
                      "page": 1,
                      "title": "Intro"
                    }
                  ]
                }
                """);

        String serialized = springHttpMapper.writeValueAsString(new JsonNodePayload(
                model,
                Map.of("model", model)
        ));
        tools.jackson.databind.JsonNode root = springHttpMapper.readTree(serialized);

        assertEquals("deckwise-project", root.path("model").path("format").asText());
        assertEquals(1, root.path("model").path("slides").get(0).path("page").asInt());
        assertFalse(root.path("model").has("nodeType"));
        assertEquals("deckwise-project", root.path("nested").path("model").path("format").asText());
        assertFalse(root.path("nested").path("model").has("nodeType"));
    }

    @Test
    void springBootAutoConfiguredJackson3MapperUsesBridgeModule() {
        contextRunner.run(context -> {
            tools.jackson.databind.ObjectMapper springHttpMapper =
                    context.getBean(tools.jackson.databind.ObjectMapper.class);
            JsonNode model = context.getBean("jackson2Mapper", com.fasterxml.jackson.databind.ObjectMapper.class)
                    .readTree("{\"format\":\"deckwise-project\"}");

            tools.jackson.databind.JsonNode root = springHttpMapper.readTree(
                    springHttpMapper.writeValueAsString(new JsonNodePayload(model, null))
            );

            assertEquals("deckwise-project", root.path("model").path("format").asText());
            assertFalse(root.path("model").has("nodeType"));
        });
    }

    private record JsonNodePayload(JsonNode model, Object nested) {
    }
}
