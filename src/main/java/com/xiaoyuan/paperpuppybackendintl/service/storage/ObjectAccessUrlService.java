package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.config.ObjectDeliveryWorkerProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObjectAccessUrlService {

    private static final String SCOPE_PRIVATE = "private";
    private static final List<String> DEFAULT_PRIVATE_USER_SCOPES = List.of(
            "uploads",
            "generated",
            "pdf-assets",
            "docx-assets",
            "pptx-assets"
    );

    private final ObjectDeliveryWorkerProperties workerProperties;
    private final ObjectResourceTokenSigner tokenSigner;
    private final R2Client r2Client;
    private final StorageR2Properties r2Properties;

    public ObjectAccessUrlService(ObjectDeliveryWorkerProperties workerProperties,
                                  ObjectResourceTokenSigner tokenSigner,
                                  R2Client r2Client,
                                  StorageR2Properties r2Properties) {
        this.workerProperties = workerProperties;
        this.tokenSigner = tokenSigner;
        this.r2Client = r2Client;
        this.r2Properties = r2Properties;
    }

    public String publicUrl(ObjectStorageFile file) {
        if (file == null || StorageChannel.isPrivate(file.getStorageChannel())) {
            return null;
        }
        String fromRecord = buildStoredPublicUrl(file.getPublicDomain(), file.getPublicPath());
        if (fromRecord != null) {
            return fromRecord;
        }
        if (isCurrentPublicBucket(file.getBucket()) && !isBlank(file.getObjectKey())) {
            return r2Client.buildPublicUrl(file.getObjectKey());
        }
        return null;
    }

    public String privateFileUrl(ObjectStorageFile file) {
        return privateFileUrl(file, null);
    }

    public String privateFileUrl(ObjectStorageFile file, Duration ttl) {
        if (file == null || isBlank(file.getObjectKey())) {
            return null;
        }
        if (!StorageChannel.isPrivate(file.getStorageChannel())) {
            return publicUrl(file);
        }
        if (!isWorkerEnabled()) {
            return r2Client.generatePresignedGetUrl(StorageChannel.PRIVATE, file.getObjectKey(),
                    ttlOrDefault(ttl, workerProperties.getDefaultFileTtlSeconds()));
        }
        return resourceTokenUrl(
                workerProperties.getFileDomain(),
                file.getObjectKey(),
                SCOPE_PRIVATE,
                subjectForFile(file),
                cleanParams(Map.of()),
                ttlOrDefault(ttl, workerProperties.getDefaultFileTtlSeconds())
        );
    }

    public String privateImageUrl(ObjectStorageFile file, Map<String, String> transformParams) {
        return privateImageUrl(file, transformParams, null);
    }

    public String privateImageUrl(ObjectStorageFile file, Map<String, String> transformParams, Duration ttl) {
        if (file == null || isBlank(file.getObjectKey())) {
            return null;
        }
        if (!StorageChannel.isPrivate(file.getStorageChannel())) {
            return publicUrl(file);
        }
        if (!isWorkerEnabled()) {
            return r2Client.generatePresignedGetUrl(StorageChannel.PRIVATE, file.getObjectKey(),
                    ttlOrDefault(ttl, workerProperties.getDefaultImageTtlSeconds()));
        }
        return resourceTokenUrl(
                workerProperties.getImageDomain(),
                file.getObjectKey(),
                SCOPE_PRIVATE,
                subjectForFile(file),
                cleanParams(transformParams),
                ttlOrDefault(ttl, workerProperties.getDefaultImageTtlSeconds())
        );
    }

    public ResourceTokenGrant privateUserResourceToken(Long userId, Duration ttl) {
        if (userId == null || userId <= 0 || !isWorkerEnabled() || tokenSigner == null) {
            return null;
        }
        Duration effectiveTtl = ttlOrDefault(ttl, workerProperties.getDefaultFileTtlSeconds());
        List<String> prefixes = defaultPrivateUserPrefixes(userId);
        String token = tokenSigner.sign(
                SCOPE_PRIVATE,
                String.valueOf(userId),
                prefixes,
                List.of(),
                effectiveTtl
        );
        ObjectResourceTokenSigner.Payload payload = tokenSigner.verify(token);
        long expiresAt = payload == null ? 0 : payload.expiresAt();
        return new ResourceTokenGrant(
                requireDomain(workerProperties.getFileDomain()),
                requireDomain(workerProperties.getImageDomain()),
                token,
                SCOPE_PRIVATE,
                prefixes,
                expiresAt,
                effectiveTtl.toSeconds()
        );
    }

    private String resourceTokenUrl(String baseDomain,
                                    String objectKey,
                                    String scope,
                                    String subject,
                                    Map<String, String> extraParams,
                                    Duration ttl) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        if (normalizedObjectKey == null || tokenSigner == null) {
            return null;
        }
        String accessPrefix = accessPrefixForObjectKey(normalizedObjectKey);
        List<String> prefixes = accessPrefix == null ? List.of() : List.of(accessPrefix);
        List<String> objects = accessPrefix == null ? List.of(normalizedObjectKey) : List.of();
        String token = tokenSigner.sign(scope, subject, prefixes, objects, ttl);

        Map<String, String> params = new LinkedHashMap<>(cleanParams(extraParams));
        params.put("rt", token);
        return requireDomain(baseDomain) + tokenSigner.objectKeyToPath(normalizedObjectKey) + "?" + query(params);
    }

    private String accessPrefixForObjectKey(String objectKey) {
        String key = normalizeObjectKey(objectKey);
        if (key == null) {
            return null;
        }
        String[] segments = key.split("/");
        if (segments.length < 3 || !isNumericSegment(segments[2])) {
            return null;
        }
        return segments[0] + "/" + segments[1] + "/" + segments[2] + "/";
    }

    private List<String> defaultPrivateUserPrefixes(Long userId) {
        String root = trimSlashes(r2Properties == null ? null : r2Properties.effectivePathPrefix(StorageChannel.PRIVATE));
        List<String> prefixes = new ArrayList<>(DEFAULT_PRIVATE_USER_SCOPES.size());
        for (String scope : DEFAULT_PRIVATE_USER_SCOPES) {
            StringBuilder prefix = new StringBuilder();
            if (root != null) {
                prefix.append(root).append('/');
            }
            prefix.append(scope).append('/').append(userId).append('/');
            prefixes.add(prefix.toString());
        }
        return List.copyOf(prefixes);
    }

    private String subjectForFile(ObjectStorageFile file) {
        if (file == null) {
            return null;
        }
        if (file.getOwnerUserId() != null && file.getOwnerUserId() > 0) {
            return String.valueOf(file.getOwnerUserId());
        }
        String prefix = accessPrefixForObjectKey(file.getObjectKey());
        if (prefix == null) {
            return null;
        }
        String[] segments = prefix.split("/");
        return segments.length >= 3 ? segments[2] : null;
    }

    private boolean isNumericSegment(String value) {
        String segment = trimToNull(value);
        return segment != null && segment.chars().allMatch(Character::isDigit);
    }

    private Map<String, String> cleanParams(Map<String, String> params) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (params == null || params.isEmpty()) {
            return cleaned;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (key != null && value != null) {
                cleaned.put(key, value);
            }
        }
        return cleaned;
    }

    private Duration ttlOrDefault(Duration ttl, long defaultSeconds) {
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            return ttl;
        }
        return Duration.ofSeconds(Math.max(1, defaultSeconds));
    }

    private String requireDomain(String baseDomain) {
        String domain = trimToNull(baseDomain);
        if (domain == null) {
            throw new IllegalArgumentException("Worker domain 不能为空");
        }
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (!domain.startsWith("https://") && !domain.startsWith("http://")) {
            throw new IllegalArgumentException("Worker domain 必须包含 http/https scheme");
        }
        return domain;
    }

    private String normalizeObjectKey(String objectKey) {
        String key = trimToNull(objectKey);
        if (key == null) {
            return null;
        }
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        return key.isBlank() ? null : key;
    }

    private String trimSlashes(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    private String query(Map<String, String> params) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = trimToNull(entry.getKey());
            String value = trimToNull(entry.getValue());
            if (key != null && value != null) {
                pairs.add(urlEncode(key) + "=" + urlEncode(value));
            }
        }
        return String.join("&", pairs);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    private boolean isWorkerEnabled() {
        return workerProperties != null && workerProperties.isEnabled();
    }

    private boolean isCurrentPublicBucket(String bucket) {
        String configuredBucket = trimToNull(r2Properties == null ? null : r2Properties.effectiveBucket());
        String objectBucket = trimToNull(bucket);
        return objectBucket == null || (configuredBucket != null && configuredBucket.equals(objectBucket));
    }

    private String buildStoredPublicUrl(String publicDomain, String publicPath) {
        String domain = trimToNull(publicDomain);
        String path = trimToNull(publicPath);
        if (domain == null || path == null) {
            return null;
        }
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return domain + "/" + path;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ResourceTokenGrant(
            String fileBaseUrl,
            String imageBaseUrl,
            String resourceToken,
            String scope,
            List<String> prefixes,
            long expiresAt,
            long ttlSeconds
    ) {
    }
}
