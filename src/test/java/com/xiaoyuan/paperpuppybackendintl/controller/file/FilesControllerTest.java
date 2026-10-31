package com.xiaoyuan.paperpuppybackendintl.controller.file;

import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadBatchResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PendingProjectFileFinalizeRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PendingProjectFileUploadResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PrivateObjectAccessUrlResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.PrivateObjectResourceTokenResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.RecentProjectFileDTO;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.service.file.PendingProjectFileService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectAccessUrlService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageFileAccessService;
import com.xiaoyuan.paperpuppybackendintl.utils.file.InMemoryMultipartFile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FilesControllerTest {

    @Test
    void recentFilesDelegatesToFileService() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        RecentProjectFileDTO recentFile = new RecentProjectFileDTO();
        recentFile.setFileId(21L);
        recentFile.setFileName("Brief.pdf");
        when(fileService.listRecentUserUploadedFiles(7L, 12)).thenReturn(List.of(recentFile));

        BaseResponse<List<RecentProjectFileDTO>> response = controller.recentFiles(12, 7L);

        assertEquals(0, response.getCode());
        assertEquals(21L, response.getData().get(0).getFileId());
        verify(fileService).listRecentUserUploadedFiles(7L, 12);
    }

    @Test
    void uploadPendingProjectFileAllowsImagesAndDelegates() {
        PendingProjectFileService pendingProjectFileService = mock(PendingProjectFileService.class);
        FilesController controller = newController(mock(FileService.class), pendingProjectFileService);
        InMemoryMultipartFile image = new InMemoryMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        PendingProjectFileUploadResponse uploaded = new PendingProjectFileUploadResponse();
        uploaded.setPendingFileRef("pending-1");
        uploaded.setFileName("cover.png");
        when(pendingProjectFileService.upload(7L, image)).thenReturn(uploaded);

        BaseResponse<PendingProjectFileUploadResponse> response = controller.uploadPendingProjectFile(image, 7L);

        assertEquals(0, response.getCode());
        assertEquals("pending-1", response.getData().getPendingFileRef());
        verify(pendingProjectFileService).upload(7L, image);
    }

    @Test
    void finalizePendingProjectFileDelegates() {
        PendingProjectFileService pendingProjectFileService = mock(PendingProjectFileService.class);
        FilesController controller = newController(mock(FileService.class), pendingProjectFileService);
        PendingProjectFileFinalizeRequest request = new PendingProjectFileFinalizeRequest();
        request.setPendingFileRef("pending-1");
        request.setProjectId(100L);
        FileUploadCacheResponse finalized = new FileUploadCacheResponse();
        finalized.setSourceFileId(12L);
        when(pendingProjectFileService.finalizeUpload(7L, request)).thenReturn(finalized);

        BaseResponse<FileUploadCacheResponse> response = controller.finalizePendingProjectFile(request, 7L);

        assertEquals(0, response.getCode());
        assertEquals(12L, response.getData().getSourceFileId());
        verify(pendingProjectFileService).finalizeUpload(7L, request);
    }

    @Test
    void fileAccessUrlDelegatesToApplicationScopedService() {
        ObjectStorageFileAccessService objectStorageFileAccessService = mock(ObjectStorageFileAccessService.class);
        FilesController controller = newController(
                mock(FileService.class),
                mock(PendingProjectFileService.class),
                mock(ObjectAccessUrlService.class),
                objectStorageFileAccessService
        );
        PrivateObjectAccessUrlResponse signed = new PrivateObjectAccessUrlResponse();
        signed.setFileId(51L);
        signed.setObjectKey("dev/files/7/a.pdf");
        signed.setAccessUrl("https://file.example.com/dev/files/7/a.pdf?rt=token");
        signed.setScope("private");
        signed.setTtlSeconds(300);
        when(objectStorageFileAccessService.fileAccessUrl(7L, 51L, 100L, 300L)).thenReturn(signed);

        BaseResponse<PrivateObjectAccessUrlResponse> response = controller.fileAccessUrl(51L, 100L, 300L, 7L);

        assertEquals(0, response.getCode());
        assertEquals(51L, response.getData().getFileId());
        assertEquals("https://file.example.com/dev/files/7/a.pdf?rt=token", response.getData().getAccessUrl());
        verify(objectStorageFileAccessService).fileAccessUrl(7L, 51L, 100L, 300L);
    }

    @Test
    void privateResourceTokenReturnsUserScopedGrant() {
        ObjectAccessUrlService objectAccessUrlService = mock(ObjectAccessUrlService.class);
        ObjectStorageFileAccessService objectStorageFileAccessService = mock(ObjectStorageFileAccessService.class);
        FilesController controller = newController(
                mock(FileService.class),
                mock(PendingProjectFileService.class),
                objectAccessUrlService,
                objectStorageFileAccessService
        );
        List<String> prefixes = List.of("dev/pdf-assets/7/", "dev/docx-assets/7/");
        when(objectStorageFileAccessService.validateAccessUrlTtl(null)).thenReturn(600L);
        when(objectAccessUrlService.privateUserResourceToken(7L, Duration.ofSeconds(600)))
                .thenReturn(new ObjectAccessUrlService.ResourceTokenGrant(
                        "https://file.example.com",
                        "https://img.example.com",
                        "rt-token",
                        "private",
                        prefixes,
                        1780000000L,
                        600L));

        BaseResponse<PrivateObjectResourceTokenResponse> response = controller.privateResourceToken(null, 7L);

        assertEquals(0, response.getCode());
        assertEquals("https://file.example.com", response.getData().getFileBaseUrl());
        assertEquals("https://img.example.com", response.getData().getImageBaseUrl());
        assertEquals("rt-token", response.getData().getResourceToken());
        assertEquals(prefixes, response.getData().getPrefixes());
        assertEquals(1780000000L, response.getData().getExpiresAt());
        assertEquals(600L, response.getData().getTtlSeconds());
    }

    @Test
    void uploadFileAllowsExcelByMimeType() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "budget.bin",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setFileName("budget.bin");
        uploaded.setCacheKey("cache_excel_mime");
        when(fileService.upload(7L, file, 100L, false, null)).thenReturn(uploaded);

        BaseResponse<FileUploadCacheResponse> response = controller.uploadFile(file, 100L, false, null, 7L);

        assertEquals(0, response.getCode());
        assertEquals("budget.bin", response.getData().getFileName());
        verify(fileService).upload(7L, file, 100L, false, null);
    }

    @Test
    void uploadFileAllowsExcelByExtensionFallback() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "budget.xlsx",
                null,
                new byte[]{1, 2, 3}
        );
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setFileName("budget.xlsx");
        uploaded.setCacheKey("cache_excel_extension");
        when(fileService.upload(7L, file, 100L, false, null)).thenReturn(uploaded);

        BaseResponse<FileUploadCacheResponse> response = controller.uploadFile(file, 100L, false, null, 7L);

        assertEquals(0, response.getCode());
        assertEquals("budget.xlsx", response.getData().getFileName());
        verify(fileService).upload(7L, file, 100L, false, null);
    }

    @Test
    void uploadFileStillAllowsPdf() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "paper.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setFileName("paper.pdf");
        uploaded.setCacheKey("cache_123");
        when(fileService.upload(7L, file, 100L, false, null)).thenReturn(uploaded);

        BaseResponse<FileUploadCacheResponse> response = controller.uploadFile(file, 100L, false, null, 7L);

        assertEquals(0, response.getCode());
        assertEquals("paper.pdf", response.getData().getFileName());
        verify(fileService).upload(7L, file, 100L, false, null);
    }

    @Test
    void uploadFilePassesPdfImageExtractionSwitch() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "paper.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setFileName("paper.pdf");
        when(fileService.upload(7L, file, 100L, true, null)).thenReturn(uploaded);

        BaseResponse<FileUploadCacheResponse> response = controller.uploadFile(file, 100L, true, null, 7L);

        assertEquals(0, response.getCode());
        assertEquals("paper.pdf", response.getData().getFileName());
        verify(fileService).upload(7L, file, 100L, true, null);
    }

    @Test
    void uploadFilePassesUploadSourceHint() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "generated.json",
                "application/json",
                new byte[]{1, 2, 3}
        );
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setFileName("generated.json");
        when(fileService.upload(7L, file, 100L, false, "generated")).thenReturn(uploaded);

        BaseResponse<FileUploadCacheResponse> response = controller.uploadFile(file, 100L, false, "generated", 7L);

        assertEquals(0, response.getCode());
        assertEquals("generated.json", response.getData().getFileName());
        verify(fileService).upload(7L, file, 100L, false, "generated");
    }

    @Test
    void uploadFileRejectsRetiredDeckPackages() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        for (String contentType : List.of(
                "application/vnd.deckwise.deck+zip",
                "application/json",
                "text/plain")) {
            InMemoryMultipartFile file = new InMemoryMultipartFile(
                    "file",
                    "portable.deck",
                    contentType,
                    new byte[]{1, 2, 3}
            );
            assertThrows(BusinessException.class,
                    () -> controller.uploadFile(file, 100L, false, null, 7L));
        }

        verifyNoInteractions(fileService);
    }

    @Test
    void uploadFileRejectsInternalAndDeprecatedOutlineNames() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);

        for (String contentType : List.of("application/json", "text/plain")) {
            for (String fileName : List.of(
                    "plan.deck-outline.json",
                    "plan.deck.outline",
                    "plan.mslides.outline")) {
                InMemoryMultipartFile file = new InMemoryMultipartFile(
                        "file",
                        fileName,
                        contentType,
                        new byte[]{1, 2, 3}
                );
                assertThrows(BusinessException.class,
                        () -> controller.uploadFile(file, 100L, false, null, 7L));
            }
        }

        verifyNoInteractions(fileService);
    }

    @Test
    void uploadFileAcceptsOrdinaryJson() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file", "data.json", "application/json", new byte[]{1, 2, 3});
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setFileName("data.json");
        when(fileService.upload(7L, file, 100L, false, null)).thenReturn(uploaded);

        BaseResponse<FileUploadCacheResponse> response = controller.uploadFile(file, 100L, false, null, 7L);

        assertEquals(0, response.getCode());
        verify(fileService).upload(7L, file, 100L, false, null);
    }

    @Test
    void uploadFileRejectsLegacyMSlidesExtension() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "file",
                "legacy.mslides",
                "application/vnd.paperpuppy.mslides+json",
                new byte[]{1, 2, 3}
        );
        assertThrows(BusinessException.class,
                () -> controller.uploadFile(file, 100L, false, null, 7L));
        verifyNoInteractions(fileService);
    }

    @Test
    void uploadFilesAllowsMixedImageAndDocumentBatch() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile image = new InMemoryMultipartFile(
                "files",
                "cover.png",
                "image/png",
                new byte[]{1}
        );
        InMemoryMultipartFile pdf = new InMemoryMultipartFile(
                "files",
                "brief.pdf",
                "application/pdf",
                new byte[]{2}
        );
        FileUploadCacheResponse uploadedImage = new FileUploadCacheResponse();
        uploadedImage.setFileName("cover.png");
        uploadedImage.setSourceFileId(11L);
        FileUploadCacheResponse uploadedPdf = new FileUploadCacheResponse();
        uploadedPdf.setFileName("brief.pdf");
        uploadedPdf.setSourceFileId(12L);
        when(fileService.upload(7L, image, 100L, false, null)).thenReturn(uploadedImage);
        when(fileService.upload(7L, pdf, 100L, false, null)).thenReturn(uploadedPdf);

        BaseResponse<FileUploadBatchResponse> response = controller.uploadFiles(List.of(image, pdf), 100L, false, null, 7L);

        assertEquals(0, response.getCode());
        assertEquals(100L, response.getData().getProjectId());
        assertEquals(2, response.getData().getTotalCount());
        assertEquals("cover.png", response.getData().getFiles().get(0).getFileName());
        assertEquals("brief.pdf", response.getData().getFiles().get(1).getFileName());
        verify(fileService).upload(7L, image, 100L, false, null);
        verify(fileService).upload(7L, pdf, 100L, false, null);
    }

    @Test
    void uploadImagePassesPrivateStorageOptions() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile image = new InMemoryMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{1}
        );
        FileUploadCacheResponse uploaded = new FileUploadCacheResponse();
        uploaded.setFileName("cover.png");
        uploaded.setStorageChannel("private");
        uploaded.setVisibility("private");
        when(fileService.uploadProjectImage(7L, image, 100L, "user_upload", "private", "private"))
                .thenReturn(uploaded);

        BaseResponse<FileUploadCacheResponse> response = controller.uploadImage(
                image,
                100L,
                "user_upload",
                "private",
                "private",
                7L);

        assertEquals(0, response.getCode());
        assertEquals("private", response.getData().getStorageChannel());
        assertEquals("private", response.getData().getVisibility());
        verify(fileService).uploadProjectImage(7L, image, 100L, "user_upload", "private", "private");
    }

    @Test
    void uploadFilesRejectsUnsupportedFileBeforeUpload() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile unsupported = new InMemoryMultipartFile(
                "files",
                "archive.zip",
                "application/zip",
                new byte[]{1, 2, 3}
        );

        assertThrows(BusinessException.class, () -> controller.uploadFiles(List.of(unsupported), 100L, false, null, 7L));

        verifyNoInteractions(fileService);
    }

    @Test
    void uploadFilesRequiresProjectId() {
        FileService fileService = mock(FileService.class);
        FilesController controller = newController(fileService);
        InMemoryMultipartFile file = new InMemoryMultipartFile(
                "files",
                "brief.pdf",
                "application/pdf",
                new byte[]{1}
        );

        assertThrows(BusinessException.class, () -> controller.uploadFiles(List.of(file), null, false, null, 7L));

        verifyNoInteractions(fileService);
    }

    private FilesController newController(FileService fileService) {
        return newController(fileService, mock(PendingProjectFileService.class));
    }

    private FilesController newController(FileService fileService, PendingProjectFileService pendingProjectFileService) {
        return newController(fileService, pendingProjectFileService, mock(ObjectAccessUrlService.class));
    }

    private FilesController newController(FileService fileService,
                                          PendingProjectFileService pendingProjectFileService,
                                          ObjectAccessUrlService objectAccessUrlService) {
        return newController(
                fileService,
                pendingProjectFileService,
                objectAccessUrlService,
                mock(ObjectStorageFileAccessService.class));
    }

    private FilesController newController(FileService fileService,
                                          PendingProjectFileService pendingProjectFileService,
                                          ObjectAccessUrlService objectAccessUrlService,
                                          ObjectStorageFileAccessService objectStorageFileAccessService) {
        return new FilesController(
                fileService,
                pendingProjectFileService,
                objectAccessUrlService,
                objectStorageFileAccessService
        );
    }
}
