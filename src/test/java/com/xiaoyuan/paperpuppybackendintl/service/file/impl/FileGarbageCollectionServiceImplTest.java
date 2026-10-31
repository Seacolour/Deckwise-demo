package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.xiaoyuan.paperpuppybackendintl.config.FileGarbageCollectionProperties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileGarbageCollectionResult;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.repository.FilePurgeRepository;
import com.xiaoyuan.paperpuppybackendintl.service.jobs.BackgroundJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileGarbageCollectionServiceImplTest {

    @Test
    void tryGarbageCollectSkipsWhenFileStillReferenced() {
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileGarbageCollectionServiceImpl service = newService(filePurgeRepository, r2Client);

        ObjectStorageFile file = ownedFile();
        when(filePurgeRepository.findOwnedFileForUpdate(7L, 51L)).thenReturn(Optional.of(file));
        when(filePurgeRepository.collectUsageSnapshot(51L))
                .thenReturn(new FilePurgeRepository.FilePurgeUsageSnapshot(1, 0, 0, 0, 0));

        FileGarbageCollectionResult result = service.tryGarbageCollect(7L, 51L, "project_delete:100");

        assertFalse(result.purged());
        assertEquals("referenced", result.status());
        assertEquals(1L, result.blockers().get("activeProjectLinks"));
        verify(filePurgeRepository, never()).hardDeleteFileRecord(7L, 51L);
        verify(r2Client, never()).delete(any(StorageChannel.class), eq("dev/files/7/source.pdf"));
    }

    @Test
    void tryGarbageCollectHardDeletesUnreferencedFileAndObjects() {
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileGarbageCollectionServiceImpl service = newService(filePurgeRepository, r2Client);

        ObjectStorageFile file = ownedFile();
        when(filePurgeRepository.findOwnedFileForUpdate(7L, 51L)).thenReturn(Optional.of(file));
        when(filePurgeRepository.collectUsageSnapshot(51L))
                .thenReturn(new FilePurgeRepository.FilePurgeUsageSnapshot(0, 0, 0, 0, 0));
        when(filePurgeRepository.listDerivativeObjectKeys(51L)).thenReturn(List.of("dev/files/7/derived.png"));
        when(filePurgeRepository.hardDeleteFileRecord(7L, 51L)).thenReturn(true);

        FileGarbageCollectionResult result = service.tryGarbageCollect(7L, 51L, "project_delete:100");

        assertTrue(result.purged());
        assertEquals("purged", result.status());
        assertEquals(2, result.scheduledObjectDeleteCount());
        verify(filePurgeRepository).revokeAccessGrants(51L);
        verify(filePurgeRepository).softDeleteTextFragments(51L);
        verify(filePurgeRepository).softDeleteDerivatives(51L);
        verify(filePurgeRepository).deleteInactiveReferencesForHardDelete(51L);
        verify(filePurgeRepository).hardDeleteFileRecord(7L, 51L);
        verify(r2Client).delete(StorageChannel.PUBLIC, "dev/files/7/source.pdf");
        verify(r2Client).delete(StorageChannel.PUBLIC, "dev/files/7/derived.png");
    }

    @Test
    void tryGarbageCollectSkipsWhenDerivedMediaStillReferencesFile() {
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileGarbageCollectionServiceImpl service = newService(filePurgeRepository, r2Client);

        ObjectStorageFile file = ownedFile();
        when(filePurgeRepository.findOwnedFileForUpdate(7L, 51L)).thenReturn(Optional.of(file));
        when(filePurgeRepository.collectUsageSnapshot(51L))
                .thenReturn(new FilePurgeRepository.FilePurgeUsageSnapshot(0, 0, 0, 1, 0));

        FileGarbageCollectionResult result = service.tryGarbageCollect(7L, 51L, "project_delete:100");

        assertFalse(result.purged());
        assertEquals("referenced", result.status());
        assertEquals(1L, result.blockers().get("activeDerivedMediaSourceReferences"));
        verify(filePurgeRepository, never()).hardDeleteFileRecord(7L, 51L);
        verify(r2Client, never()).delete(any(StorageChannel.class), eq("dev/files/7/source.pdf"));
    }

    @Test
    void tryGarbageCollectSkipsWhenDerivedMediaCacheIsRetained() {
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileGarbageCollectionProperties properties = new FileGarbageCollectionProperties();
        properties.setDerivedCacheRetentionSeconds(604800);
        FileGarbageCollectionServiceImpl service = newService(
                filePurgeRepository,
                r2Client,
                mock(BackgroundJobService.class),
                properties);

        ObjectStorageFile file = ownedFile();
        when(filePurgeRepository.findOwnedFileForUpdate(7L, 51L)).thenReturn(Optional.of(file));
        when(filePurgeRepository.collectUsageSnapshot(51L))
                .thenReturn(new FilePurgeRepository.FilePurgeUsageSnapshot(0, 0, 0, 0, 0));
        when(filePurgeRepository.countRetainedDerivedMediaCacheReferences(eq(51L), any(OffsetDateTime.class)))
                .thenReturn(1L);

        FileGarbageCollectionResult result = service.tryGarbageCollect(7L, 51L, "orphan_scanner:derived:available");

        assertFalse(result.purged());
        assertEquals("referenced", result.status());
        assertEquals(1L, result.blockers().get("retainedDerivedMediaCacheReferences"));
        verify(filePurgeRepository, never()).deleteInactiveReferencesForHardDelete(51L);
        verify(filePurgeRepository, never()).hardDeleteFileRecord(7L, 51L);
        verify(r2Client, never()).delete(any(StorageChannel.class), eq("dev/files/7/source.pdf"));
    }

    @Test
    void enqueueOrphanCandidatesSubmitsFileGcJobs() {
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        BackgroundJobService backgroundJobService = mock(BackgroundJobService.class);
        FileGarbageCollectionServiceImpl service = newService(
                filePurgeRepository,
                r2Client,
                backgroundJobService);
        OffsetDateTime cutoff = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        when(filePurgeRepository.listOrphanGarbageCollectionCandidates(eq(50), eq(cutoff), any(OffsetDateTime.class)))
                .thenReturn(List.of(
                        new FilePurgeRepository.FileGcCandidate(7L, 51L, "generated", "available"),
                        new FilePurgeRepository.FileGcCandidate(7L, 52L, "bytes", "stale")));

        int enqueued = service.enqueueOrphanCandidates(50, cutoff);

        assertEquals(2, enqueued);
        verify(backgroundJobService).enqueueAfterCommit(
                "file.gc",
                "file.gc:7:51",
                new FileGarbageCollectionServiceImpl.FileGcJobPayload(
                        7L,
                        51L,
                        "orphan_scanner:generated:available"),
                7L);
        verify(backgroundJobService).enqueueAfterCommit(
                "file.gc",
                "file.gc:7:52",
                new FileGarbageCollectionServiceImpl.FileGcJobPayload(
                        7L,
                        52L,
                        "orphan_scanner:bytes:stale"),
                7L);
    }

    @Test
    void enqueueProjectDeleteCandidateUsesDelayedExtendableJob() {
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        BackgroundJobService backgroundJobService = mock(BackgroundJobService.class);
        FileGarbageCollectionProperties properties = new FileGarbageCollectionProperties();
        properties.setProjectDeleteGraceSeconds(3600);
        FileGarbageCollectionServiceImpl service = newService(
                filePurgeRepository,
                r2Client,
                backgroundJobService,
                properties);

        service.enqueueProjectDeleteCandidateAfterCommit(7L, 51L, 100L);

        verify(backgroundJobService).enqueueAfterCommit(
                eq("file.gc"),
                eq("file.gc:7:51"),
                eq(new FileGarbageCollectionServiceImpl.FileGcJobPayload(
                        7L,
                        51L,
                        "project_delete:100")),
                eq(7L),
                any(OffsetDateTime.class),
                eq(true));
    }

    private static FileGarbageCollectionServiceImpl newService(FilePurgeRepository filePurgeRepository,
            R2Client r2Client) {
        return newService(filePurgeRepository, r2Client, mock(BackgroundJobService.class));
    }

    private static FileGarbageCollectionServiceImpl newService(FilePurgeRepository filePurgeRepository,
            R2Client r2Client,
            BackgroundJobService backgroundJobService) {
        return newService(filePurgeRepository, r2Client, backgroundJobService, new FileGarbageCollectionProperties());
    }

    private static FileGarbageCollectionServiceImpl newService(FilePurgeRepository filePurgeRepository,
            R2Client r2Client,
            BackgroundJobService backgroundJobService,
            FileGarbageCollectionProperties properties) {
        @SuppressWarnings("unchecked")
        ObjectProvider<BackgroundJobService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(backgroundJobService);
        return new FileGarbageCollectionServiceImpl(filePurgeRepository, provider, r2Client, properties);
    }

    private static ObjectStorageFile ownedFile() {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(51L);
        file.setOwnerUserId(7L);
        file.setObjectKey("dev/files/7/source.pdf");
        file.setDeleted(false);
        return file;
    }
}
