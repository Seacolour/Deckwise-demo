package com.xiaoyuan.paperpuppybackendintl.service.storage;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.config.ObjectDeliveryWorkerProperties;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PrivateObjectAccessUrlResponse;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageVisibility;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ObjectStorageFileAccessServiceTest {

    @Test
    void ownerPrivateFileGetsResourceTokenWorkerUrl() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = file(51L, 7L, StorageChannel.PRIVATE.value(), StorageVisibility.PRIVATE.value());
        when(fixture.fileRepository.findById(51L)).thenReturn(Optional.of(file));
        when(fixture.objectAccessUrlService.privateFileUrl(file, Duration.ofSeconds(300)))
                .thenReturn("https://file.example.com/dev/files/7/a.pdf?rt=token");

        PrivateObjectAccessUrlResponse response = fixture.service.fileAccessUrl(7L, 51L, null, 300L);

        assertEquals(51L, response.getFileId());
        assertEquals("dev/files/7/a.pdf", response.getObjectKey());
        assertEquals("https://file.example.com/dev/files/7/a.pdf?rt=token", response.getAccessUrl());
        assertEquals("private", response.getScope());
        assertEquals(StorageChannel.PRIVATE.value(), response.getStorageChannel());
        assertEquals(StorageVisibility.PRIVATE.value(), response.getVisibility());
        assertEquals(300, response.getTtlSeconds());
        verify(fixture.objectAccessUrlService).privateFileUrl(file, Duration.ofSeconds(300));
        verify(fixture.objectAccessUrlService, never()).publicUrl(file);
        verifyNoInteractions(fixture.fileAccessGrantRepository);
    }

    @Test
    void publicFileReturnsPublicUrlWithoutSigning() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = file(52L, 7L, StorageChannel.PUBLIC.value(), StorageVisibility.PUBLIC.value());
        when(fixture.fileRepository.findById(52L)).thenReturn(Optional.of(file));
        when(fixture.objectAccessUrlService.publicUrl(file))
                .thenReturn("https://assets.example.com/dev/files/7/a.pdf");

        PrivateObjectAccessUrlResponse response = fixture.service.fileAccessUrl(7L, 52L, null, 600L);

        assertEquals("public", response.getScope());
        assertEquals("https://assets.example.com/dev/files/7/a.pdf", response.getAccessUrl());
        assertEquals(0, response.getTtlSeconds());
        verify(fixture.objectAccessUrlService).publicUrl(file);
        verify(fixture.objectAccessUrlService, never()).privateFileUrl(file, Duration.ofSeconds(600));
    }

    @Test
    void projectGrantAllowsNonOwnerPrivateFileAccess() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = file(53L, 8L, StorageChannel.PRIVATE.value(), StorageVisibility.PRIVATE.value());
        when(fixture.fileRepository.findById(53L)).thenReturn(Optional.of(file));
        when(fixture.fileAccessGrantRepository.hasValidLink(7L, 53L, 100L)).thenReturn(true);
        when(fixture.objectAccessUrlService.privateFileUrl(file, Duration.ofSeconds(600)))
                .thenReturn("https://file.example.com/dev/files/8/a.pdf?rt=token");

        PrivateObjectAccessUrlResponse response = fixture.service.fileAccessUrl(7L, 53L, 100L, null);

        assertEquals("private", response.getScope());
        assertEquals(600, response.getTtlSeconds());
        verify(fixture.fileAccessGrantRepository).hasValidLink(7L, 53L, 100L);
    }

    @Test
    void nonOwnerWithoutGrantCannotGetUrl() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = file(54L, 8L, StorageChannel.PRIVATE.value(), StorageVisibility.PRIVATE.value());
        when(fixture.fileRepository.findById(54L)).thenReturn(Optional.of(file));
        when(fixture.fileAccessGrantRepository.hasValidLink(7L, 54L, 100L)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.fileAccessUrl(7L, 54L, 100L, null)
        );

        assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(fixture.objectAccessUrlService);
    }

    @Test
    void unavailableFileCannotGetUrl() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = file(55L, 7L, StorageChannel.PRIVATE.value(), StorageVisibility.PRIVATE.value());
        file.setStatus("stale");
        when(fixture.fileRepository.findById(55L)).thenReturn(Optional.of(file));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.fileAccessUrl(7L, 55L, null, null)
        );

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(fixture.objectAccessUrlService);
    }

    @Test
    void invalidTtlIsRejectedBeforeUrlGeneration() {
        Fixture fixture = new Fixture();
        ObjectStorageFile file = file(56L, 7L, StorageChannel.PRIVATE.value(), StorageVisibility.PRIVATE.value());
        when(fixture.fileRepository.findById(56L)).thenReturn(Optional.of(file));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.fileAccessUrl(7L, 56L, null, 30L)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(fixture.objectAccessUrlService);
    }

    @Test
    void accessUrlTtlUsesConfiguredBounds() {
        Fixture fixture = new Fixture();
        fixture.workerProperties.setAccessTokenDefaultTtlSeconds(900);
        fixture.workerProperties.setAccessTokenMinTtlSeconds(120);
        fixture.workerProperties.setAccessTokenMaxTtlSeconds(1200);

        assertEquals(900, fixture.service.validateAccessUrlTtl(null));
        assertEquals(120, fixture.service.validateAccessUrlTtl(120L));
        assertEquals(1200, fixture.service.validateAccessUrlTtl(1200L));
        assertThrows(BusinessException.class, () -> fixture.service.validateAccessUrlTtl(60L));
        assertThrows(BusinessException.class, () -> fixture.service.validateAccessUrlTtl(1800L));
    }

    private ObjectStorageFile file(Long fileId, Long ownerUserId, String storageChannel, String visibility) {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(fileId);
        file.setOwnerUserId(ownerUserId);
        file.setStorageChannel(storageChannel);
        file.setVisibility(visibility);
        file.setObjectKey("dev/files/" + ownerUserId + "/a.pdf");
        file.setStatus("available");
        file.setDeleted(false);
        return file;
    }

    private static class Fixture {
        private final ObjectStorageFileRepository fileRepository = mock(ObjectStorageFileRepository.class);
        private final FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        private final ObjectAccessUrlService objectAccessUrlService = mock(ObjectAccessUrlService.class);
        private final ObjectDeliveryWorkerProperties workerProperties = new ObjectDeliveryWorkerProperties();
        private final ObjectStorageFileAccessService service = new ObjectStorageFileAccessService(
                fileRepository,
                fileAccessGrantRepository,
                objectAccessUrlService,
                workerProperties
        );
    }
}
