package com.xiaoyuan.paperpuppyagent.core.skill.binding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加载 skill.binding.json，将 function_name 映射到运行时绑定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillBindingCatalog {
    private final ObjectMapper om;
    private final SkillBindingValidator validator;

    @Value("${skills.bindings:classpath*:skills/paperpuppy-internal/skill.binding.json}")
    private String bindingsPattern;

    private final Map<String, SkillFunctionBinding> bindingsByFunction = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadFromBindings();
        validator.validateCoverage(bindingsByFunction);
        log.info("[SkillBindingCatalog] loaded bindings: count={}, names={}",
                bindingsByFunction.size(), bindingsByFunction.keySet());
    }

    public SkillFunctionBinding getBinding(String functionName) {
        if (functionName == null) {
            return null;
        }
        return bindingsByFunction.get(functionName);
    }

    public List<SkillFunctionBinding> listBindings() {
        ArrayList<SkillFunctionBinding> out = new ArrayList<>(bindingsByFunction.values());
        out.sort(Comparator.comparing(SkillFunctionBinding::functionName));
        return List.copyOf(out);
    }

    private void loadFromBindings() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = Objects.toString(bindingsPattern, "").trim();
        if (pattern.isBlank()) {
            pattern = "classpath*:skills/paperpuppy-internal/skill.binding.json";
        }
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                if (resource == null || !resource.exists()) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    JsonNode root = om.readTree(in);
                    parseAndRegister(root, safeResourceName(resource));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load skill bindings from manifests: " + pattern, e);
        }
    }

    private void parseAndRegister(JsonNode root, String source) {
        SkillBindingManifest manifest = parseManifest(root, source);
        if (manifest.bindings() == null || manifest.bindings().isEmpty()) {
            throw new IllegalArgumentException("invalid skill.binding.json (missing bindings[]): " + source);
        }
        for (SkillFunctionBinding rawBinding : manifest.bindings()) {
            SkillFunctionBinding binding = validator.validateFunctionBinding(rawBinding, source);
            SkillFunctionBinding prev = bindingsByFunction.putIfAbsent(binding.functionName(), binding);
            if (prev != null) {
                throw new IllegalStateException("duplicate skill binding for function: " + binding.functionName()
                        + " source=" + source);
            }
        }
    }

    private SkillBindingManifest parseManifest(JsonNode root, String source) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("invalid skill.binding.json (not object): " + source);
        }
        String skillId = text(root, "id");
        String version = text(root, "version");
        if (skillId.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("invalid skill.binding.json (missing id/version): " + source);
        }
        JsonNode bindingsNode = root.get("bindings");
        if (bindingsNode == null || !bindingsNode.isArray()) {
            throw new IllegalArgumentException("invalid skill.binding.json (missing bindings[]): " + source);
        }
        List<SkillFunctionBinding> bindings = new ArrayList<>();
        for (JsonNode bindingNode : bindingsNode) {
            if (bindingNode == null || !bindingNode.isObject()) {
                continue;
            }
            SkillRuntimeType runtimeType = SkillRuntimeType.fromText(text(bindingNode, "runtime"));
            bindings.add(new SkillFunctionBinding(
                    text(bindingNode, "function_name"),
                    runtimeType,
                    text(bindingNode, "tool_name"),
                    text(bindingNode, "operation"),
                    text(bindingNode, "action")));
        }
        return new SkillBindingManifest(skillId, version, List.copyOf(bindings));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static String safeResourceName(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (Exception ignore) {
            return Objects.toString(resource, "unknown");
        }
    }
}
