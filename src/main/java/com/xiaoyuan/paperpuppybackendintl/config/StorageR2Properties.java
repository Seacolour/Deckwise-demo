package com.xiaoyuan.paperpuppybackendintl.config;

import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudflare R2（S3 兼容）配置
 */
@Data
@ConfigurationProperties(prefix = "storage.r2")
public class StorageR2Properties {

    private static final String DEFAULT_REGION = "auto";
    private static final String DEFAULT_PRIVATE_BUCKET = "paperpuppy-private";

    /**
     * Legacy public-channel config. Kept so existing local/cloud mappings continue to start
     * while the typed channel config is rolled out.
     *
     * 例如：https://{accountId}.r2.cloudflarestorage.com
     */
    private String endpoint;
    private String pathPrefix;
    /**
     * R2 通常使用 "auto"
     */
    private String region = "auto";
    private String bucket;
    private String accessKey;
    private String secretKey;
    /**
     * 可选：公开访问域名（如 Cloudflare 自定义域 / CDN 域名），用于拼接 public url
     * 例如：https://assets.example.com
     */
    private String publicDomain;

    private Node publicNode;
    private Node privateNode;

    @Data
    public static class Node {
        private String endpoint;
        private String pathPrefix;
        private String region = DEFAULT_REGION;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String publicDomain;
    }

    /**
     * Bind storage.r2.public.* without using a Java field named "public".
     */
    public Node getPublic() {
        return publicNode;
    }

    public void setPublic(Node publicNode) {
        this.publicNode = publicNode;
    }

    /**
     * Bind storage.r2.private.* without using a Java field named "private".
     */
    public Node getPrivate() {
        return privateNode;
    }

    public void setPrivate(Node privateNode) {
        this.privateNode = privateNode;
    }

    /**
     * 当前默认生效节点：public R2 配置。
     */
    public Node effectiveNode() {
        return effectiveNode(StorageChannel.PUBLIC);
    }

    public Node effectiveNode(StorageChannel channel) {
        StorageChannel normalized = channel == null ? StorageChannel.PUBLIC : channel;
        if (normalized == StorageChannel.PRIVATE) {
            return effectivePrivateNode();
        }
        return effectivePublicNode();
    }

    private Node effectivePublicNode() {
        Node legacy = legacyNode();
        if (!hasAny(publicNode)) {
            return legacy;
        }
        return merge(publicNode, legacy, null, true);
    }

    private Node effectivePrivateNode() {
        Node publicFallback = effectivePublicNode();
        if (!hasAny(privateNode) && publicFallback == null) {
            return null;
        }
        Node node = merge(privateNode, privateFallbackFromPublic(publicFallback), DEFAULT_PRIVATE_BUCKET, false);
        node.setPublicDomain(null);
        return node;
    }

    private Node privateFallbackFromPublic(Node publicFallback) {
        if (publicFallback == null) {
            return null;
        }
        Node fallback = new Node();
        fallback.setEndpoint(publicFallback.getEndpoint());
        fallback.setPathPrefix(publicFallback.getPathPrefix());
        fallback.setRegion(publicFallback.getRegion());
        fallback.setAccessKey(publicFallback.getAccessKey());
        fallback.setSecretKey(publicFallback.getSecretKey());
        return fallback;
    }

    private Node legacyNode() {
        if (isBlank(endpoint) && isBlank(bucket) && isBlank(accessKey) && isBlank(secretKey)
                && isBlank(pathPrefix) && isBlank(publicDomain)) {
            return null;
        }
        Node node = new Node();
        node.setEndpoint(endpoint);
        node.setPathPrefix(pathPrefix);
        node.setRegion(isBlank(region) ? DEFAULT_REGION : region);
        node.setBucket(bucket);
        node.setAccessKey(accessKey);
        node.setSecretKey(secretKey);
        node.setPublicDomain(publicDomain);
        return node;
    }

    private Node merge(Node preferred, Node fallback, String defaultBucket, boolean includePublicDomain) {
        Node node = new Node();
        node.setEndpoint(firstNonBlank(
                preferred == null ? null : preferred.getEndpoint(),
                fallback == null ? null : fallback.getEndpoint()));
        node.setPathPrefix(firstNonBlank(
                preferred == null ? null : preferred.getPathPrefix(),
                fallback == null ? null : fallback.getPathPrefix()));
        node.setRegion(firstNonBlank(
                preferred == null ? null : preferred.getRegion(),
                fallback == null ? null : fallback.getRegion(),
                DEFAULT_REGION));
        node.setBucket(firstNonBlank(
                preferred == null ? null : preferred.getBucket(),
                fallback == null ? null : fallback.getBucket(),
                defaultBucket));
        node.setAccessKey(firstNonBlank(
                preferred == null ? null : preferred.getAccessKey(),
                fallback == null ? null : fallback.getAccessKey()));
        node.setSecretKey(firstNonBlank(
                preferred == null ? null : preferred.getSecretKey(),
                fallback == null ? null : fallback.getSecretKey()));
        if (includePublicDomain) {
            node.setPublicDomain(firstNonBlank(
                    preferred == null ? null : preferred.getPublicDomain(),
                    fallback == null ? null : fallback.getPublicDomain()));
        }
        return node;
    }

    public String effectiveBucket() {
        return effectiveBucket(StorageChannel.PUBLIC);
    }

    public String effectiveBucket(StorageChannel channel) {
        Node n = effectiveNode(channel);
        return n == null ? null : trimToNull(n.getBucket());
    }

    public String effectivePathPrefix() {
        return effectivePathPrefix(StorageChannel.PUBLIC);
    }

    public String effectivePathPrefix(StorageChannel channel) {
        Node n = effectiveNode(channel);
        return n == null ? null : trimToNull(n.getPathPrefix());
    }

    public String effectivePublicDomain() {
        return effectivePublicDomain(StorageChannel.PUBLIC);
    }

    public String effectivePublicDomain(StorageChannel channel) {
        Node n = effectiveNode(channel);
        return n == null ? null : trimToNull(n.getPublicDomain());
    }

    private static boolean hasAny(Node node) {
        return node != null
                && (!isBlank(node.getEndpoint())
                || !isBlank(node.getPathPrefix())
                || !isBlank(node.getRegion())
                || !isBlank(node.getBucket())
                || !isBlank(node.getAccessKey())
                || !isBlank(node.getSecretKey())
                || !isBlank(node.getPublicDomain()));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}


