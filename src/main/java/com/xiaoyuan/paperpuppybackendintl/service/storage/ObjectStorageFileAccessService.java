package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.config.ObjectDeliveryWorkerProperties;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PrivateObjectAccessUrlResponse;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageVisibility;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ObjectStorageFileAccessService {

    private static final long FALLBACK_ACCESS_URL_TTL_SECONDS = 600;
    private static final long FALLBACK_MIN_ACCESS_URL_TTL_SECONDS = 60;
    private static final long FALLBACK_MAX_ACCESS_URL_TTL_SECONDS = 1800;

    private static final String STATUS_AVAILABLE = "available";

    private final ObjectStorageFileRepository objectStorageFileRepository;
    private final FileAccessGrantRepository fileAccessGrantRepository;
    private final ObjectAccessUrlService objectAccessUrlService;
    private final ObjectDeliveryWorkerProperties workerProperties;

    public ObjectStorageFileAccessService(ObjectStorageFileRepository objectStorageFileRepository,
                                          FileAccessGrantRepository fileAccessGrantRepository,
                                          ObjectAccessUrlService objectAccessUrlService,
                                          ObjectDeliveryWorkerProperties workerProperties) {
        this.objectStorageFileRepository = objectStorageFileRepository;
        this.fileAccessGrantRepository = fileAccessGrantRepository;
        this.objectAccessUrlService = objectAccessUrlService;
        this.workerProperties = workerProperties == null
                ? new ObjectDeliveryWorkerProperties()
                : workerProperties;
    }

    public PrivateObjectAccessUrlResponse fileAccessUrl(Long userId,
                                                        Long fileId,
                                                        Long projectId,
                                                        Long ttlSeconds) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(fileId == null || fileId <= 0, ErrorCode.PARAMS_ERROR, "文件ID非法");

        ObjectStorageFile file = objectStorageFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或不可用"));
        requireAvailable(file);
        requireAccess(userId, file, projectId);

        String storageChannel = normalizedStorageChannel(file.getStorageChannel());
        String visibility = normalizedVisibility(file.getVisibility());
        file.setStorageChannel(storageChannel);
        file.setVisibility(visibility);

        long effectiveTtlSeconds = validateAccessUrlTtl(ttlSeconds);
        String accessUrl;
        String scope;
        long responseTtlSeconds;
        if (StorageVisibility.isPrivate(visibility) && StorageChannel.isPrivate(storageChannel)) {
            accessUrl = objectAccessUrlService.privateFileUrl(file, Duration.ofSeconds(effectiveTtlSeconds));
            scope = "private";
            responseTtlSeconds = effectiveTtlSeconds;
        } else {
            accessUrl = objectAccessUrlService.publicUrl(file);
            scope = "public";
            responseTtlSeconds = 0;
        }

        if (accessUrl == null || accessUrl.isBlank()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件访问 URL 生成失败");
        }

        PrivateObjectAccessUrlResponse response = new PrivateObjectAccessUrlResponse();
        response.setFileId(file.getId());
        response.setObjectKey(file.getObjectKey());
        response.setAccessUrl(accessUrl);
        response.setScope(scope);
        response.setStorageChannel(storageChannel);
        response.setVisibility(visibility);
        response.setTtlSeconds(responseTtlSeconds);
        return response;
    }

    public long validateAccessUrlTtl(Long ttlSeconds) {
        long minTtlSeconds = accessTokenMinTtlSeconds();
        long maxTtlSeconds = accessTokenMaxTtlSeconds(minTtlSeconds);
        long effective = ttlSeconds == null
                ? accessTokenDefaultTtlSeconds(minTtlSeconds, maxTtlSeconds)
                : ttlSeconds;
        if (effective < minTtlSeconds || effective > maxTtlSeconds) {
            throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "ttlSeconds 必须在 " + minTtlSeconds + " 到 " + maxTtlSeconds + " 秒之间");
        }
        return effective;
    }

    private long accessTokenDefaultTtlSeconds(long minTtlSeconds, long maxTtlSeconds) {
        long configured = positiveOrFallback(
                workerProperties.getAccessTokenDefaultTtlSeconds(),
                FALLBACK_ACCESS_URL_TTL_SECONDS);
        return Math.max(minTtlSeconds, Math.min(maxTtlSeconds, configured));
    }

    private long accessTokenMinTtlSeconds() {
        return positiveOrFallback(
                workerProperties.getAccessTokenMinTtlSeconds(),
                FALLBACK_MIN_ACCESS_URL_TTL_SECONDS);
    }

    private long accessTokenMaxTtlSeconds(long minTtlSeconds) {
        long configured = positiveOrFallback(
                workerProperties.getAccessTokenMaxTtlSeconds(),
                FALLBACK_MAX_ACCESS_URL_TTL_SECONDS);
        return Math.max(minTtlSeconds, configured);
    }

    private long positiveOrFallback(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private void requireAvailable(ObjectStorageFile file) {
        if (file == null
                || Boolean.TRUE.equals(file.getDeleted())
                || !STATUS_AVAILABLE.equalsIgnoreCase(file.getStatus())
                || file.getObjectKey() == null
                || file.getObjectKey().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或不可用");
        }
    }

    private void requireAccess(Long userId, ObjectStorageFile file, Long projectId) {
        if (file.getOwnerUserId() != null && file.getOwnerUserId().equals(userId)) {
            return;
        }
        if (projectId != null
                && projectId > 0
                && fileAccessGrantRepository.hasValidLink(userId, file.getId(), projectId)) {
            return;
        }
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户无权限访问该文件");
    }

    private String normalizedStorageChannel(String value) {
        String normalized = StorageChannel.normalize(value);
        return StorageChannel.isKnown(normalized) ? normalized : StorageChannel.PUBLIC.value();
    }

    private String normalizedVisibility(String value) {
        String normalized = StorageVisibility.normalize(value);
        return StorageVisibility.isKnown(normalized) ? normalized : StorageVisibility.PUBLIC.value();
    }
}
