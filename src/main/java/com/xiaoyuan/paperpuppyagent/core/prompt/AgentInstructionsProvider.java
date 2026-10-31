package com.xiaoyuan.paperpuppyagent.core.prompt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加载 Agent loop 的 system prompt（instructions），支持多语言。
 *
 * <p>
 * 优先按 locale 加载对应语言的提示词文件：
 * <ul>
 * <li>尝试加载 {@code agent_loop_instructions_{locale}.md}（如 zh-CN）</li>
 * <li>回退到语言代码（如 zh）</li>
 * <li>最终回退到默认语言（en）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class AgentInstructionsProvider {

    private static final String DEFAULT_LOCALE = "en";

    private final ResourceLoader resourceLoader;

    /**
     * 提示词资源目录（支持 classpath: 或 file:）
     */
    @Value("${agent.loop.instructions-dir:classpath:prompts/}")
    private String instructionsDir;

    /**
     * 提示词文件前缀
     */
    @Value("${agent.loop.instructions-prefix:agent_loop_instructions_}")
    private String instructionsPrefix;

    /**
     * 提示词文件后缀
     */
    @Value("${agent.loop.instructions-suffix:.md}")
    private String instructionsSuffix;

    /**
     * 可选：兜底提示词（用于快速覆盖/调试）
     */
    @Value("${agent.loop.instructions:}")
    private String inlineInstructions;

    /**
     * 缓存：locale -> instructions
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public AgentInstructionsProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        // 启动时扫描目录，自动 preload 所有存在的 locale 文件
        // 文件名格式：{prefix}{locale}{suffix}，例如 agent_loop_instructions_zh-hant.md
        boolean loadedAny = false;
        try {
            String pattern = instructionsDir + instructionsPrefix + "*" + instructionsSuffix;
            // instructionsDir 可能是 "classpath:prompts/"，PathMatchingResourcePatternResolver 需要 classpath*: 才能扫描
            String scanPattern = pattern.startsWith("classpath:")
                    ? "classpath*:" + pattern.substring("classpath:".length())
                    : pattern;
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(scanPattern);
            int prefixLen = instructionsPrefix.length();
            int suffixLen = instructionsSuffix.length();
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename == null) continue;
                if (!filename.startsWith(instructionsPrefix) || !filename.endsWith(instructionsSuffix)) continue;
                String locale = filename.substring(prefixLen, filename.length() - suffixLen);
                if (!locale.isBlank()) {
                    get(locale);
                    loadedAny = true;
                }
            }
        } catch (Exception e) {
            log.warn("[AgentInstructions] failed to scan locale files, falling back to default preload: {}", e.getMessage());
        }
        if (!loadedAny) {
            // 扫描失败时兜底预加载 en 和 zh
            get(DEFAULT_LOCALE);
            get("zh");
        }
    }

    /**
     * 获取指定 locale 的提示词。
     *
     * @param locale 用户偏好语言（如 "zh-CN", "en-US"），可为空
     * @return 提示词内容
     */
    public String get(String locale) {
        String normalizedLocale = normalizeLocale(locale);
        return cache.computeIfAbsent(normalizedLocale, this::load);
    }

    private String load(String locale) {
        // 1) 尝试完整 locale（如 zh）
        String result = tryLoadResource(instructionsDir, instructionsPrefix, instructionsSuffix, locale);
        if (result != null) {
            return result;
        }

        // 2) 回退到语言代码（如 zh）
        String lang = extractLanguage(locale);
        if (!lang.equals(locale)) {
            result = tryLoadResource(instructionsDir, instructionsPrefix, instructionsSuffix, lang);
            if (result != null) {
                return result;
            }
        }

        // 3) 回退到默认语言
        if (!DEFAULT_LOCALE.equals(locale) && !DEFAULT_LOCALE.equals(lang)) {
            result = tryLoadResource(instructionsDir, instructionsPrefix, instructionsSuffix, DEFAULT_LOCALE);
            if (result != null) {
                return result;
            }
        }

        // 4) 尝试 inline 配置
        String inline = normalize(inlineInstructions);
        if (!inline.isBlank()) {
            log.info("[AgentInstructions] loaded from inline config: locale={}, chars={}, sha256={}",
                    locale, inline.length(), sha256Hex(inline));
            return inline;
        }

        // 5) 最终兜底
        String fallback = "You are a helpful assistant. Use tools when needed.";
        log.warn("[AgentInstructions] using fallback default instructions: locale={}", locale);
        return fallback;
    }

    private String tryLoadResource(String dir, String prefix, String suffix, String localeKey) {
        String location = dir + prefix + localeKey + suffix;
        try {
            Resource r = resourceLoader.getResource(location);
            if (r.exists()) {
                String text = readUtf8(r);
                String normalized = normalize(text);
                if (!normalized.isBlank()) {
                    log.info("[AgentInstructions] loaded: locale={}, location={}, chars={}, sha256={}",
                            localeKey, location, normalized.length(), sha256Hex(normalized));
                    return normalized;
                }
            }
        } catch (Exception e) {
            log.debug("[AgentInstructions] resource not found: locale={}, location={}", localeKey, location);
        }
        return null;
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }

        // 1) 去掉 ICU 扩展 (@rg=xxzzzz)
        int atIdx = locale.indexOf('@');
        String base = atIdx > 0 ? locale.substring(0, atIdx) : locale;
        base = base.trim().replace('_', '-').toLowerCase(Locale.ROOT);

        // 兼容历史请求中把中文传成国家代码 cn 的情况，实际提示词仍使用标准 zh 文件。
        if ("cn".equals(base) || base.startsWith("cn-")) {
            return "zh";
        }

        // 2) 提取语言代码（前 2 位）
        String lang = base.length() >= 2 ? base.substring(0, 2) : base;

        // 3) 中文特殊处理：识别繁体
        if ("zh".equals(lang)) {
            if (base.contains("hant")) {
                return "zh-hant"; // 繁体（未来可加 zh-hant.md）
            }
            return "zh"; // 简体（默认）
        }

        // 4) 其他语言只保留语言代码
        return lang;
    }

    private static String extractLanguage(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        int idx = locale.indexOf('-');
        return idx > 0 ? locale.substring(0, idx) : locale;
    }

    private static String readUtf8(Resource r) throws Exception {
        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
