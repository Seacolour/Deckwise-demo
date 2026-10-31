package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.ObjectDeliveryWorkerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ObjectResourceTokenSigner {

    private static final String TOKEN_VERSION = "v1";
    private static final String TOKEN_TYPE = "PP-RT";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Set<String> ALLOWED_SCOPES = Set.of("private");
    private static final ObjectMapper TOKEN_MAPPER = new ObjectMapper();

    private final ObjectDeliveryWorkerProperties properties;
    private final Clock clock;

    @Autowired
    public ObjectResourceTokenSigner(ObjectDeliveryWorkerProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ObjectResourceTokenSigner(ObjectDeliveryWorkerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String sign(String scope,
                       String subject,
                       List<String> prefixes,
                       List<String> objectKeys,
                       Duration ttl) {
        String normalizedScope = requireScope(scope);
        List<String> normalizedPrefixes = normalizePrefixes(prefixes);
        List<String> normalizedObjects = normalizeObjectKeys(objectKeys);
        if (normalizedPrefixes.isEmpty() && normalizedObjects.isEmpty()) {
            throw new IllegalArgumentException("资源访问 token 必须包含 prefix 或 objectKey");
        }

        Duration safeTtl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(5) : ttl;
        Instant now = Instant.now(clock);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", TOKEN_TYPE);
        header.put("kid", requireKeyId());
        header.put("v", TOKEN_VERSION);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("v", TOKEN_VERSION);
        payload.put("scope", normalizedScope);
        payload.put("sub", trimToNull(subject));
        payload.put("prefixes", normalizedPrefixes);
        payload.put("objects", normalizedObjects);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(safeTtl).getEpochSecond());
        payload.put("kid", requireKeyId());

        String signingInput = base64Url(toJsonBytes(header)) + "." + base64Url(toJsonBytes(payload));
        return signingInput + "." + hmacSha256(signingInput);
    }

    public Payload verify(String token) {
        String value = trimToNull(token);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        String signingInput = parts[0] + "." + parts[1];
        String expected = hmacSha256(signingInput);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            Map<String, Object> header = readTokenMap(parts[0]);
            Map<String, Object> payload = readTokenMap(parts[1]);
            if (!"HS256".equals(stringValue(header.get("alg")))
                    || !TOKEN_TYPE.equals(stringValue(header.get("typ")))
                    || !TOKEN_VERSION.equals(stringValue(header.get("v")))
                    || !requireKeyId().equals(stringValue(header.get("kid")))) {
                return null;
            }
            long exp = longValue(payload.get("exp"));
            if (exp <= 0 || exp < Instant.now(clock).getEpochSecond()) {
                return null;
            }
            String scope = requireScope(stringValue(payload.get("scope")));
            String kid = stringValue(payload.get("kid"));
            if (kid != null && !requireKeyId().equals(kid)) {
                return null;
            }
            return new Payload(
                    stringValue(payload.get("sub")),
                    scope,
                    stringList(payload.get("prefixes")),
                    stringList(payload.get("objects")),
                    longValue(payload.get("iat")),
                    exp,
                    requireKeyId()
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public String objectKeyToPath(String objectKey) {
        String key = normalizeObjectKey(objectKey);
        if (key == null) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        String[] parts = key.split("/", -1);
        StringBuilder path = new StringBuilder("/");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                path.append('/');
            }
            path.append(urlEncode(parts[i]));
        }
        return path.toString();
    }

    private byte[] toJsonBytes(Map<String, Object> value) {
        try {
            return TOKEN_MAPPER.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("资源访问 token 序列化失败", e);
        }
    }

    private Map<String, Object> readTokenMap(String base64Url) throws Exception {
        byte[] json = Base64.getUrlDecoder().decode(base64Url);
        return TOKEN_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
        });
    }

    private String hmacSha256(String signingInput) {
        String secret = trimToNull(properties == null ? null : properties.getResourceTokenSecret());
        if (secret == null) {
            throw new IllegalStateException("storage.object-delivery.worker.resource-token-secret 未配置");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("资源访问 token 签名失败", e);
        }
    }

    private List<String> normalizePrefixes(List<String> prefixes) {
        List<String> normalized = new ArrayList<>();
        if (prefixes == null) {
            return normalized;
        }
        for (String prefix : prefixes) {
            String raw = trimToNull(prefix);
            while (raw != null && raw.endsWith("/")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            String value = normalizeObjectKey(raw);
            if (value == null) {
                continue;
            }
            if (!value.endsWith("/")) {
                value = value + "/";
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeObjectKeys(List<String> objectKeys) {
        List<String> normalized = new ArrayList<>();
        if (objectKeys == null) {
            return normalized;
        }
        for (String objectKey : objectKeys) {
            String value = normalizeObjectKey(objectKey);
            if (value != null) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private String normalizeObjectKey(String value) {
        String key = trimToNull(value);
        if (key == null) {
            return null;
        }
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.isBlank() || key.contains("\\") || key.contains("//")) {
            throw new IllegalArgumentException("objectKey 不合法");
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("objectKey 不合法");
            }
        }
        return key;
    }

    private String requireKeyId() {
        String keyId = trimToNull(properties == null ? null : properties.getResourceTokenKeyId());
        if (keyId == null) {
            throw new IllegalStateException("storage.object-delivery.worker.resource-token-key-id 未配置");
        }
        return keyId;
    }

    private String requireScope(String scope) {
        String normalizedScope = trimToNull(scope);
        normalizedScope = normalizedScope == null ? null : normalizedScope.toLowerCase(Locale.ROOT);
        if (normalizedScope == null || !ALLOWED_SCOPES.contains(normalizedScope)) {
            throw new IllegalArgumentException("不支持的访问 scope: " + scope);
        }
        return normalizedScope;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(ObjectResourceTokenSigner::stringValue)
                .map(ObjectResourceTokenSigner::trimToNull)
                .filter(item -> item != null)
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record Payload(
            String subject,
            String scope,
            List<String> prefixes,
            List<String> objects,
            long issuedAt,
            long expiresAt,
            String keyId
    ) {
    }
}
