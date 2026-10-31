package com.xiaoyuan.paperpuppybackendintl.infrastructure.r2;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class R2Client {

    private final S3Client s3Client;
    private final StorageR2Properties props;
    private final S3Presigner presigner;
    private final Map<StorageChannel, S3Client> channelClients = new ConcurrentHashMap<>();
    private final Map<StorageChannel, S3Presigner> channelPresigners = new ConcurrentHashMap<>();

    public R2Client(S3Client s3Client,
                    StorageR2Properties props,
                    S3Presigner presigner) {
        this.s3Client = s3Client;
        this.props = props;
        this.presigner = presigner;
    }

    /**
     * 暴露原始 S3Client（R2 兼容层）。
     * <p>用于少数需要获取底层响应细节的场景（etag/version_id/last_modified 等）。</p>
     */
    public S3Client rawClient() {
        return s3Client;
    }

    public S3Client rawClient(StorageChannel channel) {
        return clientFor(channel);
    }

    /**
     * 直接 putObject 并返回原始响应（包含 eTag/versionId 等）。
     */
    public PutObjectResponse putObject(String key, byte[] bytes, String contentType) {
        return putObject(StorageChannel.PUBLIC, key, bytes, contentType, null);
    }

    public PutObjectResponse putObject(StorageChannel channel, String key, byte[] bytes, String contentType) {
        return putObject(channel, key, bytes, contentType, null);
    }

    /**
     * 直接 putObject 并返回原始响应（包含 eTag/versionId 等）。
     */
    public PutObjectResponse putObject(String key, byte[] bytes, String contentType, String contentDisposition) {
        return putObject(StorageChannel.PUBLIC, key, bytes, contentType, contentDisposition);
    }

    public PutObjectResponse putObject(StorageChannel channel,
                                       String key,
                                       byte[] bytes,
                                       String contentType,
                                       String contentDisposition) {
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件内容为空");
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        String ct = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType.trim();
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(k)
                .contentType(ct);
        if (contentDisposition != null && !contentDisposition.isBlank()) {
            request.contentDisposition(contentDisposition.trim());
        }
        return clientFor(normalizedChannel)
                .putObject(request.build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
    }

    /**
     * headObject：用于读取 last_modified / etag / version_id 等信息。
     */
    public HeadObjectResponse headObject(String key) {
        return headObject(StorageChannel.PUBLIC, key);
    }

    public HeadObjectResponse headObject(StorageChannel channel, String key) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        return clientFor(normalizedChannel).headObject(b -> b.bucket(bucket).key(k));
    }

    /**
     * 上传并返回更完整的元信息（publicUrl + etag/versionId/lastModified）。
     */
    public UploadResult uploadWithMeta(String key, byte[] bytes, String contentType) {
        return uploadWithMeta(StorageChannel.PUBLIC, key, bytes, contentType, null);
    }

    public UploadResult uploadWithMeta(StorageChannel channel, String key, byte[] bytes, String contentType) {
        return uploadWithMeta(channel, key, bytes, contentType, null);
    }

    /**
     * 上传并返回更完整的元信息（publicUrl + etag/versionId/lastModified）。
     */
    public UploadResult uploadWithMeta(String key, byte[] bytes, String contentType, String contentDisposition) {
        return uploadWithMeta(StorageChannel.PUBLIC, key, bytes, contentType, contentDisposition);
    }

    public UploadResult uploadWithMeta(StorageChannel channel,
                                       String key,
                                       byte[] bytes,
                                       String contentType,
                                       String contentDisposition) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String k = normalizeKey(key);
        PutObjectResponse put = putObject(normalizedChannel, k, bytes, contentType, contentDisposition);
        HeadObjectResponse head = null;
        try {
            head = headObject(normalizedChannel, k);
        } catch (Exception ignored) {
            // 有些环境可能限制 headObject；允许降级（lastModified/versionId 为空）
        }
        String etag = put == null ? null : put.eTag();
        String versionId = put == null ? null : put.versionId();
        OffsetDateTime lastModified = null;
        if (head != null && head.lastModified() != null) {
            lastModified = OffsetDateTime.ofInstant(head.lastModified(), ZoneOffset.UTC);
            if (versionId == null || versionId.isBlank()) {
                versionId = head.versionId();
            }
            if (etag == null || etag.isBlank()) {
                etag = head.eTag();
            }
        }
        return new UploadResult(buildPublicUrl(normalizedChannel, k), etag, versionId, lastModified);
    }

    public String upload(String key, byte[] bytes, String contentType) {
        return upload(StorageChannel.PUBLIC, key, bytes, contentType);
    }

    public String upload(StorageChannel channel, String key, byte[] bytes, String contentType) {
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件内容为空");
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        String ct = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType.trim();

        clientFor(normalizedChannel).putObject(
                b -> b.bucket(bucket)
                        .key(k)
                        .contentType(ct),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes)
        );
        return buildPublicUrl(normalizedChannel, k);
    }

    /**
     * 生成 presigned PUT URL，让浏览器直接上传到当前 public R2 节点。
     *
     * <p>objectKey 仍由后端生成；调用方只拿到一次性上传许可，不需要也不能接触 R2 凭证。</p>
     */
    public String generatePresignedPutUrl(String key, Duration ttl, String contentType) {
        return generatePresignedPutUrl(StorageChannel.PUBLIC, key, ttl, contentType);
    }

    public String generatePresignedPutUrl(StorageChannel channel, String key, Duration ttl, String contentType) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        Duration expires = (ttl == null || ttl.isNegative() || ttl.isZero()) ? Duration.ofMinutes(5) : ttl;

        PutObjectRequest.Builder putReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key(k);
        if (contentType != null && !contentType.isBlank()) {
            putReq.contentType(contentType.trim());
        }

        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
                .signatureDuration(expires)
                .putObjectRequest(putReq.build())
                .build();
        URL url = requirePresigner(normalizedChannel).presignPutObject(presignReq).url();
        return url == null ? null : url.toString();
    }

    /** 生成短期 GET URL，供模型读取 private 临时对象。 */
    public String generatePresignedGetUrl(StorageChannel channel, String key, Duration ttl) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        Duration expires = (ttl == null || ttl.isNegative() || ttl.isZero()) ? Duration.ofMinutes(10) : ttl;
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(k)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expires)
                .getObjectRequest(getRequest)
                .build();
        URL url = requirePresigner(normalizedChannel).presignGetObject(presignRequest).url();
        return url == null ? null : url.toString();
    }

    public void delete(String key) {
        delete(StorageChannel.PUBLIC, key);
    }

    public void delete(StorageChannel channel, String key) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        try {
            clientFor(normalizedChannel).deleteObject(b -> b.bucket(bucket).key(k));
        } catch (S3Exception e) {
            // 删除幂等：不存在就当成功
            if (e.statusCode() != 404) {
                throw e;
            }
        }
    }

    /**
     * 下载对象内容（一次性读入内存）。
     * <p>
     * 说明：向量化触发需要把文件 bytes 通过 gRPC 发送到 JRAG（上限 50MB），因此这里采用 bytes API。
     */
    public byte[] downloadBytes(String key) {
        return downloadBytes(StorageChannel.PUBLIC, key);
    }

    public byte[] downloadBytes(StorageChannel channel, String key) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        try {
            return clientFor(normalizedChannel).getObjectAsBytes(b -> b.bucket(bucket).key(k)).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "对象不存在");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "对象不存在");
            }
            throw e;
        }
    }

    public boolean exists(String key) {
        return exists(StorageChannel.PUBLIC, key);
    }

    public boolean exists(StorageChannel channel, String key) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        String bucket = requireBucket(normalizedChannel);
        String k = normalizeKey(key);
        try {
            clientFor(normalizedChannel).headObject(
                    b -> b.bucket(bucket)
                            .key(k)
            );
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            // 不吞其他异常，便于排查权限/网络/签名等问题
            throw e;
        }
    }

    public String buildPublicUrl(String key) {
        return buildPublicUrl(StorageChannel.PUBLIC, key);
    }

    public String buildPublicUrl(StorageChannel channel, String key) {
        // 显式兜底，避免静态分析误报/未来改动导致 NPE
        ThrowUtils.throwIf(key == null || key.isBlank(), ErrorCode.PARAMS_ERROR, "对象 key 不能为空");
        StorageChannel normalizedChannel = normalizeChannel(channel);
        if (normalizedChannel == StorageChannel.PRIVATE) {
            return null;
        }
        String k = normalizeKey(key);
        StorageR2Properties.Node node = requireNode(normalizedChannel);
        String domain = node.getPublicDomain();
        if (domain == null || domain.isBlank()) {
            // public-domain 未配置时回退到 endpoint/bucket/key（不保证公开可访问，但至少可定位）
            String endpoint = node.getEndpoint();
            endpoint = endpoint == null ? "" : endpoint.replaceAll("/$", "");
            return endpoint + "/" + requireBucket(normalizedChannel) + "/" + k;
        }
        return domain.replaceAll("/$", "") + "/" + k;
    }

    private String requireBucket(StorageChannel channel) {
        StorageR2Properties.Node node = requireNode(channel);
        ThrowUtils.throwIf(node.getBucket() == null || node.getBucket().isBlank(),
                ErrorCode.SYSTEM_ERROR,
                configPath(channel) + ".bucket 未配置");
        return node.getBucket().trim();
    }

    private StorageR2Properties.Node requireNode(StorageChannel channel) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        StorageR2Properties.Node node = props == null ? null : props.effectiveNode(normalizedChannel);
        ThrowUtils.throwIf(node == null, ErrorCode.SYSTEM_ERROR, configPath(normalizedChannel) + " 节点配置缺失");
        return node;
    }

    private S3Client clientFor(StorageChannel channel) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        if (normalizedChannel == StorageChannel.PUBLIC) {
            ThrowUtils.throwIf(s3Client == null, ErrorCode.SYSTEM_ERROR, "storage.r2.public S3 客户端未初始化");
            return s3Client;
        }
        return channelClients.computeIfAbsent(normalizedChannel,
                ch -> R2S3ClientConfig.buildClient(requireNode(ch), configPath(ch)));
    }

    private S3Presigner requirePresigner(StorageChannel channel) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        if (normalizedChannel == StorageChannel.PUBLIC) {
            ThrowUtils.throwIf(presigner == null, ErrorCode.SYSTEM_ERROR,
                    "storage.r2.public 预签名器未初始化");
            return presigner;
        }
        return channelPresigners.computeIfAbsent(normalizedChannel,
                ch -> R2S3ClientConfig.buildPresigner(requireNode(ch), configPath(ch)));
    }

    private static StorageChannel normalizeChannel(StorageChannel channel) {
        return channel == null ? StorageChannel.PUBLIC : channel;
    }

    private static String configPath(StorageChannel channel) {
        StorageChannel normalizedChannel = normalizeChannel(channel);
        return normalizedChannel == StorageChannel.PRIVATE ? "storage.r2.private" : "storage.r2.public";
    }

    private static String normalizeKey(String key) {
        // 这里用显式 throw，避免静态分析对 ThrowUtils 的识别问题
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "对象 key 不能为空");
        }
        String k = key.trim();
        while (k.startsWith("/")) {
            k = k.substring(1);
        }
        return k;
    }

    public record UploadResult(String publicUrl, String etag, String versionId, OffsetDateTime lastModified) {
    }
}
