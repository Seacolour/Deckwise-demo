package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.xiaoyuan.paperpuppybackendintl.config.PendingProjectFileProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PendingProjectFileFinalizeRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PendingProjectFileUploadResponse;
import com.xiaoyuan.paperpuppybackendintl.utils.file.InMemoryMultipartFile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingProjectFileServiceTest {

    @Test
    void uploadStoresPublicObjectAndRedisMetadataWithoutCreatingObjectStorageFile() {
        Fixture fixture = new Fixture();
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "brief.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        PendingProjectFileUploadResponse response = fixture.service().upload(7L, file);

        assertNotNull(response.getPendingFileRef());
        assertEquals("brief.pdf", response.getFileName());
        assertEquals(3L, response.getFileSize());
        assertEquals(3L, response.getSizeBytes());
        verify(fixture.r2Client()).putObject(any(String.class), aryEq(new byte[]{1, 2, 3}), eq("application/pdf"));
        verify(fixture.redis()).expire(eq(RedisKey.pendingProjectFile(response.getPendingFileRef())),
                eq(691200L), eq(TimeUnit.SECONDS));
        verify(fixture.zSet()).add(eq(RedisKey.pendingProjectFileCleanup()), eq(response.getPendingFileRef()), anyDouble());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.hash()).putAll(eq(RedisKey.pendingProjectFile(response.getPendingFileRef())),
                payloadCaptor.capture());
        assertEquals("7", payloadCaptor.getValue().get("ownerUserId"));
        assertEquals("brief.pdf", payloadCaptor.getValue().get("fileName"));
    }

    @Test
    void finalizeConsumesPendingFileThroughFileServiceAndDeletesTempObject() {
        Fixture fixture = new Fixture();
        String pendingRef = "pending-ref";
        byte[] bytes = new byte[]{4, 5, 6};
        Map<Object, Object> meta = new HashMap<>();
        meta.put("objectKey", "dev/tmp/file.pdf");
        meta.put("ownerUserId", "7");
        meta.put("fileName", "file.pdf");
        meta.put("contentType", "application/pdf");
        meta.put("sizeBytes", "3");
        meta.put("expiresAt", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()));
        when(fixture.hash().entries(RedisKey.pendingProjectFile(pendingRef))).thenReturn(meta);
        when(fixture.redisClient().setIfAbsent(any(String.class), any(String.class), eq(5L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(fixture.r2Client().downloadBytes("dev/tmp/file.pdf")).thenReturn(bytes);
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setSourceFileId(21L);
        when(fixture.fileService().uploadWithBytes(eq(7L), any(MultipartFile.class), eq(100L),
                eq(bytes), eq(true), eq("user_upload"))).thenReturn(uploaded);
        PendingProjectFileFinalizeRequest request = new PendingProjectFileFinalizeRequest();
        request.setPendingFileRef(pendingRef);
        request.setProjectId(100L);
        request.setExtractPdfImages(true);

        FileUploadCacheResponse response = fixture.service().finalizeUpload(7L, request);

        assertEquals(21L, response.getSourceFileId());
        verify(fixture.r2Client()).delete("dev/tmp/file.pdf");
        verify(fixture.redis()).delete(RedisKey.pendingProjectFile(pendingRef));
        verify(fixture.zSet()).remove(RedisKey.pendingProjectFileCleanup(), pendingRef);
    }

    private static final class Fixture {
        private final R2Client r2Client = mock(R2Client.class);
        private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        private final HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        @SuppressWarnings("unchecked")
        private final ZSetOperations<String, String> zSet = mock(ZSetOperations.class);
        private final RedisClient redisClient = mock(RedisClient.class);
        private final FileService fileService = mock(FileService.class);
        private final PendingProjectFileService service;

        private Fixture() {
            StorageR2Properties storage = new StorageR2Properties();
            storage.setBucket("paperpuppy");
            storage.setPathPrefix("dev");
            PendingProjectFileProperties properties = new PendingProjectFileProperties();
            properties.setMetadataTtlSeconds(86400);
            properties.setCleanupGraceSeconds(604800);
            when(redis.opsForHash()).thenReturn(hash);
            when(redis.opsForZSet()).thenReturn(zSet);
            service = new PendingProjectFileService(
                    r2Client,
                    storage,
                    properties,
                    redis,
                    redisClient,
                    fileService);
        }

        private PendingProjectFileService service() {
            return service;
        }

        private R2Client r2Client() {
            return r2Client;
        }

        private StringRedisTemplate redis() {
            return redis;
        }

        private HashOperations<String, Object, Object> hash() {
            return hash;
        }

        private ZSetOperations<String, String> zSet() {
            return zSet;
        }

        private RedisClient redisClient() {
            return redisClient;
        }

        private FileService fileService() {
            return fileService;
        }
    }
}
