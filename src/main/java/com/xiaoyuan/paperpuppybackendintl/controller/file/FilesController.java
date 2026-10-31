package com.xiaoyuan.paperpuppybackendintl.controller.file;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 对齐旧项目：/files/upload
 */
@Slf4j
@RestController
@RequestMapping("/files")
@AuthCheck
public class FilesController {

    private static final int MAX_BATCH_UPLOAD_FILES = 20;
    private final FileService fileService;
    private final PendingProjectFileService pendingProjectFileService;
    private final ObjectAccessUrlService objectAccessUrlService;
    private final ObjectStorageFileAccessService objectStorageFileAccessService;

    public FilesController(FileService fileService,
                           PendingProjectFileService pendingProjectFileService,
                           ObjectAccessUrlService objectAccessUrlService,
                           ObjectStorageFileAccessService objectStorageFileAccessService) {
        this.fileService = fileService;
        this.pendingProjectFileService = pendingProjectFileService;
        this.objectAccessUrlService = objectAccessUrlService;
        this.objectStorageFileAccessService = objectStorageFileAccessService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<FileUploadCacheResponse> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "extractPdfImages", required = false, defaultValue = "false") boolean extractPdfImages,
            @RequestParam(value = "uploadSource", required = false) String uploadSource,
            @CurrentUserId Long userId
    ) {
        validateUploadFile(file, false);
        return ResultUtils.success(fileService.upload(userId, file, projectId, extractPdfImages, uploadSource));
    }

    /**
     * 批量上传项目文件。
     * <p>
     * 面向同一项目的一组文件上传请求，支持图片和普通项目文件混合上传。当前仍复用同步单文件上传 pipeline；
     * batchId 先作为批次标识返回，后续可平滑接入批次级限流、审计或异步上传队列。
     */
    @PostMapping(path = "/upload/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<FileUploadBatchResponse> uploadFiles(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("projectId") Long projectId,
            @RequestParam(value = "extractPdfImages", required = false, defaultValue = "false") boolean extractPdfImages,
            @RequestParam(value = "uploadSource", required = false) String uploadSource,
            @CurrentUserId Long userId
    ) {
        validateProjectId(projectId);
        List<MultipartFile> uploadFiles = validateBatchUploadFiles(files);

        List<FileUploadCacheResponse> uploaded = new ArrayList<>(uploadFiles.size());
        for (MultipartFile file : uploadFiles) {
            uploaded.add(fileService.upload(userId, file, projectId, extractPdfImages, uploadSource));
        }

        FileUploadBatchResponse response = new FileUploadBatchResponse();
        response.setBatchId(UUID.randomUUID().toString());
        response.setProjectId(projectId);
        response.setTotalCount(uploaded.size());
        response.setFiles(uploaded);
        return ResultUtils.success(response);
    }

    /**
     * 图片上传（用于多模态/项目资源）：上传到 R2 并写入 DB。
     * <p>
     * projectId 必填，文件将加入项目并创建永久授权。
     * <p>
     * 返回结构复用 /files/upload（cacheKey + accessUrl + 对象存储元数据）。
     */
    @PostMapping(path = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<FileUploadCacheResponse> uploadImage(@RequestPart("file") MultipartFile file,
                                                              @RequestParam("projectId") Long projectId,
                                                              @RequestParam(value = "uploadSource", required = false) String uploadSource,
                                                              @RequestParam(value = "storageChannel", required = false) String storageChannel,
                                                              @RequestParam(value = "visibility", required = false) String visibility,
                                                              @CurrentUserId Long userId) {
        String ct = file != null ? file.getContentType() : null;
        if (ct == null || !ct.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(
                    ErrorCode.INVALID_FILE_TYPE,
                    "仅支持上传图片文件");
        }
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "projectId 不能为空");
        }
        return ResultUtils.success(fileService.uploadProjectImage(
                userId,
                file,
                projectId,
                uploadSource,
                storageChannel,
                visibility));
    }

    @GetMapping("/recent")
    public BaseResponse<List<RecentProjectFileDTO>> recentFiles(
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
            @CurrentUserId Long userId) {
        return ResultUtils.success(fileService.listRecentUserUploadedFiles(userId, limit));
    }

    @GetMapping("/{fileId}/access-url")
    public BaseResponse<PrivateObjectAccessUrlResponse> fileAccessUrl(
            @PathVariable("fileId") Long fileId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "ttlSeconds", required = false) Long ttlSeconds,
            @CurrentUserId Long userId) {
        return ResultUtils.success(objectStorageFileAccessService.fileAccessUrl(
                userId,
                fileId,
                projectId,
                ttlSeconds));
    }

    @GetMapping("/private-resource-token")
    public BaseResponse<PrivateObjectResourceTokenResponse> privateResourceToken(
            @RequestParam(value = "ttlSeconds", required = false) Long ttlSeconds,
            @CurrentUserId Long userId) {
        long effectiveTtlSeconds = objectStorageFileAccessService.validateAccessUrlTtl(ttlSeconds);
        ObjectAccessUrlService.ResourceTokenGrant grant = objectAccessUrlService.privateUserResourceToken(
                userId,
                Duration.ofSeconds(effectiveTtlSeconds));
        if (grant == null || grant.resourceToken() == null || grant.resourceToken().isBlank()) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "private object delivery worker 未启用或资源 token 签发失败");
        }

        PrivateObjectResourceTokenResponse response = new PrivateObjectResourceTokenResponse();
        response.setFileBaseUrl(grant.fileBaseUrl());
        response.setImageBaseUrl(grant.imageBaseUrl());
        response.setResourceToken(grant.resourceToken());
        response.setScope(grant.scope());
        response.setPrefixes(grant.prefixes());
        response.setExpiresAt(grant.expiresAt());
        response.setTtlSeconds(grant.ttlSeconds());
        return ResultUtils.success(response);
    }

    @PostMapping(path = "/pending/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<PendingProjectFileUploadResponse> uploadPendingProjectFile(
            @RequestPart("file") MultipartFile file,
            @CurrentUserId Long userId) {
        validateUploadFile(file, true);
        return ResultUtils.success(pendingProjectFileService.upload(userId, file));
    }

    @PostMapping("/pending/finalize")
    public BaseResponse<FileUploadCacheResponse> finalizePendingProjectFile(
            @RequestBody PendingProjectFileFinalizeRequest request,
            @CurrentUserId Long userId) {
        return ResultUtils.success(pendingProjectFileService.finalizeUpload(userId, request));
    }

    private static List<MultipartFile> validateBatchUploadFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        if (files.size() > MAX_BATCH_UPLOAD_FILES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单次最多上传 " + MAX_BATCH_UPLOAD_FILES + " 个文件");
        }
        for (MultipartFile file : files) {
            validateUploadFile(file, true);
        }
        return files;
    }

    private static void validateProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "projectId 不能为空");
        }
    }

    private static void validateUploadFile(MultipartFile file, boolean allowImage) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        // 支持普通文档和文件上传；批量上传额外支持图片。
        // 注意：浏览器/客户端可能不会发送标准 MIME，因此同时允许按文件后缀兜底判断。
        String contentType = file.getContentType();
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (isRetiredDeckFile(name, contentType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "幻灯片专用文件格式已停止支持");
        }
        boolean isImage = allowImage && isImageUpload(contentType, name);
        boolean isPdf = "application/pdf".equalsIgnoreCase(contentType) || name.endsWith(".pdf");
        boolean isMd = "text/markdown".equalsIgnoreCase(contentType)
                || "text/x-markdown".equalsIgnoreCase(contentType)
                || name.endsWith(".md");
        boolean isJson = "application/json".equalsIgnoreCase(contentType) || name.endsWith(".json");
        boolean isDocx = "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
                || name.endsWith(".docx");
        boolean isXlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equalsIgnoreCase(contentType)
                || name.endsWith(".xlsx");
        boolean isPptx = "application/vnd.openxmlformats-officedocument.presentationml.presentation".equalsIgnoreCase(contentType)
                || name.endsWith(".pptx");
        boolean isTxt = "text/plain".equalsIgnoreCase(contentType) || name.endsWith(".txt");
        if (!isImage && !isPdf && !isMd && !isJson && !isDocx && !isXlsx && !isPptx && !isTxt) {
            String message = allowImage
                    ? "仅支持上传图片 / PDF / Markdown / JSON / Word(.docx) / Excel(.xlsx) / PowerPoint(.pptx) / 纯文本(.txt) 文件"
                    : "仅支持上传 PDF / Markdown / JSON / Word(.docx) / Excel(.xlsx) / PowerPoint(.pptx) / 纯文本(.txt) 文件";
            throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
        }
    }

    private static boolean isImageUpload(String contentType, String lowerName) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return true;
        }
        return lowerName.endsWith(".avif")
                || lowerName.endsWith(".bmp")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".heic")
                || lowerName.endsWith(".heif")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".svg")
                || lowerName.endsWith(".webp");
    }

    private static boolean isRetiredDeckFile(String lowerName, String contentType) {
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".deck")
                || lowerName.endsWith(".deck.json")
                || lowerName.endsWith(".deck-outline.json")
                || lowerName.endsWith(".deck.outline")
                || lowerName.endsWith(".mslides")
                || lowerName.endsWith(".mslides.outline")
                || mime.startsWith("application/vnd.deckwise.")
                || mime.startsWith("application/vnd.paperpuppy.mslides");
    }

}
