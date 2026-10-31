package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;

/**
 * 轻量 OpenAIClient 工厂（复用连接池、支持按 apiKey/baseUrl/org/project 维度缓存）。
 */
final class OpenAiClients {

    private OpenAiClients() {
    }

    /**
     * 有界缓存：避免“动态 apiKey/baseUrl”导致静态 Map 无限增长（尤其在多租户/转发场景）。
     *
     * <p>说明：OpenAIClient 内部通常持有连接池等资源，复用是有价值的；但必须有上限与过期策略。</p>
     */
    private static final Cache<Key, OpenAIClient> CACHE = Caffeine.newBuilder()
            .maximumSize(longProp("paperpuppy.openai.clientCache.maxSize", 256))
            .expireAfterAccess(Duration.ofMinutes(longProp("paperpuppy.openai.clientCache.expireAfterAccessMinutes", 30)))
            .build();

    static OpenAIClient get(AiParam p) {
        if (p == null) throw new IllegalArgumentException("param is null");
        String apiKey = Objects.toString(p.apiKey(), "").trim();
        if (apiKey.isBlank()) throw new IllegalArgumentException("apiKey is blank");
        long timeoutMs = p.timeoutMs();
        int maxRetries = p.clientMaxRetries();
        Key key = new Key(apiKey, normalizeBaseUrl(p.baseUrl()), trimToNull(p.orgId()), trimToNull(p.projectId()), timeoutMs, maxRetries);
        return CACHE.get(key, OpenAiClients::buildClient);
    }

    private static OpenAIClient buildClient(Key k) {
        var b = OpenAIOkHttpClient.builder().apiKey(k.apiKey);
        if (k.baseUrl != null) {
            b.baseUrl(k.baseUrl);
        }
        if (k.orgId != null) {
            b.organization(k.orgId);
        }
        if (k.projectId != null) {
            b.project(k.projectId);
        }
        if (k.timeoutMs > 0) {
            b.timeout(Duration.ofMillis(k.timeoutMs));
        }
        if (k.maxRetries >= 0) {
            b.maxRetries(k.maxRetries);
        }
        return b.build();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    /**
     * openai-java SDK 需要 baseUrl 指向 API 根路径（含 /v1）。
     */
    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return null;
        String s = baseUrl.trim();
        if (s.isEmpty()) return null;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("/v1")) return s;
        return s + "/v1";
    }

    /**
     * 注意：不要用 record 默认 toString()（会把 apiKey 直接打印出来）。
     */
    private static final class Key {
        private final String apiKey;
        private final String baseUrl;
        private final String orgId;
        private final String projectId;
        private final long timeoutMs;
        private final int maxRetries;

        private Key(String apiKey, String baseUrl, String orgId, String projectId, long timeoutMs, int maxRetries) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.orgId = orgId;
            this.projectId = projectId;
            this.timeoutMs = timeoutMs;
            this.maxRetries = maxRetries;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(apiKey, key.apiKey)
                    && Objects.equals(baseUrl, key.baseUrl)
                    && Objects.equals(orgId, key.orgId)
                    && Objects.equals(projectId, key.projectId)
                    && timeoutMs == key.timeoutMs
                    && maxRetries == key.maxRetries;
        }

        @Override
        public int hashCode() {
            return Objects.hash(apiKey, baseUrl, orgId, projectId, timeoutMs, maxRetries);
        }

        @Override
        public String toString() {
            return "Key{" +
                    "apiKey=" + mask(apiKey) +
                    ", baseUrl='" + baseUrl + '\'' +
                    ", orgId='" + orgId + '\'' +
                    ", projectId='" + projectId + '\'' +
                    ", timeoutMs=" + timeoutMs +
                    ", maxRetries=" + maxRetries +
                    '}';
        }
    }

    private static long longProp(String key, long def) {
        try {
            String v = System.getProperty(key);
            if (v == null || v.isBlank()) return def;
            long x = Long.parseLong(v.trim());
            return x > 0 ? x : def;
        } catch (Exception ignore) {
            return def;
        }
    }

    private static String mask(String s) {
        if (s == null) return "null";
        String t = s.trim();
        if (t.isEmpty()) return "\"\"";
        if (t.length() <= 8) return "***";
        return t.substring(0, 4) + "****" + t.substring(t.length() - 4);
    }
}


