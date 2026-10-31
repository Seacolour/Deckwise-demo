package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgeRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgeResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileMappingDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.RecentProjectFileDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileDerivativeRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FilePurgeRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileTextFragmentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.internal.DocumentAutoCreateService;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.utils.HashUtils;
import com.xiaoyuan.paperpuppybackendintl.utils.file.InMemoryMultipartFile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceImplTest {

    @Test
    void listRecentUserUploadedFilesReturnsUploadMetadataByCreatedAt() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = newService(r2Client, objectStorageFileRepository);
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-05-01T12:00:00Z");

        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(21L);
        file.setSourceKey("cache-21");
        file.setOriginalName("Brief.pdf");
        file.setMimeType("application/pdf");
        file.setFileExt("pdf");
        file.setSizeBytes(4096L);
        file.setObjectKey("dev/files/7/brief.pdf");
        file.setPublicDomain("https://assets.example.com/");
        file.setPublicPath("/dev/files/7/brief.pdf");
        file.setCreatedAt(createdAt);

        when(objectStorageFileRepository.listRecentUserUploadedFiles(7L, 50)).thenReturn(List.of(file));

        List<RecentProjectFileDTO> recentFiles = service.listRecentUserUploadedFiles(7L, 100);

        assertEquals(1, recentFiles.size());
        RecentProjectFileDTO recentFile = recentFiles.get(0);
        assertEquals(21L, recentFile.getFileId());
        assertEquals("cache-21", recentFile.getCacheKey());
        assertEquals("Brief.pdf", recentFile.getFileName());
        assertEquals("application/pdf", recentFile.getMimeType());
        assertEquals("pdf", recentFile.getFileExt());
        assertEquals(4096L, recentFile.getSizeBytes());
        assertEquals("https://assets.example.com/dev/files/7/brief.pdf", recentFile.getPublicUrl());
        assertEquals(createdAt, recentFile.getCreatedAt());
    }

    @Test
    void listProjectFilesReturnsStoredSizeBytes() {
        R2Client r2Client = mock(R2Client.class);
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        FileServiceImpl service = new FileServiceImpl(
                r2Client,
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                userRepository,
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                mock(FileAccessGrantRepository.class),
                mock(DocumentAutoCreateService.class),
                documentRepository,
                null
        );
        Project project = activeOwnedProject(100L, 7L);
        ProjectFileLinkView view = new ProjectFileLinkView();
        view.setProjectId(100L);
        view.setFileId(21L);
        view.setCacheKey("cache-21");
        view.setDisplayName("Brief.pdf");
        view.setSizeBytes(4096L);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(project));
        when(projectFileLinkRepository.listProjectFiles(100L)).thenReturn(List.of(view));
        when(documentRepository.listByProjectAndCurrentFileIds(100L, List.of(21L))).thenReturn(List.of());

        List<ProjectFileMappingDTO> files = service.listProjectFiles(7L, 100L);

        assertEquals(1, files.size());
        assertEquals(4096L, files.get(0).getSizeBytes());
    }

    @Test
    void listProjectFilesHidesSupersededDocumentVersions() {
        R2Client r2Client = mock(R2Client.class);
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        FileServiceImpl service = new FileServiceImpl(
                r2Client,
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                userRepository,
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                mock(FileAccessGrantRepository.class),
                mock(DocumentAutoCreateService.class),
                documentRepository,
                null
        );

        ProjectFileLinkView current = projectFile(22L, "notes.md");
        ProjectFileLinkView superseded = projectFile(21L, "notes.md");
        ProjectFileLinkView sourceMaterial = projectFile(30L, "brief.pdf");

        Document document = new Document();
        document.setId(455L);
        document.setProjectId(100L);
        document.setCurrentFileId(22L);
        document.setDocumentType(DocumentType.MARKDOWN);
        document.setVersion(2);

        List<Long> linkedFileIds = List.of(22L, 21L, 30L);
        when(projectRepository.findById(100L)).thenReturn(Optional.of(activeOwnedProject(100L, 7L)));
        when(projectFileLinkRepository.listProjectFiles(100L))
                .thenReturn(List.of(current, superseded, sourceMaterial));
        when(documentRepository.listByProjectAndCurrentFileIds(100L, linkedFileIds))
                .thenReturn(List.of(document));
        when(documentRepository.listSupersededVersionFileIdsByProject(100L, linkedFileIds))
                .thenReturn(List.of(21L));

        List<ProjectFileMappingDTO> files = service.listProjectFiles(7L, 100L);

        assertEquals(List.of(22L, 30L), files.stream().map(ProjectFileMappingDTO::getId).toList());
        assertEquals(455L, files.get(0).getDocumentId());
        assertEquals(DocumentType.MARKDOWN, files.get(0).getDocumentType());
    }

    @Test
    void uploadReusesAvailableFileWhenObjectStillExists() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = newService(r2Client, objectStorageFileRepository);

        byte[] bytes = "hello cache".getBytes(StandardCharsets.UTF_8);
        String cacheKey = HashUtils.sha256Hex(bytes);
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "note.txt", "text/plain", bytes);

        ObjectStorageFile existing = new ObjectStorageFile();
        existing.setId(11L);
        existing.setOwnerUserId(7L);
        existing.setSourceType("bytes");
        existing.setSourceKey(cacheKey);
        existing.setObjectKey("dev/files/7/20260327/" + cacheKey + ".txt");
        existing.setPublicDomain("https://assets.example.com");
        existing.setPublicPath(existing.getObjectKey());
        existing.setSizeBytes(123L);
        existing.setStatus("available");
        existing.setDeleted(false);

        when(objectStorageFileRepository.findAvailableByOwnerSourceAndChannel(7L, "bytes", cacheKey, "public"))
                .thenReturn(Optional.of(existing));
        when(r2Client.exists(StorageChannel.PUBLIC, existing.getObjectKey())).thenReturn(true);

        FileUploadCacheResponse response = service.uploadWithBytes(7L, file, null, bytes, false, "user_upload");

        assertEquals(11L, response.getSourceFileId());
        assertEquals(cacheKey, response.getCacheKey());
        assertEquals(123L, response.getFileSize());
        assertEquals(123L, response.getSizeBytes());
        assertEquals("https://assets.example.com/" + existing.getObjectKey(), response.getPublicUrl());
        verify(r2Client, never()).uploadWithMeta(eq(StorageChannel.PUBLIC), anyString(), any(), anyString());
        verify(objectStorageFileRepository, never()).markStale(anyLong());
        verify(objectStorageFileRepository, never()).findByOwnerSourceAndChannel(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void uploadRevivesStaleFileWhenUnderlyingObjectIsMissing() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = newService(r2Client, objectStorageFileRepository);

        byte[] bytes = "hello stale".getBytes(StandardCharsets.UTF_8);
        String cacheKey = HashUtils.sha256Hex(bytes);
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "note.txt", "text/plain", bytes);

        ObjectStorageFile available = new ObjectStorageFile();
        available.setId(21L);
        available.setOwnerUserId(7L);
        available.setSourceType("bytes");
        available.setSourceKey(cacheKey);
        available.setObjectKey("dev/files/7/20260320/" + cacheKey + ".txt");
        available.setPublicDomain("https://assets.example.com");
        available.setPublicPath(available.getObjectKey());
        available.setStatus("available");
        available.setDeleted(false);

        ObjectStorageFile stale = new ObjectStorageFile();
        stale.setId(21L);
        stale.setOwnerUserId(7L);
        stale.setSourceType("bytes");
        stale.setSourceKey(cacheKey);
        stale.setObjectKey(available.getObjectKey());
        stale.setPublicDomain("https://assets.example.com");
        stale.setPublicPath(stale.getObjectKey());
        stale.setStatus("stale");
        stale.setDeleted(false);

        when(objectStorageFileRepository.findAvailableByOwnerSourceAndChannel(7L, "bytes", cacheKey, "public"))
                .thenReturn(Optional.of(available));
        when(objectStorageFileRepository.findByOwnerSourceAndChannel(7L, "bytes", cacheKey, "public"))
                .thenReturn(Optional.of(stale));
        when(r2Client.exists(StorageChannel.PUBLIC, available.getObjectKey())).thenReturn(false);

        R2Client.UploadResult uploadResult = new R2Client.UploadResult(
                "https://assets.example.com/dev/files/7/20260327/" + cacheKey + ".txt",
                "etag-1",
                "v1",
                OffsetDateTime.parse("2026-03-27T10:15:30Z"));
        when(r2Client.uploadWithMeta(eq(StorageChannel.PUBLIC), anyString(), eq(bytes), eq("text/plain")))
                .thenReturn(uploadResult);

        FileUploadCacheResponse response = service.uploadWithBytes(7L, file, null, bytes, false, "user_upload");

        ArgumentCaptor<ObjectStorageFile> updatedCaptor = ArgumentCaptor.forClass(ObjectStorageFile.class);
        verify(objectStorageFileRepository).markStale(21L);
        verify(objectStorageFileRepository).update(updatedCaptor.capture());
        verify(objectStorageFileRepository, never()).insert(any());

        ObjectStorageFile revived = updatedCaptor.getValue();
        assertEquals(21L, revived.getId());
        assertEquals("available", revived.getStatus());
        assertFalse(Boolean.TRUE.equals(revived.getDeleted()));
        assertEquals(cacheKey, revived.getSourceKey());
        assertTrue(revived.getObjectKey().endsWith(cacheKey + ".txt"));

        assertEquals(21L, response.getSourceFileId());
        assertEquals(cacheKey, response.getCacheKey());
        assertEquals(uploadResult.publicUrl(), response.getPublicUrl());
    }

    @Test
    void uploadWithBytesRejectsUnsupportedProjectMaterialType() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = newService(r2Client, objectStorageFileRepository);

        byte[] bytes = "fake mp3 bytes".getBytes(StandardCharsets.UTF_8);
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "song.mp3", "audio/mpeg", bytes);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.uploadWithBytes(7L, file, null, bytes, false, "user_upload")
        );

        assertEquals(ErrorCode.INVALID_FILE_TYPE.getCode(), exception.getCode());
        verify(r2Client, never()).uploadWithMeta(eq(StorageChannel.PUBLIC), anyString(), any(), anyString());
        verify(objectStorageFileRepository, never()).insert(any());
    }

    @Test
    void uploadGeneratedWithBytesUsesGeneratedSourceTypeAndUniqueKey() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        DocumentAutoCreateService documentAutoCreateService = mock(DocumentAutoCreateService.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = new FileServiceImpl(
                r2Client,
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                mock(UserRepository.class),
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                fileAccessGrantRepository,
                documentAutoCreateService,
                mock(DocumentRepository.class),
                null
        );

        byte[] bytes = "# Generated\n".getBytes(StandardCharsets.UTF_8);
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "generated.md", "text/markdown", bytes);
        ObjectStorageFile inserted = new ObjectStorageFile();
        inserted.setId(71L);
        inserted.setSourceKey("generated-key");

        when(projectRepository.findById(100L)).thenReturn(Optional.of(activeOwnedProject(100L, 7L)));
        when(objectStorageFileRepository.findAvailableByOwnerSourceAndChannel(eq(7L), eq("generated"), anyString(), eq("public")))
                .thenReturn(Optional.empty());
        when(objectStorageFileRepository.findByOwnerSourceAndChannel(eq(7L), eq("generated"), anyString(), eq("public")))
                .thenReturn(Optional.empty());
        when(r2Client.uploadWithMeta(eq(StorageChannel.PUBLIC), anyString(), eq(bytes), eq("text/markdown")))
                .thenReturn(new R2Client.UploadResult(
                        "https://assets.example.com/dev/generated/7/20260327/generated.md",
                        "etag-generated",
                        null,
                        OffsetDateTime.parse("2026-03-27T12:15:30Z")));
        when(objectStorageFileRepository.insert(any())).thenReturn(inserted);
        when(documentAutoCreateService.ensureCreatedForUploadedMarkdown(eq(7L), eq(100L), eq(71L), eq("generated.md"), any()))
                .thenReturn(81L);

        FileUploadCacheResponse response = service.uploadGeneratedWithBytes(7L, file, 100L, bytes);

        ArgumentCaptor<ObjectStorageFile> insertCaptor = ArgumentCaptor.forClass(ObjectStorageFile.class);
        verify(objectStorageFileRepository).insert(insertCaptor.capture());
        ObjectStorageFile generated = insertCaptor.getValue();
        assertEquals("generated", generated.getSourceType());
        assertNotEquals(HashUtils.sha256Hex(bytes), generated.getSourceKey());
        assertTrue(generated.getObjectKey().contains("/generated/"));
        verify(projectFileLinkRepository).upsertLink(100L, 71L, 7L, "generated.md", "document");
        verify(fileAccessGrantRepository).upsertLink(7L, 71L, 100L);
        assertEquals(81L, response.getDocumentId());
    }

    @Test
    void uploadGeneratedWithBytesRethrowsDatabaseFailureFromAutoCreateDocument() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        DocumentAutoCreateService documentAutoCreateService = mock(DocumentAutoCreateService.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = new FileServiceImpl(
                r2Client,
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                mock(UserRepository.class),
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                fileAccessGrantRepository,
                documentAutoCreateService,
                mock(DocumentRepository.class),
                null
        );

        byte[] bytes = "# Generated\n".getBytes(StandardCharsets.UTF_8);
        InMemoryMultipartFile file = new InMemoryMultipartFile("file", "generated.md", "text/markdown", bytes);
        ObjectStorageFile inserted = new ObjectStorageFile();
        inserted.setId(71L);
        inserted.setSourceKey("generated-key");

        when(projectRepository.findById(100L)).thenReturn(Optional.of(activeOwnedProject(100L, 7L)));
        when(objectStorageFileRepository.findAvailableByOwnerSourceAndChannel(eq(7L), eq("generated"), anyString(), eq("public")))
                .thenReturn(Optional.empty());
        when(objectStorageFileRepository.findByOwnerSourceAndChannel(eq(7L), eq("generated"), anyString(), eq("public")))
                .thenReturn(Optional.empty());
        when(r2Client.uploadWithMeta(eq(StorageChannel.PUBLIC), anyString(), eq(bytes), eq("text/markdown")))
                .thenReturn(new R2Client.UploadResult(
                        "https://assets.example.com/dev/generated/7/20260327/generated.md",
                        "etag-generated",
                        null,
                        OffsetDateTime.parse("2026-03-27T12:15:30Z")));
        when(objectStorageFileRepository.insert(any())).thenReturn(inserted);
        when(documentAutoCreateService.ensureCreatedForUploadedMarkdown(eq(7L), eq(100L), eq(71L), eq("generated.md"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate document version"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.uploadGeneratedWithBytes(7L, file, 100L, bytes)
        );
    }





    @Test
    void addFileToProjectResolvesPublishCacheKey() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FileServiceImpl service = new FileServiceImpl(
                mock(R2Client.class),
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                mock(UserRepository.class),
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                mock(FileAccessGrantRepository.class),
                mock(DocumentAutoCreateService.class),
                mock(DocumentRepository.class),
                null
        );

        String cacheKey = HashUtils.sha256Hex("published artifact".getBytes(StandardCharsets.UTF_8));
        ObjectStorageFile publishFile = new ObjectStorageFile();
        publishFile.setId(31L);
        publishFile.setOwnerUserId(7L);
        publishFile.setSourceType("publish");
        publishFile.setSourceKey(cacheKey);
        publishFile.setFileExt("md");
        publishFile.setMimeType("text/markdown");
        publishFile.setOriginalName("published.md");

        when(projectRepository.findById(88L)).thenReturn(Optional.of(activeOwnedProject(88L, 7L)));
        when(objectStorageFileRepository.findAvailableByOwnerAndSource(7L, "bytes", cacheKey)).thenReturn(Optional.empty());
        when(objectStorageFileRepository.findAvailableByOwnerAndSource(7L, "publish", cacheKey))
                .thenReturn(Optional.of(publishFile));

        boolean added = service.addFileToProject(7L, 88L, cacheKey, null, null);

        assertTrue(added);
        verify(projectFileLinkRepository).upsertLink(88L, 31L, 7L, "published.md", "document");
        verify(projectRepository).updateLastActiveAt(eq(88L), any());
        verify(objectStorageFileRepository).findAvailableByOwnerAndSource(7L, "bytes", cacheKey);
        verify(objectStorageFileRepository).findAvailableByOwnerAndSource(7L, "publish", cacheKey);
        verify(objectStorageFileRepository, never()).findAvailableByOwnerAndSource(7L, "copy", cacheKey);
    }

    @Test
    void removeFileFromProjectUsesProjectScopedLinkCacheKey() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FileServiceImpl service = new FileServiceImpl(
                mock(R2Client.class),
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                mock(UserRepository.class),
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                mock(FileAccessGrantRepository.class),
                mock(DocumentAutoCreateService.class),
                mock(DocumentRepository.class),
                null
        );

        String cacheKey = HashUtils.sha256Hex("published artifact".getBytes(StandardCharsets.UTF_8));
        when(projectRepository.findById(99L)).thenReturn(Optional.of(activeOwnedProject(99L, 7L)));
        when(projectFileLinkRepository.findActiveFileIdByProjectAndCacheKey(99L, cacheKey)).thenReturn(Optional.of(41L));
        when(projectFileLinkRepository.softDeleteLink(99L, 41L)).thenReturn(true);

        boolean removed = service.removeFileFromProject(7L, 99L, cacheKey);

        assertTrue(removed);
        verify(projectFileLinkRepository).findActiveFileIdByProjectAndCacheKey(99L, cacheKey);
        verify(projectFileLinkRepository).softDeleteLink(99L, 41L);
    }

    @Test
    void purgeProjectFileBlocksWhenActiveDocumentStillReferencesFile() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = newServiceWithPurge(
                r2Client,
                objectStorageFileRepository,
                mock(ProjectFileLinkRepository.class),
                projectRepository,
                filePurgeRepository);

        ObjectStorageFile file = activeFile(51L, 7L, "dev/files/7/20260508/source.md");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(activeOwnedProject(100L, 7L)));
        when(filePurgeRepository.findOwnedFileForUpdate(7L, 51L)).thenReturn(Optional.of(file));
        when(filePurgeRepository.existsProjectHistoryLink(100L, 51L)).thenReturn(true);
        when(filePurgeRepository.collectUsageSnapshot(51L))
                .thenReturn(new FilePurgeRepository.FilePurgeUsageSnapshot(0, 1, 0, 0, 0));

        FilePurgeRequest request = new FilePurgeRequest();
        request.setConfirm(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.purgeProjectFile(7L, 100L, 51L, request));

        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), exception.getCode());
        verify(filePurgeRepository, never()).hardDeleteFileRecord(anyLong(), anyLong());
        verify(r2Client, never()).delete(any(StorageChannel.class), anyString());
    }

    @Test
    void purgeProjectFileHardDeletesRecordAndManagedObjects() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FilePurgeRepository filePurgeRepository = mock(FilePurgeRepository.class);
        R2Client r2Client = mock(R2Client.class);
        FileServiceImpl service = newServiceWithPurge(
                r2Client,
                objectStorageFileRepository,
                mock(ProjectFileLinkRepository.class),
                projectRepository,
                filePurgeRepository);

        ObjectStorageFile file = activeFile(51L, 7L, "dev/files/7/20260508/source.md");
        when(projectRepository.findById(100L)).thenReturn(Optional.of(activeOwnedProject(100L, 7L)));
        when(filePurgeRepository.findOwnedFileForUpdate(7L, 51L)).thenReturn(Optional.of(file));
        when(filePurgeRepository.existsProjectHistoryLink(100L, 51L)).thenReturn(true);
        when(filePurgeRepository.collectUsageSnapshot(51L))
                .thenReturn(new FilePurgeRepository.FilePurgeUsageSnapshot(0, 0, 0, 0, 0));
        when(filePurgeRepository.listDerivativeObjectKeys(51L))
                .thenReturn(List.of("dev/derivatives/51/extracted.txt", "dev/files/7/20260508/source.md"));
        when(filePurgeRepository.hardDeleteFileRecord(7L, 51L)).thenReturn(true);

        FilePurgeRequest request = new FilePurgeRequest();
        request.setConfirm(true);
        request.setExpectedCacheKey(file.getSourceKey());

        FilePurgeResponse response = service.purgeProjectFile(7L, 100L, 51L, request);

        assertTrue(Boolean.TRUE.equals(response.getPurged()));
        assertEquals("purged", response.getStatus());
        assertEquals(2, response.getScheduledObjectDeleteCount());
        assertTrue(Boolean.TRUE.equals(response.getPreview().getPurgeable()));
        verify(filePurgeRepository).revokeAccessGrants(51L);
        verify(filePurgeRepository).softDeleteTextFragments(51L);
        verify(filePurgeRepository).softDeleteDerivatives(51L);
        verify(filePurgeRepository).deleteInactiveReferencesForHardDelete(51L);
        verify(filePurgeRepository).hardDeleteFileRecord(7L, 51L);
        verify(r2Client).delete(StorageChannel.PUBLIC, "dev/files/7/20260508/source.md");
        verify(r2Client).delete(StorageChannel.PUBLIC, "dev/derivatives/51/extracted.txt");
    }

    @Test
    void duplicateProjectFileCreatesIndependentCopyFileAndAppendsMissingExtension() {
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        FileDerivativeRepository fileDerivativeRepository = mock(FileDerivativeRepository.class);
        FileTextFragmentRepository fileTextFragmentRepository = mock(FileTextFragmentRepository.class);
        FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        DocumentAutoCreateService documentAutoCreateService = mock(DocumentAutoCreateService.class);
        R2Client r2Client = mock(R2Client.class);

        FileServiceImpl service = new FileServiceImpl(
                r2Client,
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                mock(UserRepository.class),
                fileDerivativeRepository,
                fileTextFragmentRepository,
                fileAccessGrantRepository,
                documentAutoCreateService,
                mock(DocumentRepository.class),
                null
        );

        byte[] bytes = "# Title\n\nHello".getBytes(StandardCharsets.UTF_8);
        ObjectStorageFile source = new ObjectStorageFile();
        source.setId(51L);
        source.setOwnerUserId(7L);
        source.setSourceType("bytes");
        source.setSourceKey(HashUtils.sha256Hex(bytes));
        source.setObjectKey("dev/files/7/20260327/source.md");
        source.setMimeType("text/markdown");
        source.setOriginalName("file1.md");
        source.setStatus("available");
        source.setDeleted(false);

        ProjectFileLinkView sourceView = new ProjectFileLinkView();
        sourceView.setFileId(51L);
        sourceView.setDisplayName("file1.md");

        ObjectStorageFile inserted = new ObjectStorageFile();
        inserted.setId(61L);

        when(projectRepository.findById(100L)).thenReturn(Optional.of(activeOwnedProject(100L, 7L)));
        when(projectFileLinkRepository.listProjectFiles(100L)).thenReturn(List.of(sourceView));
        when(objectStorageFileRepository.findById(51L)).thenReturn(Optional.of(source));
        when(r2Client.downloadBytes(StorageChannel.PUBLIC, source.getObjectKey())).thenReturn(bytes);
        when(objectStorageFileRepository.findAvailableByOwnerSourceAndChannel(eq(7L), eq("copy"), anyString(), eq("public")))
                .thenReturn(Optional.empty());
        when(objectStorageFileRepository.findByOwnerSourceAndChannel(eq(7L), eq("copy"), anyString(), eq("public")))
                .thenReturn(Optional.empty());
        when(r2Client.uploadWithMeta(eq(StorageChannel.PUBLIC), anyString(), eq(bytes), eq("text/markdown")))
                .thenReturn(new R2Client.UploadResult(
                        "https://assets.example.com/dev/files/7/20260327/duplicate.md",
                        "etag-dup",
                        "v2",
                        OffsetDateTime.parse("2026-03-27T11:15:30Z")));
        when(objectStorageFileRepository.insert(any())).thenReturn(inserted);
        when(documentAutoCreateService.ensureCreatedForUploadedMarkdown(eq(7L), eq(100L), eq(61L), eq("file1(1).md"), any()))
                .thenReturn(77L);

        FileUploadCacheResponse response = service.duplicateProjectFile(7L, 100L, 51L, "file1(1)");

        ArgumentCaptor<ObjectStorageFile> insertCaptor = ArgumentCaptor.forClass(ObjectStorageFile.class);
        verify(objectStorageFileRepository).insert(insertCaptor.capture());
        verify(projectFileLinkRepository).upsertLink(100L, 61L, 7L, "file1(1).md", "document");
        verify(fileAccessGrantRepository).upsertLink(7L, 61L, 100L);
        verify(documentAutoCreateService).ensureCreatedForUploadedMarkdown(eq(7L), eq(100L), eq(61L), eq("file1(1).md"), any());
        verify(fileDerivativeRepository).upsertExtractedText(eq(61L), anyString(), any());
        verify(fileTextFragmentRepository).upsertFragments(eq(61L), any(), eq("md"));

        ObjectStorageFile duplicated = insertCaptor.getValue();
        assertEquals("copy", duplicated.getSourceType());
        assertEquals(7L, duplicated.getOwnerUserId());
        assertNotNull(duplicated.getSourceKey());
        assertNotEquals(source.getSourceKey(), duplicated.getSourceKey());
        assertTrue(duplicated.getObjectKey().contains(duplicated.getSourceKey()));
        assertEquals("file1(1).md", duplicated.getOriginalName());

        assertEquals(61L, response.getSourceFileId());
        assertEquals("file1(1).md", response.getFileName());
        assertEquals(77L, response.getDocumentId());
        assertEquals("text/markdown", response.getMimeType());
        assertNotEquals(source.getSourceKey(), response.getCacheKey());
    }

    private FileServiceImpl newService(R2Client r2Client, ObjectStorageFileRepository objectStorageFileRepository) {
        return new FileServiceImpl(
                r2Client,
                sampleR2Properties(),
                objectStorageFileRepository,
                mock(ProjectFileLinkRepository.class),
                mock(ProjectRepository.class),
                mock(UserRepository.class),
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                mock(FileAccessGrantRepository.class),
                mock(DocumentAutoCreateService.class),
                mock(DocumentRepository.class),
                null
        );
    }

    private FileServiceImpl newServiceWithPurge(R2Client r2Client,
            ObjectStorageFileRepository objectStorageFileRepository,
            ProjectFileLinkRepository projectFileLinkRepository,
            ProjectRepository projectRepository,
            FilePurgeRepository filePurgeRepository) {
        return new FileServiceImpl(
                r2Client,
                sampleR2Properties(),
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                mock(UserRepository.class),
                mock(FileDerivativeRepository.class),
                mock(FileTextFragmentRepository.class),
                mock(FileAccessGrantRepository.class),
                filePurgeRepository,
                mock(DocumentAutoCreateService.class),
                mock(DocumentRepository.class),
                null
        );
    }

    private StorageR2Properties sampleR2Properties() {
        StorageR2Properties r2Properties = new StorageR2Properties();
        r2Properties.setBucket("paperpuppy");
        r2Properties.setPublicDomain("https://assets.example.com");
        r2Properties.setPathPrefix("dev");
        return r2Properties;
    }

    private ProjectFileLinkView projectFile(Long fileId, String displayName) {
        ProjectFileLinkView view = new ProjectFileLinkView();
        view.setProjectId(100L);
        view.setFileId(fileId);
        view.setCacheKey("cache-" + fileId);
        view.setDisplayName(displayName);
        view.setSizeBytes(128L);
        return view;
    }

    private Project activeOwnedProject(Long projectId, Long ownerId) {
        Project project = new Project();
        project.setId(projectId);
        project.setOwnerId(ownerId);
        project.setDeleted(false);
        return project;
    }

    private ObjectStorageFile activeFile(Long fileId, Long ownerUserId, String objectKey) {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(fileId);
        file.setOwnerUserId(ownerUserId);
        file.setSourceType("bytes");
        file.setSourceKey(HashUtils.sha256Hex(("file-" + fileId).getBytes(StandardCharsets.UTF_8)));
        file.setObjectKey(objectKey);
        file.setPublicDomain("https://assets.example.com");
        file.setPublicPath(objectKey);
        file.setMimeType("text/markdown");
        file.setOriginalName("source.md");
        file.setSizeBytes(128L);
        file.setStatus("available");
        file.setDeleted(false);
        return file;
    }
}
