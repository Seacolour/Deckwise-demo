package com.xiaoyuan.paperpuppyagent.core.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openai.core.JsonValue;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.WebSearchTool;
import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * SkillCatalog：从 classpath 加载内置 skill manifests，并导出给模型的 function tools 列表。
 *
 * <p>支持从 {@code skills.manifests} 指定的资源/通配符加载：
 * 默认 {@code classpath*:skills/paperpuppy-internal/skill.json}；
 * 生产环境可配置为 {@code file:/.../skills/&#42;&#42;/skill.json} 并通过容器挂载覆盖。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillCatalog {

    private final ObjectMapper om;

    @Value("${skills.manifests:classpath*:skills/paperpuppy-internal/skill.json}")
    private String manifests;

    @Value("${skills.web-search.enabled:true}")
    private boolean webSearchEnabled;

    @Value("${skills.web-search.search-context-size:medium}")
    private String webSearchContextSize;

    /**
     * 全局禁用工具列表（逗号分隔的 skill name）。
     * 优先级高于用户级设置，匹配的工具对所有用户不可见、不可调用。
     * 可通过环境变量 SKILLS_GLOBALLY_DISABLED 在运行时控制，无需重新打包。
 * 示例：document_diff,document_save
     */
    @Value("${skills.globally-disabled:}")
    private String globallyDisabledRaw;

    private final Map<String, SkillFunction> functionsByName = new ConcurrentHashMap<>();
    private ToolCompositionRegistry toolCompositionRegistry;

    @Autowired(required = false)
    public void setToolCompositionRegistry(ToolCompositionRegistry toolCompositionRegistry) {
        this.toolCompositionRegistry = toolCompositionRegistry;
    }

    @PostConstruct
    public void init() {
        loadFromManifests();
        log.info("[SkillCatalog] loaded functions: count={}, names={}", functionsByName.size(), functionsByName.keySet());
    }

    public SkillFunction getFunction(String name) {
        if (name == null) {
            return null;
        }
        return functionsByName.get(name);
    }

    public List<SkillFunction> listFunctions() {
        ArrayList<SkillFunction> out = new ArrayList<>(functionsByName.values());
        out.sort(Comparator.comparing(SkillFunction::name));
        return List.copyOf(out);
    }

    /**
     * 生成 openai-java SDK 的 Tools（FunctionTool），不做过滤。
     */
    public List<com.openai.models.responses.Tool> toOpenAiSdkTools() {
        return toOpenAiSdkTools(Set.of());
    }

    /**
     * 生成 openai-java SDK 的 Tools（FunctionTool），过滤掉 disabledSkillNames 中的技能。
     */
    public List<com.openai.models.responses.Tool> toOpenAiSdkTools(Set<String> disabledSkillNames) {
        return toOpenAiSdkTools(disabledSkillNames, f -> true);
    }

    /**
     * 生成 openai-java SDK 的 Tools（FunctionTool），并允许调用方按 binding/capability 再做过滤。
     */
    public List<com.openai.models.responses.Tool> toOpenAiSdkTools(Set<String> disabledSkillNames,
                                                                   Predicate<SkillFunction> visibilityFilter) {
        Set<String> globallyDisabled = parseGloballyDisabled();
        Set<String> userDisabled = disabledSkillNames == null ? Set.of() : disabledSkillNames;
        Predicate<SkillFunction> filter = visibilityFilter == null ? f -> true : visibilityFilter;
        ArrayList<com.openai.models.responses.Tool> out = new ArrayList<>();
        for (SkillFunction f : listFunctions()) {
            if (globallyDisabled.contains(f.name())) {
                log.debug("[SkillCatalog] 已过滤全局禁用技能: {}", f.name());
                continue;
            }
            if (userDisabled.contains(f.name())) {
                log.debug("[SkillCatalog] 已过滤用户禁用技能: {}", f.name());
                continue;
            }
            if (!filter.test(f)) {
                log.debug("[SkillCatalog] 已按 binding/capability 过滤技能: {}", f.name());
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> schemaMap = (Map<String, Object>) om.convertValue(
                    modelVisibleInputSchema(f),
                    Map.class
            );
            FunctionTool.Parameters.Builder pb = FunctionTool.Parameters.builder();
            if (schemaMap != null) {
                for (Map.Entry<String, Object> e : schemaMap.entrySet()) {
                    pb.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
                }
            }
            FunctionTool ft = FunctionTool.builder()
                    .name(f.name())
                    .description(Objects.toString(f.description(), ""))
                    .parameters(pb.build())
                    .strict(f.strict())
                    .build();
            out.add(com.openai.models.responses.Tool.ofFunction(ft));
        }
        // OpenAI 内置 web search 工具（服务端执行，无需本地 SkillExecutor）
        if (webSearchEnabled) {
            WebSearchTool.Builder wsb = WebSearchTool.builder()
                    .type(WebSearchTool.Type.WEB_SEARCH);
            String ctxSize = Objects.toString(webSearchContextSize, "medium").trim().toLowerCase();
            switch (ctxSize) {
                case "low" -> wsb.searchContextSize(WebSearchTool.SearchContextSize.LOW);
                case "high" -> wsb.searchContextSize(WebSearchTool.SearchContextSize.HIGH);
                default -> wsb.searchContextSize(WebSearchTool.SearchContextSize.MEDIUM);
            }
            out.add(com.openai.models.responses.Tool.ofWebSearch(wsb.build()));
            log.info("[SkillCatalog] web_search tool enabled: contextSize={}", ctxSize);
        }

        return List.copyOf(out);
    }

    private ObjectNode modelVisibleInputSchema(SkillFunction function) {
        ObjectNode schema = function.inputSchema() == null
                ? om.createObjectNode().put("type", "object")
                : function.inputSchema();
        if (toolCompositionRegistry == null) {
            return schema;
        }
        return toolCompositionRegistry.decorateInputSchema(function.name(), schema, function.strict());
    }

    public boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public boolean isGloballyDisabled(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            return false;
        }
        return parseGloballyDisabled().contains(functionName.trim());
    }

    private Set<String> parseGloballyDisabled() {
        String raw = Objects.toString(globallyDisabledRaw, "").trim();
        if (raw.isBlank()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String s : raw.split(",")) {
            String name = s.trim();
            if (!name.isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    private void loadFromManifests() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String pattern = Objects.toString(manifests, "").trim();
        if (pattern.isBlank()) {
            pattern = "classpath*:skills/paperpuppy-internal/skill.json";
        }
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource r : resources) {
                if (r == null || !r.exists()) {
                    continue;
                }
                try (InputStream in = r.getInputStream()) {
                    JsonNode root = om.readTree(in);
                    parseAndRegister(root, safeResourceName(r));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load skills from manifests: " + pattern, e);
        }
    }

    private void parseAndRegister(JsonNode root, String source) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("invalid skill.json (not object): " + source);
        }
        String skillId = text(root, "id");
        String version = text(root, "version");
        if (skillId.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("invalid skill.json (missing id/version): " + source);
        }
        JsonNode funcs = root.get("functions");
        if (funcs == null || !funcs.isArray()) {
            throw new IllegalArgumentException("invalid skill.json (missing functions[]): " + source);
        }
        for (JsonNode fn : funcs) {
            if (fn == null || !fn.isObject()) {
                continue;
            }
            String name = text(fn, "name");
            String desc = text(fn, "description");
            boolean strict = fn.has("strict") ? fn.get("strict").asBoolean(true) : true;
            ObjectNode inputSchema = fn.has("input_schema") && fn.get("input_schema").isObject()
                    ? (ObjectNode) fn.get("input_schema")
                    : om.createObjectNode().put("type", "object");

            SkillScope scope = parseScope(fn.get("scope"));
            SkillSafety safety = parseSafety(fn.get("safety"));

            if (name.isBlank()) {
                throw new IllegalArgumentException("invalid function name (blank): " + source + " skillId=" + skillId);
            }

            // OpenAI strict 校验要求：required 必须包含 properties 的每一个 key
            if (strict) {
                validateStrictSchema(name, inputSchema, source);
            }

            SkillFunction f = new SkillFunction(skillId, version, name, desc, strict, inputSchema, scope, safety);
            SkillFunction prev = functionsByName.putIfAbsent(name, f);
            if (prev != null) {
                throw new IllegalStateException("duplicate function name: " + name + " sources=" +
                        prev.skillId() + "/" + prev.skillVersion() + " and " + skillId + "/" + version);
            }
        }
    }

    private static void validateStrictSchema(String functionName, ObjectNode schema, String source) {
        validateStrictSchemaNode(functionName, schema, source, "$");
    }

    private static void validateStrictSchemaNode(String functionName, JsonNode schema, String source, String path) {
        if (schema == null) {
            throw new IllegalArgumentException("strict schema missing: fn=" + functionName + " source=" + source);
        }
        JsonNode props = schema.get("properties");
        boolean objectSchema = declaresType(schema, "object") || (props != null && props.isObject());
        if (objectSchema) {
            if (!schema.has("additionalProperties")
                    || !schema.get("additionalProperties").isBoolean()
                    || schema.get("additionalProperties").asBoolean(true)) {
                throw new IllegalArgumentException("invalid strict schema: 'additionalProperties' must be false: fn="
                        + functionName + " source=" + source + " path=" + path);
            }
            if (props == null || !props.isObject()) {
                props = null;
            }
            JsonNode req = schema.get("required");
            if (req == null || !req.isArray()) {
                throw new IllegalArgumentException("invalid strict schema: 'required' must be array: fn=" +
                        functionName + " source=" + source + " path=" + path);
            }
            if (props != null) {
                Set<String> required = new HashSet<>();
                for (JsonNode r : req) {
                    if (r != null && r.isTextual()) {
                        required.add(r.asText(""));
                    }
                }
                Iterator<String> it = props.fieldNames();
                while (it.hasNext()) {
                    String k = it.next();
                    if (!required.contains(k)) {
                        throw new IllegalArgumentException("invalid strict schema: missing required '" + k + "': fn=" +
                                functionName + " source=" + source + " path=" + path);
                    }
                }
                Iterator<Map.Entry<String, JsonNode>> fieldIt = props.fields();
                while (fieldIt.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fieldIt.next();
                    validateStrictSchemaNode(functionName, entry.getValue(), source, path + ".properties." + entry.getKey());
                }
            }
        }

        if (declaresType(schema, "array")) {
            JsonNode items = schema.get("items");
            if (items != null && !items.isNull()) {
                validateStrictSchemaNode(functionName, items, source, path + ".items");
            }
        }

        JsonNode anyOf = schema.get("anyOf");
        if (anyOf != null && anyOf.isArray()) {
            for (int i = 0; i < anyOf.size(); i++) {
                validateStrictSchemaNode(functionName, anyOf.get(i), source, path + ".anyOf[" + i + "]");
            }
        }

        JsonNode oneOf = schema.get("oneOf");
        if (oneOf != null && oneOf.isArray()) {
            for (int i = 0; i < oneOf.size(); i++) {
                validateStrictSchemaNode(functionName, oneOf.get(i), source, path + ".oneOf[" + i + "]");
            }
        }
    }

    private static boolean declaresType(JsonNode schema, String typeName) {
        if (schema == null || typeName == null || typeName.isBlank()) {
            return false;
        }
        JsonNode type = schema.get("type");
        if (type == null || type.isNull()) {
            return false;
        }
        if (type.isTextual()) {
            return typeName.equals(type.asText(""));
        }
        if (type.isArray()) {
            for (JsonNode item : type) {
                if (item != null && item.isTextual() && typeName.equals(item.asText(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private SkillScope parseScope(JsonNode n) {
        if (n == null || !n.isObject()) {
            return SkillScope.none();
        }
        boolean requiresUser = n.has("requires_user") && n.get("requires_user").asBoolean(false);
        boolean requiresProject = n.has("requires_project") && n.get("requires_project").asBoolean(false);
        boolean requiresSession = n.has("requires_session") && n.get("requires_session").asBoolean(false);
        return new SkillScope(requiresUser, requiresProject, requiresSession);
    }

    private SkillSafety parseSafety(JsonNode n) {
        if (n == null || !n.isObject()) {
            return SkillSafety.defaultReadOnly();
        }
        boolean writesData = n.has("writes_data") && n.get("writes_data").asBoolean(false);
        boolean idempotent = n.has("idempotent") && n.get("idempotent").asBoolean(true);
        return new SkillSafety(writesData, idempotent);
    }

    private static String text(JsonNode n, String field) {
        if (n == null || field == null) {
            return "";
        }
        JsonNode v = n.get(field);
        return v == null ? "" : v.asText("");
    }

    private static String safeResourceName(Resource r) {
        try {
            return r.getURL().toString();
        } catch (Exception e) {
            return String.valueOf(r);
        }
    }
}

