package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ObjectStorageFileSourceType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageVisibility;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Service
@Slf4j
public class ObjectStorageFileWriteService {

    private final R2Client r2Client;
    private final StorageR2Properties r2Properties;
    private final ObjectStorageFileRepository objectStorageFileRepository;

    public ObjectStorageFileWriteService(R2Client r2Client,
                                         StorageR2Properties r2Properties,
                                         ObjectStorageFileRepository objectStorageFileRepository) {
        this.r2Client = r2Client;
        this.r2Properties = r2Properties;
        this.objectStorageFileRepository = objectStorageFileRepository;
    }

    public ObjectStorageFileWriteResult resolveOrStore(ObjectStorageFileWriteRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "文件写入请求不能为空");
        ThrowUtils.throwIf(request.ownerUserId() == null || request.ownerUserId() <= 0,
                ErrorCode.NOT_LOGIN_ERROR,
                "未登录");
        ThrowUtils.throwIf(request.bytes() == null || request.bytes().length == 0,
                ErrorCode.PARAMS_ERROR,
                "文件内容为空");

        String sourceType = ObjectStorageFileSourceType.normalize(request.sourceType());
        ThrowUtils.throwIf(!ObjectStorageFileSourceType.isKnown(sourceType), ErrorCode.PARAMS_ERROR, "sourceType不支持");
        String sourceKey = trimToNull(request.sourceKey());
        ThrowUtils.throwIf(sourceKey == null, ErrorCode.PARAMS_ERROR, "sourceKey不能为空");

        ObjectStorageWriteOptions options = request.options() == null
                ? ObjectStorageWriteOptions.publicObject(null)
                : request.options();
        StorageChannel channel = options.effectiveChannel();
        StorageVisibility visibility = options.effectiveVisibility();
        String channelValue = channel.value();

        Optional<ObjectStorageFile> existingAvailable = objectStorageFileRepository
                .findAvailableByOwnerSourceAndChannel(request.ownerUserId(), sourceType, sourceKey, channelValue);
        if (existingAvailable.isPresent()) {
            ObjectStorageFile stored = existingAvailable.get();
            StoredObjectAvailability availability = checkStoredObjectAvailability(stored, channel);
            if (availability == StoredObjectAvailability.AVAILABLE
                    || availability == StoredObjectAvailability.UNKNOWN) {
                return new ObjectStorageFileWriteResult(stored, buildPublicUrl(stored, channel), false);
            }
            objectStorageFileRepository.markStale(stored.getId());
        }

        ObjectStorageFile existingAny = objectStorageFileRepository
                .findByOwnerSourceAndChannel(request.ownerUserId(), sourceType, sourceKey, channelValue)
                .orElse(null);
        if (existingAny != null) {
            StoredObjectAvailability availability = checkStoredObjectAvailability(existingAny, channel);
            if (availability == StoredObjectAvailability.AVAILABLE) {
                if (!isReusableRecord(existingAny)) {
                    existingAny.setStatus("available");
                    existingAny.setDeleted(false);
                    objectStorageFileRepository.update(existingAny);
                }
                return new ObjectStorageFileWriteResult(existingAny, buildPublicUrl(existingAny, channel), false);
            }
        }

        String extWithDot = normalizeExtWithDot(request.fileExt());
        String objectKey = buildObjectKey(request.ownerUserId(), options.scopePrefix(), sourceKey, extWithDot, channel);
        String contentType = normalizeContentType(request.contentType());
        R2Client.UploadResult uploaded = r2Client.uploadWithMeta(channel, objectKey, request.bytes(), contentType);
        String publicUrl = uploaded == null ? r2Client.buildPublicUrl(channel, objectKey) : uploaded.publicUrl();

        if (existingAny != null) {
            populateStoredFile(existingAny, request, sourceType, objectKey, extWithDot, channel, visibility, uploaded);
            objectStorageFileRepository.update(existingAny);
            return new ObjectStorageFileWriteResult(existingAny, publicUrl, false);
        }

        ObjectStorageFile record = new ObjectStorageFile();
        populateStoredFile(record, request, sourceType, objectKey, extWithDot, channel, visibility, uploaded);
        ObjectStorageFile stored = objectStorageFileRepository.insert(record);
        if (stored != null && stored.getId() != null) {
            return new ObjectStorageFileWriteResult(stored, publicUrl, true);
        }

        stored = objectStorageFileRepository
                .findAvailableByOwnerSourceAndChannel(request.ownerUserId(), sourceType, sourceKey, channelValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "文件写入失败"));
        return new ObjectStorageFileWriteResult(stored, buildPublicUrl(stored, channel), false);
    }

    private void populateStoredFile(ObjectStorageFile fileRecord,
                                    ObjectStorageFileWriteRequest request,
                                    String sourceType,
                                    String objectKey,
                                    String extWithDot,
                                    StorageChannel channel,
                                    StorageVisibility visibility,
                                    R2Client.UploadResult uploaded) {
        fileRecord.setOwnerUserId(request.ownerUserId());
        fileRecord.setSourceType(sourceType);
        fileRecord.setSourceKey(request.sourceKey());
        fileRecord.setStorageType("r2");
        fileRecord.setStorageChannel(channel.value());
        fileRecord.setVisibility(visibility.value());
        fileRecord.setBucket(r2Properties.effectiveBucket(channel));
        fileRecord.setObjectKey(objectKey);
        fileRecord.setPublicDomain(channel == StorageChannel.PUBLIC ? r2Properties.effectivePublicDomain(channel) : null);
        fileRecord.setPublicPath(channel == StorageChannel.PUBLIC ? objectKey : null);
        fileRecord.setMimeType(normalizeContentType(request.contentType()));
        fileRecord.setFileExt(extWithoutDot(extWithDot));
        fileRecord.setSizeBytes(request.sizeBytes() == null ? (long) request.bytes().length : request.sizeBytes());
        fileRecord.setOriginalName(trimToNull(request.originalName()));
        fileRecord.setStatus("available");
        fileRecord.setDeleted(false);
        if (uploaded != null) {
            fileRecord.setEtag(uploaded.etag());
            fileRecord.setVersionId(uploaded.versionId());
            fileRecord.setLastModified(uploaded.lastModified());
        } else {
            fileRecord.setEtag(null);
            fileRecord.setVersionId(null);
            fileRecord.setLastModified(null);
        }
    }

    private StoredObjectAvailability checkStoredObjectAvailability(ObjectStorageFile stored, StorageChannel fallbackChannel) {
        if (stored == null || stored.getObjectKey() == null || stored.getObjectKey().isBlank()) {
            return StoredObjectAvailability.MISSING;
        }
        StorageChannel channel = StorageChannel.isPrivate(stored.getStorageChannel())
                ? StorageChannel.PRIVATE
                : fallbackChannel;
        try {
            return r2Client.exists(channel, stored.getObjectKey())
                    ? StoredObjectAvailability.AVAILABLE
                    : StoredObjectAvailability.MISSING;
        } catch (Exception e) {
            log.warn("[ObjectStorageWrite] failed to verify object existence, fallback to DB state: fileId={}, key={}, channel={}, err={}",
                    stored.getId(), stored.getObjectKey(), channel.value(), e.getMessage());
            return StoredObjectAvailability.UNKNOWN;
        }
    }

    private String buildObjectKey(Long userId,
                                  String scopePrefix,
                                  String sourceKey,
                                  String extWithDot,
                                  StorageChannel channel) {
        String prefix = r2Properties.effectivePathPrefix(channel);
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String ext = extWithDot == null ? "" : extWithDot;
        String scope = trimToNull(scopePrefix);
        if (scope == null) {
            scope = "uploads";
        }
        String base = scope + "/" + userId + "/" + date + "/" + sourceKey + ext;
        if (prefix == null || prefix.isBlank()) {
            return base;
        }
        return prefix.replaceAll("/$", "") + "/" + base;
    }

    private String buildPublicUrl(ObjectStorageFile file, StorageChannel fallbackChannel) {
        if (file == null || file.getObjectKey() == null || file.getObjectKey().isBlank()) {
            return null;
        }
        StorageChannel channel = StorageChannel.isPrivate(file.getStorageChannel())
                ? StorageChannel.PRIVATE
                : fallbackChannel;
        if (channel == StorageChannel.PRIVATE) {
            return null;
        }
        if (file.getPublicDomain() != null && !file.getPublicDomain().isBlank()
                && file.getPublicPath() != null && !file.getPublicPath().isBlank()) {
            return file.getPublicDomain().replaceAll("/$", "") + "/" + file.getPublicPath().replaceFirst("^/", "");
        }
        return r2Client.buildPublicUrl(channel, file.getObjectKey());
    }

    private boolean isReusableRecord(ObjectStorageFile stored) {
        return stored != null
                && !Boolean.TRUE.equals(stored.getDeleted())
                && "available".equalsIgnoreCase(stored.getStatus());
    }

    private String normalizeContentType(String contentType) {
        String value = trimToNull(contentType);
        return value == null ? "application/octet-stream" : value;
    }

    private String normalizeExtWithDot(String fileExt) {
        String ext = trimToNull(fileExt);
        if (ext == null) {
            return "";
        }
        ext = ext.toLowerCase(Locale.ROOT);
        return ext.startsWith(".") ? ext : "." + ext;
    }

    private String extWithoutDot(String extWithDot) {
        String ext = trimToNull(extWithDot);
        if (ext == null) {
            return "";
        }
        while (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        return ext;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private enum StoredObjectAvailability {
        AVAILABLE,
        MISSING,
        UNKNOWN
    }
}
