package com.xiaoyuan.paperpuppyagent.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads local tool-composition whitelist rules and decorates model-visible schemas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCompositionRegistry {

    private final ObjectMapper om;

    @Value("${agent.tool-compositions.registry:classpath*:tool-compositions/*.json}")
    private String registryPattern;

    private final Map<String, List<ToolCompositionRule>> rulesBySource = new HashMap<>();

    @PostConstruct
    public void init() {
        loadFromResources();
        int count = rulesBySource.values().stream().mapToInt(List::size).sum();
        log.info("[ToolCompositionRegistry] loaded rules: count={}, sources={}", count, rulesBySource.keySet());
    }

    public boolean hasRulesForSource(String source) {
        return !rulesForSource(source).isEmpty();
    }

    public List<ToolCompositionRule> rulesForSource(String source) {
        String key = normalize(source);
        if (key.isBlank()) {
            return List.of();
        }
        return rulesBySource.getOrDefault(key, List.of());
    }

    public Optional<ToolCompositionRule> findRule(String source, String then, String when) {
        String normalizedThen = normalize(then);
        String normalizedWhen = normalize(when);
        if (normalizedThen.isBlank()) {
            return Optional.empty();
        }
        for (ToolCompositionRule rule : rulesForSource(source)) {
            if (!normalizedThen.equals(rule.then())) {
                continue;
            }
            if (normalizedWhen.isBlank() || normalizedWhen.equals(rule.when())) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public ObjectNode decorateInputSchema(String functionName, ObjectNode inputSchema, boolean strict) {
        ObjectNode schema = inputSchema == null
                ? om.createObjectNode().put("type", "object")
                : inputSchema.deepCopy();
        List<ToolCompositionRule> rules = rulesForSource(functionName);
        if (rules.isEmpty() || strict) {
            return schema;
        }

        ObjectNode properties;
        JsonNode existingProps = schema.get("properties");
        if (existingProps != null && existingProps.isObject()) {
            properties = (ObjectNode) existingProps;
        } else {
            properties = schema.putObject("properties");
        }

        ObjectNode compose = properties.putObject("compose");
        compose.put("type", "object");
        compose.put("description", composeDescription(functionName, rules));

        ObjectNode composeProps = compose.putObject("properties");
        composeProps.putObject("then")
                .put("type", "string")
                .put("description", "The whitelisted tool to run after this source tool if the assertion passes. Allowed: " + allowedThenList(rules));
        composeProps.putObject("when")
                .put("type", "string")
                .put("description", "Composition assertion. Allowed: " + allowedWhenList(rules));
        composeProps.putObject("on_assertion_failed")
                .put("type", "string")
                .put("description", "Use return_source_result. The framework returns the source result and skip reason without guessing.");
        ObjectNode nextArgs = composeProps.putObject("next_args");
        nextArgs.put("type", "object");
        nextArgs.put("description", "Optional arguments for the next tool, such as max_nodes/include_card_json or row-window limits.");
        nextArgs.put("additionalProperties", true);
        ArrayNode required = compose.putArray("required");
        required.add("then");
        compose.put("additionalProperties", false);

        return schema;
    }

    public String instructionsForModel() {
        int count = rulesBySource.values().stream().mapToInt(List::size).sum();
        if (count == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Tool Composition Runtime\n");
        sb.append("Selected read-only tools support a `compose` argument. Use it only when the user asks to locate/search and immediately inspect the result. ");
        sb.append("The framework runs the source tool first, then runs the next tool only if the assertion passes. If the assertion fails, continue from the returned candidates.\n");
        for (ToolCompositionRule rule : rulesBySource.values().stream().flatMap(List::stream).toList()) {
            sb.append("- ").append(rule.source())
                    .append(" -> ").append(rule.then())
                    .append(" when ").append(rule.when())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private void loadFromResources() {
        rulesBySource.clear();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = Objects.toString(registryPattern, "").trim();
        if (pattern.isBlank()) {
            pattern = "classpath*:tool-compositions/*.json";
        }
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                if (resource == null || !resource.exists()) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    parseResource(om.readTree(in), safeResourceName(resource));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load tool composition registry: " + pattern, e);
        }
    }

    private void parseResource(JsonNode root, String sourceName) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("invalid tool composition registry (not object): " + sourceName);
        }
        JsonNode rules = root.path("rules");
        if (!rules.isArray()) {
            throw new IllegalArgumentException("invalid tool composition registry (missing rules[]): " + sourceName);
        }
        for (JsonNode node : rules) {
            ToolCompositionRule rule = parseRule(node, sourceName);
            rulesBySource.computeIfAbsent(rule.source(), ignored -> new ArrayList<>()).add(rule);
        }
    }

    private ToolCompositionRule parseRule(JsonNode node, String sourceName) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("invalid tool composition rule: " + sourceName);
        }
        String source = normalize(node.path("source").asText(""));
        String then = normalize(node.path("then").asText(""));
        String when = normalize(node.path("when").asText(""));
        if (source.isBlank() || then.isBlank() || when.isBlank()) {
            throw new IllegalArgumentException("invalid tool composition rule (source/then/when required): " + sourceName);
        }
        int maxDepth = Math.max(1, node.path("max_depth").asInt(1));
        boolean allowWriteTools = node.path("safety").path("allow_write_tools").asBoolean(false);
        JsonNode argMapping = node.path("arg_mapping").isObject()
                ? node.path("arg_mapping").deepCopy()
                : om.createObjectNode();
        return new ToolCompositionRule(source, then, when, maxDepth, allowWriteTools, argMapping);
    }

    private String composeDescription(String functionName, List<ToolCompositionRule> rules) {
        return "Optional runtime composition request for " + functionName
                + ". Use only when the user wants this tool's result to be inspected by a whitelisted next tool. Allowed next tools: "
                + allowedThenList(rules) + ".";
    }

    private static String allowedThenList(List<ToolCompositionRule> rules) {
        return rules.stream().map(ToolCompositionRule::then).distinct().toList().toString();
    }

    private static String allowedWhenList(List<ToolCompositionRule> rules) {
        return rules.stream().map(ToolCompositionRule::when).distinct().toList().toString();
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim();
    }

    private static String safeResourceName(Resource resource) {
        try {
            return resource.getDescription();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
