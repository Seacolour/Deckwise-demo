package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.config.PendingProjectFileProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PendingProjectFileFinalizeRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PendingProjectFileUploadResponse;
import com.xiaoyuan.paperpuppybackendintl.utils.file.InMemoryMultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingProjectFileService {

    private static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;
    private static final String DEFAULT_UPLOAD_SOURCE = "user_upload";

    private static final String F_OBJECT_KEY = "objectKey";
    private static final String F_OWNER_USER_ID = "ownerUserId";
    private static final String F_FILE_NAME = "fileName";
    private static final String F_CONTENT_TYPE = "contentType";
    private static final String F_SIZE_BYTES = "sizeBytes";
    private static final String F_CREATED_AT = "createdAt";
    private static final String F_EXPIRES_AT = "expiresAt";

    private final R2Client r2Client;
    private final StorageR2Properties r2Properties;
    private final PendingProjectFileProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisClient redisClient;
    private final FileService fileService;

    public PendingProjectFileUploadResponse upload(Long userId, MultipartFile file) {
        long startedAt = System.nanoTime();
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小超出限制（最大50MB）");

        long readStartedAt = System.nanoTime();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取上传文件失败");
        }
        ThrowUtils.throwIf(bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件不能为空");
        long readMs = elapsedMs(readStartedAt);

        String fileName = safeFileName(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType());
        String objectKey = buildPendingObjectKey(userId, fileName);
        long uploadStartedAt = System.nanoTime();
        r2Client.putObject(objectKey, bytes, contentType);
        long uploadMs = elapsedMs(uploadStartedAt);

        String pendingFileRef = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(safeMetadataTtlSeconds());
        Map<String, String> payload = new HashMap<>();
        payload.put(F_OBJECT_KEY, objectKey);
        payload.put(F_OWNER_USER_ID, String.valueOf(userId));
        payload.put(F_FILE_NAME, fileName);
        payload.put(F_CONTENT_TYPE, contentType);
        payload.put(F_SIZE_BYTES, String.valueOf(bytes.length));
        payload.put(F_CREATED_AT, String.valueOf(now.toEpochSecond()));
        payload.put(F_EXPIRES_AT, String.valueOf(expiresAt.toEpochSecond()));

        String redisKey = RedisKey.pendingProjectFile(pendingFileRef);
        stringRedisTemplate.opsForHash().putAll(redisKey, payload);
        stringRedisTemplate.expire(redisKey, redisRecordTtlSeconds(), TimeUnit.SECONDS);
        stringRedisTemplate.opsForZSet().add(
                RedisKey.pendingProjectFileCleanup(),
                pendingFileRef,
                expiresAt.toEpochSecond());

        PendingProjectFileUploadResponse response = new PendingProjectFileUploadResponse();
        response.setPendingFileRef(pendingFileRef);
        response.setFileName(fileName);
        response.setFileSize((long) bytes.length);
        response.setSizeBytes((long) bytes.length);
        response.setMimeType(contentType);
        response.setExpiresAt(expiresAt);
        log.info("[PendingProjectFile] upload finished: userId={}, fileName={}, bytes={}, readMs={}, tempUploadMs={}, elapsedMs={}",
                userId,
                fileName,
                bytes.length,
                readMs,
                uploadMs,
                elapsedMs(startedAt));
        return response;
    }

    public FileUploadCacheResponse finalizeUpload(Long userId, PendingProjectFileFinalizeRequest request) {
        long startedAt = System.nanoTime();
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");
        ThrowUtils.throwIf(request.getProjectId() == null || request.getProjectId() <= 0,
                ErrorCode.PARAMS_ERROR, "projectId 不能为空");

        String pendingFileRef = normalizeFileRef(request.getPendingFileRef());
        String lockKey = RedisKey.lock("pending-project-file:" + pendingFileRef);
        String lockToken = UUID.randomUUID().toString();
        if (!redisClient.setIfAbsent(lockKey, lockToken, 5, TimeUnit.MINUTES)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件正在处理，请稍后重试");
        }

        try {
            PendingMeta meta = requireMeta(pendingFileRef);
            ThrowUtils.throwIf(meta.ownerUserId() == null || !meta.ownerUserId().equals(userId),
                    ErrorCode.NO_AUTH_ERROR, "无权限访问该临时文件");
            if (meta.expiresAtEpochSeconds() <= Instant.now().getEpochSecond()) {
                cleanupOne(pendingFileRef, true);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "临时文件已过期，请重新上传");
            }

            long downloadStartedAt = System.nanoTime();
            byte[] bytes = r2Client.downloadBytes(meta.objectKey());
            long downloadMs = elapsedMs(downloadStartedAt);
            InMemoryMultipartFile file = new InMemoryMultipartFile(
                    "file",
                    meta.fileName(),
                    meta.contentType(),
                    bytes);
            long storeStartedAt = System.nanoTime();
            FileUploadCacheResponse response = fileService.uploadWithBytes(
                    userId,
                    file,
                    request.getProjectId(),
                    bytes,
                    Boolean.TRUE.equals(request.getExtractPdfImages()),
                    resolveUploadSource(request.getUploadSource()));
            long storeMs = elapsedMs(storeStartedAt);
            long cleanupStartedAt = System.nanoTime();
            deletePendingRecordAndObject(pendingFileRef, meta.objectKey());
            long cleanupMs = elapsedMs(cleanupStartedAt);
            log.info("[PendingProjectFile] finalize finished: userId={}, projectId={}, fileName={}, bytes={}, downloadMs={}, storeMs={}, cleanupMs={}, elapsedMs={}",
                    userId,
                    request.getProjectId(),
                    meta.fileName(),
                    bytes.length,
                    downloadMs,
                    storeMs,
                    cleanupMs,
                    elapsedMs(startedAt));
            return response;
        } finally {
            redisClient.deleteIfValue(lockKey, lockToken);
        }
    }

    @Scheduled(fixedDelayString = "${storage.r2.pending-project-file.cleanup-delay-ms:600000}")
    public void cleanupExpiredPendingFiles() {
        Set<String> refs = stringRedisTemplate.opsForZSet().rangeByScore(
                RedisKey.pendingProjectFileCleanup(),
                0,
                Instant.now().getEpochSecond(),
                0,
                safeCleanupBatchSize());
        if (refs == null || refs.isEmpty()) {
            return;
        }
        for (String ref : refs) {
            try {
                cleanupOne(ref, false);
            } catch (Exception e) {
                log.warn("[PendingProjectFile] failed to cleanup pending file, ref={}, err={}",
                        ref,
                        e.getMessage());
            }
        }
    }

    private PendingMeta requireMeta(String pendingFileRef) {
        String key = RedisKey.pendingProjectFile(pendingFileRef);
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (map == null || map.isEmpty()) {
            stringRedisTemplate.opsForZSet().remove(RedisKey.pendingProjectFileCleanup(), pendingFileRef);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "临时文件不存在或已过期");
        }
        String objectKey = asString(map.get(F_OBJECT_KEY));
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "临时文件元数据损坏(objectKey)");
        }
        Long ownerUserId = parseLong(asString(map.get(F_OWNER_USER_ID)));
        Long expiresAt = parseLong(asString(map.get(F_EXPIRES_AT)));
        if (expiresAt == null || expiresAt <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "临时文件元数据损坏(expiresAt)");
        }
        return new PendingMeta(
                objectKey,
                ownerUserId,
                safeFileName(asString(map.get(F_FILE_NAME))),
                resolveContentType(asString(map.get(F_CONTENT_TYPE))),
                parseLong(asString(map.get(F_SIZE_BYTES))),
                expiresAt);
    }

    private void cleanupOne(String pendingFileRef, boolean force) {
        String ref = normalizeFileRef(pendingFileRef);
        String key = RedisKey.pendingProjectFile(ref);
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if (map == null || map.isEmpty()) {
            stringRedisTemplate.opsForZSet().remove(RedisKey.pendingProjectFileCleanup(), ref);
            return;
        }

        Long expiresAt = parseLong(asString(map.get(F_EXPIRES_AT)));
        if (!force && (expiresAt == null || expiresAt > Instant.now().getEpochSecond())) {
            return;
        }
        String objectKey = asString(map.get(F_OBJECT_KEY));
        if (objectKey != null && !objectKey.isBlank()) {
            r2Client.delete(objectKey);
        }
        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForZSet().remove(RedisKey.pendingProjectFileCleanup(), ref);
    }

    private void deletePendingRecordAndObject(String pendingFileRef, String objectKey) {
        try {
            if (objectKey != null && !objectKey.isBlank()) {
                r2Client.delete(objectKey);
            }
        } catch (Exception e) {
            log.warn("[PendingProjectFile] finalized but failed to delete pending temp object, ref={}, key={}, err={}",
                    pendingFileRef,
                    objectKey,
                    e.getMessage());
        }
        stringRedisTemplate.delete(RedisKey.pendingProjectFile(pendingFileRef));
        stringRedisTemplate.opsForZSet().remove(RedisKey.pendingProjectFileCleanup(), pendingFileRef);
    }

    private String buildPendingObjectKey(Long userId, String originalName) {
        String safeName = safeFileName(originalName).replaceAll("[^A-Za-z0-9._-]", "_");
        String base = "tmp/pending-project-files/" + userId + "/" + UUID.randomUUID() + "_" + safeName;
        String prefix = r2Properties == null ? null : r2Properties.effectivePathPrefix();
        if (prefix == null || prefix.isBlank()) {
            return base;
        }
        return prefix.replaceAll("/$", "") + "/" + base;
    }

    private String normalizeFileRef(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "pendingFileRef 不能为空");
        }
        return value.trim();
    }

    private String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "upload.bin";
        }
        return name.trim();
    }

    private String resolveContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.trim();
    }

    private String resolveUploadSource(String uploadSource) {
        return uploadSource == null || uploadSource.isBlank()
                ? DEFAULT_UPLOAD_SOURCE
                : uploadSource.trim();
    }

    private long safeMetadataTtlSeconds() {
        long ttl = properties.getMetadataTtlSeconds();
        return ttl <= 0 ? 24 * 60 * 60 : ttl;
    }

    private long redisRecordTtlSeconds() {
        long grace = properties.getCleanupGraceSeconds();
        if (grace <= 0) {
            grace = 7 * 24 * 60 * 60;
        }
        return safeMetadataTtlSeconds() + grace;
    }

    private long safeCleanupBatchSize() {
        int configured = properties.getCleanupBatchSize();
        return Math.max(1, configured);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long elapsedMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }

    private record PendingMeta(String objectKey,
                               Long ownerUserId,
                               String fileName,
                               String contentType,
                               Long sizeBytes,
                               long expiresAtEpochSeconds) {
    }
}
