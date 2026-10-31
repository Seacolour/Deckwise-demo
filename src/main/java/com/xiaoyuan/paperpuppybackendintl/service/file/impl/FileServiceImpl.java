package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.exception.ThrowUtils;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.*;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ObjectStorageFileSourceType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageVisibility;
import com.xiaoyuan.paperpuppybackendintl.repository.DocumentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileDerivativeRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FilePurgeRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileTextFragmentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.internal.DocumentAutoCreateService;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.service.file.PdfDerivedMediaAssetExtractionService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectAccessUrlService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageFileWriteRequest;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageFileWriteResult;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageFileWriteService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageWriteOptions;
import com.xiaoyuan.paperpuppybackendintl.utils.HashUtils;
import com.xiaoyuan.paperpuppybackendintl.utils.file.InMemoryMultipartFile;
import com.xiaoyuan.paperpuppybackendintl.utils.file.PdfParseResult;
import com.xiaoyuan.paperpuppybackendintl.utils.file.PdfParser;
import com.xiaoyuan.paperpuppybackendintl.utils.file.TokenApproxChunker;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.tinycircl.pandoc4j.Format;
import org.tinycircl.pandoc4j.spring.PandocClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.function.Function;


@Service
@Slf4j
public class FileServiceImpl implements FileService {

    private static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;
    private static final int DEFAULT_RECENT_USER_FILE_LIMIT = 20;
    private static final int MAX_RECENT_USER_FILE_LIMIT = 50;
    private static final String PROJECT_IMAGE_PRIVATE_SCOPE = "uploads";

    private final R2Client r2Client;
    private final StorageR2Properties r2Properties;
    private final ObjectStorageFileRepository objectStorageFileRepository;
    private final ProjectFileLinkRepository projectFileLinkRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FileDerivativeRepository fileDerivativeRepository;
    private final FileTextFragmentRepository fileTextFragmentRepository;
    private final FileAccessGrantRepository fileAccessGrantRepository;
    @Nullable
    private final FilePurgeRepository filePurgeRepository;
    private final DocumentAutoCreateService documentAutoCreateService;
    private final DocumentRepository documentRepository;
    private final ObjectStorageFileWriteService objectStorageFileWriteService;
    @Nullable
    private final ObjectAccessUrlService objectAccessUrlService;
    @Nullable
    private final PdfDerivedMediaAssetExtractionService pdfDerivedMediaAssetExtractionService;
    @Nullable
    private final PandocClient pandocClient;

    public FileServiceImpl(R2Client r2Client,
            StorageR2Properties r2Properties,
            ObjectStorageFileRepository objectStorageFileRepository,
            ProjectFileLinkRepository projectFileLinkRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            FileDerivativeRepository fileDerivativeRepository,
            FileTextFragmentRepository fileTextFragmentRepository,
            FileAccessGrantRepository fileAccessGrantRepository,
            DocumentAutoCreateService documentAutoCreateService,
            DocumentRepository documentRepository,
            @Nullable PandocClient pandocClient) {
        this(r2Client,
                r2Properties,
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                userRepository,
                fileDerivativeRepository,
                fileTextFragmentRepository,
                fileAccessGrantRepository,
                null,
                documentAutoCreateService,
                documentRepository,
                null,
                null,
                pandocClient);
    }

    public FileServiceImpl(R2Client r2Client,
            StorageR2Properties r2Properties,
            ObjectStorageFileRepository objectStorageFileRepository,
            ProjectFileLinkRepository projectFileLinkRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            FileDerivativeRepository fileDerivativeRepository,
            FileTextFragmentRepository fileTextFragmentRepository,
            FileAccessGrantRepository fileAccessGrantRepository,
            FilePurgeRepository filePurgeRepository,
            DocumentAutoCreateService documentAutoCreateService,
            DocumentRepository documentRepository,
            @Nullable PandocClient pandocClient) {
        this(r2Client,
                r2Properties,
                objectStorageFileRepository,
                projectFileLinkRepository,
                projectRepository,
                userRepository,
                fileDerivativeRepository,
                fileTextFragmentRepository,
                fileAccessGrantRepository,
                filePurgeRepository,
                documentAutoCreateService,
                documentRepository,
                null,
                null,
                pandocClient);
    }

    @Autowired
    public FileServiceImpl(R2Client r2Client,
            StorageR2Properties r2Properties,
            ObjectStorageFileRepository objectStorageFileRepository,
            ProjectFileLinkRepository projectFileLinkRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            FileDerivativeRepository fileDerivativeRepository,
            FileTextFragmentRepository fileTextFragmentRepository,
            FileAccessGrantRepository fileAccessGrantRepository,
            FilePurgeRepository filePurgeRepository,
            DocumentAutoCreateService documentAutoCreateService,
            DocumentRepository documentRepository,
            @Nullable ObjectAccessUrlService objectAccessUrlService,
            @Nullable PdfDerivedMediaAssetExtractionService pdfDerivedMediaAssetExtractionService,
            @Nullable PandocClient pandocClient) {
        this.r2Client = r2Client;
        this.r2Properties = r2Properties;
        this.objectStorageFileRepository = objectStorageFileRepository;
        this.projectFileLinkRepository = projectFileLinkRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.fileDerivativeRepository = fileDerivativeRepository;
        this.fileTextFragmentRepository = fileTextFragmentRepository;
        this.fileAccessGrantRepository = fileAccessGrantRepository;
        this.filePurgeRepository = filePurgeRepository;
        this.documentAutoCreateService = documentAutoCreateService;
        this.documentRepository = documentRepository;
        this.objectStorageFileWriteService =
                new ObjectStorageFileWriteService(r2Client, r2Properties, objectStorageFileRepository);
        this.objectAccessUrlService = objectAccessUrlService;
        this.pdfDerivedMediaAssetExtractionService = pdfDerivedMediaAssetExtractionService;
        this.pandocClient = pandocClient;
    }

    @Override
    @Transactional
    public FileUploadCacheResponse upload(Long userId, MultipartFile file, Long projectId) {
        return upload(userId, file, projectId, false);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse upload(Long userId, MultipartFile file, Long projectId, boolean extractPdfImages) {
        return upload(userId, file, projectId, extractPdfImages, null);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse upload(Long userId, MultipartFile file, Long projectId,
            boolean extractPdfImages, @Nullable String uploadSource) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少文件");
        }
        ThrowUtils.throwIf(file.isEmpty(), ErrorCode.PARAMS_ERROR, "缺少文件");
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小超出限制（最大50MB）");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件失败");
        }
        return uploadWithBytesInternal(userId, file, projectId, bytes, false, extractPdfImages, uploadSource);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse uploadProjectImage(Long userId,
            MultipartFile file,
            Long projectId,
            @Nullable String uploadSource,
            @Nullable String storageChannel,
            @Nullable String visibility) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少文件");
        }
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "projectId不能为空");
        ThrowUtils.throwIf(file.isEmpty(), ErrorCode.PARAMS_ERROR, "缺少文件");
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小超出限制（最大50MB）");
        String contentType = file.getContentType();
        ThrowUtils.throwIf(contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/"),
                ErrorCode.INVALID_FILE_TYPE,
                "仅支持上传图片文件");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件失败");
        }
        if (!requestsPrivateImageStorage(storageChannel, visibility)) {
            return uploadWithBytesInternal(userId, file, projectId, bytes, false, false, uploadSource);
        }
        return uploadProjectImageWithBytesInternal(userId, file, projectId, bytes, uploadSource);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse uploadWithBytes(Long userId, MultipartFile file, Long projectId,
            byte[] bytes) {
        return uploadWithBytes(userId, file, projectId, bytes, false);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse uploadWithBytes(Long userId, MultipartFile file, Long projectId,
            byte[] bytes, boolean extractPdfImages) {
        return uploadWithBytesInternal(userId, file, projectId, bytes, false, extractPdfImages, null);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse uploadWithBytes(Long userId, MultipartFile file, Long projectId,
            byte[] bytes, boolean extractPdfImages, @Nullable String uploadSource) {
        return uploadWithBytesInternal(userId, file, projectId, bytes, false, extractPdfImages, uploadSource);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse uploadGeneratedWithBytes(Long userId, MultipartFile file, Long projectId,
            byte[] bytes) {
        return uploadGeneratedWithBytesInternal(
                userId,
                file,
                projectId,
                bytes,
                false,
                defaultStorageOptions("generated"));
    }

    @Override
    @Transactional
    public FileUploadCacheResponse uploadGeneratedPrivateWithBytes(Long userId, MultipartFile file, Long projectId,
            byte[] bytes) {
        return uploadGeneratedWithBytesInternal(
                userId,
                file,
                projectId,
                bytes,
                false,
                ObjectStorageWriteOptions.privateObject("generated"));
    }

    private FileUploadCacheResponse uploadGeneratedWithBytesInternal(Long userId, MultipartFile file, Long projectId,
            byte[] bytes, boolean extractPdfImages) {
        return uploadGeneratedWithBytesInternal(
                userId,
                file,
                projectId,
                bytes,
                extractPdfImages,
                defaultStorageOptions("generated"));
    }

    private FileUploadCacheResponse uploadGeneratedWithBytesInternal(Long userId,
            MultipartFile file,
            Long projectId,
            byte[] bytes,
            boolean extractPdfImages,
            ObjectStorageWriteOptions storageOptions) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少文件");
        }
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "projectId不能为空");
        ThrowUtils.throwIf(file.isEmpty(), ErrorCode.PARAMS_ERROR, "缺少文件");
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件内容为空");
        ThrowUtils.throwIf(bytes.length > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小超出限制（最大50MB）");
        byte[] normalizedBytes = bytes;
        String bytesHash = HashUtils.sha256Hex(normalizedBytes);
        String sourceKey = HashUtils.sha256Hex("generated:" + userId + ":" + projectId + ":" + bytesHash + ":" + UUID.randomUUID());
        ThrowUtils.throwIf(sourceKey == null || sourceKey.isBlank(), ErrorCode.SYSTEM_ERROR, "生成文件标识失败");
        return storeFileAndBuildResponse(userId, file, projectId, normalizedBytes,
                ObjectStorageFileSourceType.GENERATED.value(), sourceKey, "generated", true, extractPdfImages, storageOptions);
    }

    @Override
    @Transactional
    public FileUploadCacheResponse uploadWithBytesForDocumentPublish(Long userId, MultipartFile file, Long projectId,
            byte[] bytes, Long documentId, int versionNo) {
        // publish 专用：独立 source_type='publish'，不走上传去重
        return publishInternal(userId, file, projectId, bytes, documentId, versionNo);
    }

    private FileUploadCacheResponse uploadWithBytesInternal(Long userId,
            MultipartFile file,
            Long projectId,
            byte[] bytes,
            boolean disableAutoCreateDocument,
            boolean extractPdfImages,
            @Nullable String uploadSource) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少文件");
        }
        ThrowUtils.throwIf(file.isEmpty(), ErrorCode.PARAMS_ERROR, "缺少文件");
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件内容为空");
        ThrowUtils.throwIf(bytes.length > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小超出限制（最大50MB）");

        String sourceType = ObjectStorageFileSourceType.resolvePublicUploadSourceType(uploadSource);
        ThrowUtils.throwIf(sourceType == null, ErrorCode.PARAMS_ERROR,
                "uploadSource仅支持 user_upload/reusable 或 generated；未显式传入时按 generated 处理");
        if (ObjectStorageFileSourceType.GENERATED.value().equals(sourceType)) {
            byte[] normalizedBytes = bytes;
            return uploadGeneratedWithBytesInternal(userId, file, projectId, normalizedBytes, extractPdfImages);
        }

        validateProjectMaterialFile(file);
        byte[] normalizedBytes = bytes;
        String cacheKey = HashUtils.sha256Hex(normalizedBytes);
        ThrowUtils.throwIf(cacheKey == null || cacheKey.isBlank(), ErrorCode.SYSTEM_ERROR, "计算文件指纹失败");
        return storeFileAndBuildResponse(userId, file, projectId, normalizedBytes,
                ObjectStorageFileSourceType.BYTES.value(), cacheKey, "files", !disableAutoCreateDocument,
                extractPdfImages);
    }

    private FileUploadCacheResponse uploadProjectImageWithBytesInternal(Long userId,
            MultipartFile file,
            Long projectId,
            byte[] bytes,
            @Nullable String uploadSource) {
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件内容为空");
        ThrowUtils.throwIf(bytes.length > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小超出限制（最大50MB）");

        String effectiveUploadSource = uploadSource == null || uploadSource.isBlank()
                ? "user_upload"
                : uploadSource;
        String sourceType = ObjectStorageFileSourceType.resolvePublicUploadSourceType(effectiveUploadSource);
        ThrowUtils.throwIf(sourceType == null, ErrorCode.PARAMS_ERROR,
                "uploadSource仅支持 user_upload/reusable 或 generated");

        if (ObjectStorageFileSourceType.GENERATED.value().equals(sourceType)) {
            byte[] normalizedBytes = bytes;
            String bytesHash = HashUtils.sha256Hex(normalizedBytes);
            String sourceKey = HashUtils.sha256Hex(
                    "generated:" + userId + ":" + projectId + ":" + bytesHash + ":" + UUID.randomUUID());
            ThrowUtils.throwIf(sourceKey == null || sourceKey.isBlank(), ErrorCode.SYSTEM_ERROR, "生成文件标识失败");
            return storeFileAndBuildResponse(
                    userId,
                    file,
                    projectId,
                    normalizedBytes,
                    ObjectStorageFileSourceType.GENERATED.value(),
                    sourceKey,
                    "generated",
                    false,
                    false,
                    ObjectStorageWriteOptions.privateObject("generated"));
        }

        String cacheKey = HashUtils.sha256Hex(bytes);
        ThrowUtils.throwIf(cacheKey == null || cacheKey.isBlank(), ErrorCode.SYSTEM_ERROR, "计算文件指纹失败");
        return storeFileAndBuildResponse(
                userId,
                file,
                projectId,
                bytes,
                ObjectStorageFileSourceType.BYTES.value(),
                cacheKey,
                PROJECT_IMAGE_PRIVATE_SCOPE,
                false,
                false,
                ObjectStorageWriteOptions.privateObject(PROJECT_IMAGE_PRIVATE_SCOPE));
    }

    private boolean requestsPrivateImageStorage(@Nullable String storageChannel, @Nullable String visibility) {
        String normalizedChannel = StorageChannel.normalize(storageChannel);
        String normalizedVisibility = StorageVisibility.normalize(visibility);
        if ((normalizedChannel != null && !StorageChannel.isKnown(normalizedChannel))
                || (normalizedVisibility != null && !StorageVisibility.isKnown(normalizedVisibility))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "storageChannel/visibility非法");
        }
        return StorageChannel.PRIVATE.value().equals(normalizedChannel)
                || StorageVisibility.PRIVATE.value().equals(normalizedVisibility);
    }


    /**
     * 发布专用内部方法：source_type='publish'，source_key = sha256(docId:versionNo:bytesHash)。
     * 每次发布都会创建独立的 object_storage_files 记录，彻底与上传去重解耦。
     */
    private FileUploadCacheResponse publishInternal(Long userId, MultipartFile file, Long projectId,
            byte[] bytes, Long documentId, int versionNo) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件内容为空");
        ThrowUtils.throwIf(documentId == null || documentId <= 0, ErrorCode.PARAMS_ERROR, "documentId非法");

        String bytesHash = HashUtils.sha256Hex(bytes);
        String sourceKey = HashUtils.sha256Hex(documentId + ":" + versionNo + ":" + bytesHash);
        ThrowUtils.throwIf(sourceKey == null || sourceKey.isBlank(), ErrorCode.SYSTEM_ERROR, "计算文件指纹失败");
        return storeFileAndBuildResponse(userId, file, projectId, bytes,
                ObjectStorageFileSourceType.PUBLISH.value(), sourceKey, "publish", false, false,
                ObjectStorageWriteOptions.privateObject("publish"));
    }

    private void tryParseAndPersistPdf(ObjectStorageFile stored, MultipartFile file, byte[] bytes,
            boolean newlyCreated) {
        if (stored == null || stored.getId() == null || stored.getId() <= 0) {
            return;
        }
        if (!isPdf(file, stored)) {
            return;
        }

        // 复用场景：老文件已解析过就不再解析；但如果 fragments 缺失（比如历史上解析中断），允许补齐
        boolean hasDerivative = fileDerivativeRepository.existsExtractedText(stored.getId());
        int fragCount = fileTextFragmentRepository.countByFileId(stored.getId());
        if (!newlyCreated && hasDerivative && fragCount > 0) {
            return;
        }

        PdfParseResult parsed = PdfParser.parse(bytes, null);
        String text = parsed.getText();
        if (text == null || text.isBlank()) {
            return;
        }

        // 4000 token 近似分片（默认额外 20000 chars 硬限制）
        List<String> fragments = TokenApproxChunker.chunk(text, 4000);
        if (fragments.isEmpty()) {
            return;
        }

        // 派生物元信息（轻量自描述，用于后续 OCR/Markdown 等扩展）
        JSONB meta = buildExtractedTextMetadata(parsed);

        // upsert: extracted_text & fragments
        fileDerivativeRepository.upsertExtractedText(stored.getId(), text, meta);
        fileTextFragmentRepository.upsertFragments(stored.getId(), fragments, "pdf");
    }

    private String tryParseAndPersistOfficeDocument(ObjectStorageFile stored, MultipartFile file, byte[] bytes,
            String sourceKey,
            boolean newlyCreated) {
        if (stored == null || stored.getId() == null || stored.getId() <= 0) {
            return null;
        }
        OfficeConversionConfig config = resolveOfficeConversionConfig(file, stored);
        if (config == null) {
            return null;
        }
        if (pandocClient == null) {
            log.debug("[OfficeConvert] PandocClient not available (Pandoc not installed), skipping {}->markdown",
                    config.sourceName());
            return null;
        }

        boolean hasDerivative = fileDerivativeRepository.existsExtractedText(stored.getId());
        int fragCount = fileTextFragmentRepository.countByFileId(stored.getId());

        // 重复文件（全局去重复用）：已有完整衍生物时，直接从 DB 读取已存文本，跳过 Pandoc 重跑
        if (!newlyCreated && hasDerivative && fragCount > 0) {
            String storedText = fileDerivativeRepository.getExtractedText(stored.getId());
            if (storedText != null && !storedText.isBlank()) {
                log.debug("[OfficeConvert] reusing stored extracted_text for fileId={}, skipping Pandoc re-run",
                        stored.getId());
                return storedText;
            }
            // DB 里读不到内容（异常情况），fall-through 重新转换
        }

        String markdown = convertOfficeToMarkdown(bytes, sourceKey, config);
        if (markdown == null || markdown.isBlank()) {
            return null;
        }

        String normalized = PdfParser.normalizeText(markdown);
        int maxChars = 2_000_000;
        if (normalized.length() > maxChars) {
            normalized = normalized.substring(0, maxChars);
        }

        if (newlyCreated || !hasDerivative || fragCount <= 0) {
            List<String> fragments = TokenApproxChunker.chunk(normalized, 4000);
            if (!fragments.isEmpty()) {
                JSONB meta = buildExtractedTextMetadataForPandocMarkdown(config.sourceName());
                fileDerivativeRepository.upsertExtractedText(stored.getId(), normalized, meta);
                fileTextFragmentRepository.upsertFragments(stored.getId(), fragments, "md");
            }
        }
        return normalized;
    }

    private String convertOfficeToMarkdown(byte[] bytes, String sourceKey, OfficeConversionConfig config) {
        Path workingDir = null;
        try {
            workingDir = Files.createTempDirectory("pdrd-pandoc-");
            String suffix = "." + config.inputFormat().getPrimaryExtension();
            Path inputFile = workingDir.resolve("source" + suffix);
            String mediaDirectoryName = "pandoc-media";
            Path mediaRoot = workingDir.resolve(mediaDirectoryName);

            Files.write(inputFile, bytes);

            String markdown = pandocClient.builder()
                    .from(config.inputFormat())
                    .to(Format.MARKDOWN_GFM)
                    .wrapNone()
                    .workingDirectory(workingDir)
                    .extractMedia(mediaDirectoryName)
                    .cleanMarkdown()
                    .convertFile(inputFile);

            int mediaCount = countRegularFiles(mediaRoot);
            if (mediaCount <= 0) {
                return markdown;
            }

            try {
                List<UploadedMedia> uploadedMedia = uploadPandocExtractedMedia(mediaRoot, workingDir, sourceKey);
                log.info("[OfficeConvert] uploaded {} media files for sourceKey={}, source={}",
                        uploadedMedia.size(), sourceKey, config.sourceName());
                return replacePandocMediaLinks(markdown, uploadedMedia);
            } catch (Exception e) {
                log.warn("[OfficeConvert] media upload failed for sourceKey={}, source={}: {}",
                        sourceKey, config.sourceName(), e.getMessage());
                return markdown;
            }
        } catch (Exception e) {
            log.warn("[OfficeConvert] pandoc {}->markdown conversion failed: {}", config.sourceName(), e.getMessage());
            return null;
        } finally {
            if (workingDir != null) {
                try {
                    deleteRecursively(workingDir);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean isDocx(MultipartFile file, ObjectStorageFile stored) {
        String ct = file == null ? null : file.getContentType();
        if (ct != null && ct.equalsIgnoreCase(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            return true;
        }
        String ext = stored == null ? null : stored.getFileExt();
        if (ext != null && !ext.isBlank()) {
            return "docx".equalsIgnoreCase(ext.replaceFirst("^\\.", ""));
        }
        String name = file == null ? null : file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    private boolean isPptx(MultipartFile file, ObjectStorageFile stored) {
        String ct = file == null ? null : file.getContentType();
        if (ct != null && ct.equalsIgnoreCase(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return true;
        }
        String ext = stored == null ? null : stored.getFileExt();
        if (ext != null && !ext.isBlank()) {
            return "pptx".equalsIgnoreCase(ext.replaceFirst("^\\.", ""));
        }
        String name = file == null ? null : file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".pptx");
    }


    private OfficeConversionConfig resolveOfficeConversionConfig(MultipartFile file, ObjectStorageFile stored) {
        if (isDocx(file, stored)) {
            return new OfficeConversionConfig(Format.DOCX, "docx");
        }
        if (isPptx(file, stored)) {
            return new OfficeConversionConfig(Format.PPTX, "pptx");
        }
        return null;
    }

    private String toMarkdownFilename(String original) {
        if (original == null || original.isBlank()) {
            return "document.md";
        }
        String name = original.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name + ".md";
    }




    private List<UploadedMedia> uploadPandocExtractedMedia(Path mediaRoot, Path workingDirectory,
            String sourceKey) throws java.io.IOException {
        List<Path> files = listRegularFiles(mediaRoot);
        if (files.isEmpty()) {
            return List.of();
        }

        List<UploadedMedia> uploadedMedia = new ArrayList<>(files.size());
        for (Path file : files) {
            String relativeToMediaRoot = toUnixPath(mediaRoot.relativize(file));
            String relativeToWorkingDirectory = toUnixPath(workingDirectory.relativize(file));
            String objectKey = buildPandocMediaObjectKey(sourceKey, relativeToMediaRoot);
            String contentType = detectContentType(file);
            String publicUrl = r2Client.upload(objectKey, Files.readAllBytes(file), contentType);
            uploadedMedia.add(new UploadedMedia(relativeToWorkingDirectory, relativeToMediaRoot, publicUrl));
        }
        return uploadedMedia;
    }

    private String buildPandocMediaObjectKey(String sourceKey, String relativePath) {
        String normalizedSourceKey = sourceKey == null || sourceKey.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : sourceKey.trim();
        String normalizedRelativePath = toUnixPath(Path.of(relativePath)).replaceFirst("^/+", "");
        String prefix = r2Properties.effectivePathPrefix();
        String base = "pandoc-media/" + normalizedSourceKey + "/" + normalizedRelativePath;
        if (prefix == null || prefix.isBlank()) {
            return base;
        }
        return prefix.replaceAll("/$", "") + "/" + base;
    }

    private String replacePandocMediaLinks(String markdown, List<UploadedMedia> uploadedMedia) {
        if (markdown == null || markdown.isBlank() || uploadedMedia == null || uploadedMedia.isEmpty()) {
            return markdown;
        }
        List<UploadedMedia> sortedMedia = new ArrayList<>(uploadedMedia);
        sortedMedia.sort(Comparator.comparingInt(this::sortUploadedMediaWeight).reversed());

        String rewritten = markdown;
        for (UploadedMedia media : sortedMedia) {
            rewritten = replaceMarkdownDestination(rewritten, media.relativePathFromWorkingDirectory(), media.publicUrl());
            rewritten = replaceMarkdownDestination(rewritten, "./" + media.relativePathFromWorkingDirectory(), media.publicUrl());
            rewritten = replaceMarkdownDestination(rewritten, media.relativePathFromMediaRoot(), media.publicUrl());
            rewritten = replaceMarkdownDestination(rewritten, "./" + media.relativePathFromMediaRoot(), media.publicUrl());
        }
        return rewritten;
    }

    private int sortUploadedMediaWeight(UploadedMedia media) {
        return Math.max(media.relativePathFromWorkingDirectory().length(), media.relativePathFromMediaRoot().length());
    }

    private String replaceMarkdownDestination(String markdown, String sourcePath, String targetUrl) {
        if (sourcePath == null || sourcePath.isBlank() || targetUrl == null || targetUrl.isBlank()
                || sourcePath.equals(targetUrl)) {
            return markdown;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\\(" + java.util.regex.Pattern.quote(sourcePath) + "(?=(?:\\s+\"[^\"]*\")?\\))");
        return pattern.matcher(markdown)
                .replaceAll(java.util.regex.Matcher.quoteReplacement("(" + targetUrl));
    }

    private List<Path> listRegularFiles(Path root) throws java.io.IOException {
        if (root == null || !Files.isDirectory(root)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    private int countRegularFiles(Path root) throws java.io.IOException {
        return listRegularFiles(root).size();
    }

    private String detectContentType(Path file) throws java.io.IOException {
        String contentType = Files.probeContentType(file);
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return switch (getExtension(file).toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private String getExtension(Path file) {
        if (file == null || file.getFileName() == null) {
            return "";
        }
        String filename = file.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1);
    }

    private String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private void deleteRecursively(Path root) throws java.io.IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws java.io.IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, java.io.IOException exc)
                    throws java.io.IOException {
                Files.deleteIfExists(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private void tryParseAndPersistMarkdown(ObjectStorageFile stored, MultipartFile file, byte[] bytes,
            boolean newlyCreated) {
        if (stored == null || stored.getId() == null || stored.getId() <= 0) {
            return;
        }
        if (!isMarkdown(file, stored)) {
            return;
        }

        boolean hasDerivative = fileDerivativeRepository.existsExtractedText(stored.getId());
        int fragCount = fileTextFragmentRepository.countByFileId(stored.getId());
        if (!newlyCreated && hasDerivative && fragCount > 0) {
            return;
        }

        String text = decodeUtf8Text(bytes);
        if (text.isBlank()) {
            return;
        }
        // 与 PDF 一致的“轻量规范化”：只统一换行/合并过多空行与空格（不是 markdown 语法清洗）
        text = PdfParser.normalizeText(text);
        // 防炸：避免极端长文本导致 DB/上下文压力
        int maxChars = 2_000_000;
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars);
        }

        List<String> fragments = TokenApproxChunker.chunk(text, 4000);
        if (fragments.isEmpty()) {
            return;
        }

        JSONB meta = buildExtractedTextMetadataForMarkdown();
        fileDerivativeRepository.upsertExtractedText(stored.getId(), text, meta);
        fileTextFragmentRepository.upsertFragments(stored.getId(), fragments, "md");
    }

    private boolean isPdf(MultipartFile file, ObjectStorageFile stored) {
        String ct = file == null ? null : file.getContentType();
        if (ct != null && ct.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        String ext = stored == null ? null : stored.getFileExt();
        if (ext != null && !ext.isBlank()) {
            return "pdf".equalsIgnoreCase(ext.replaceFirst("^\\.", ""));
        }
        String name = file == null ? null : file.getOriginalFilename();
        return name != null && name.toLowerCase().endsWith(".pdf");
    }

    private boolean isMarkdown(MultipartFile file, ObjectStorageFile stored) {
        String ct = file == null ? null : file.getContentType();
        if (ct != null && !ct.isBlank()) {
            String c = ct.toLowerCase(Locale.ROOT);
            if (c.equals("text/markdown") || c.equals("text/x-markdown")) {
                return true;
            }
        }
        String ext = stored == null ? null : stored.getFileExt();
        if (ext != null && !ext.isBlank()) {
            return "md".equalsIgnoreCase(ext.replaceFirst("^\\.", ""));
        }
        String name = file == null ? null : file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".md");
    }

    private String decodeUtf8Text(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String s = new String(bytes, StandardCharsets.UTF_8);
        // UTF-8 BOM
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private JSONB buildExtractedTextMetadata(PdfParseResult parsed) {
        if (parsed == null) {
            return null;
        }
        // 避免引入额外 JSON 序列化依赖：这里手写一个最小 JSON（字段值做简单转义）
        String level = esc(parsed.getLevel());
        String parser = esc(parsed.getParser());
        String version = esc(parsed.getParserVersion());
        String title = esc(parsed.getTitle());
        String titleCandidate = esc(parsed.getTitleCandidate());
        Integer pages = parsed.getPageCount();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (level != null)
            sb.append("\"level\":\"").append(level).append("\",");
        if (parser != null)
            sb.append("\"parser\":\"").append(parser).append("\",");
        if (version != null)
            sb.append("\"parserVersion\":\"").append(version).append("\",");
        if (title != null)
            sb.append("\"title\":\"").append(title).append("\",");
        if (titleCandidate != null)
            sb.append("\"titleCandidate\":\"").append(titleCandidate).append("\",");
        if (pages != null)
            sb.append("\"pageCount\":").append(pages).append(",");

        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("}");
        return JSONB.valueOf(sb.toString());
    }

    private JSONB buildExtractedTextMetadataForMarkdown() {
        // 最小自描述：不依赖额外序列化库
        return JSONB.valueOf("{\"format\":\"markdown\",\"charset\":\"utf-8\"}");
    }

    private JSONB buildExtractedTextMetadataForPandocMarkdown(String source) {
        String normalizedSource = esc(source);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"format\":\"markdown\",\"converter\":\"pandoc\"");
        if (normalizedSource != null) {
            sb.append(",\"source\":\"").append(normalizedSource).append("\"");
        }
        sb.append("}");
        return JSONB.valueOf(sb.toString());
    }

    private String esc(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isBlank()) {
            return null;
        }
        return t.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Transactional
    @Override
    public boolean addFileToProject(Long userId, Long projectId, String cacheKey, String fileName,
            @Nullable Long fileId) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目ID非法");
        ThrowUtils.throwIf(cacheKey == null || cacheKey.isBlank(), ErrorCode.PARAMS_ERROR, "cacheKey不能为空");
        requireProjectOwner(userId, projectId);
        ObjectStorageFile file;
        if (fileId != null && fileId > 0) {
            file = objectStorageFileRepository.findById(fileId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或不可用"));
        } else {
            file = resolveAvailableOwnedFileByCacheKey(userId, cacheKey)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或不可用"));
        }

        String resolvedName = (fileName != null && !fileName.isBlank()) ? fileName : file.getOriginalName();
        projectFileLinkRepository.upsertLink(projectId, file.getId(), userId, resolvedName,
                guessKindFromExt(file.getFileExt(), file.getMimeType()));
        // 更新项目活跃时间
        projectRepository.updateLastActiveAt(projectId, OffsetDateTime.now());
        return true;
    }

    @Override
    @Transactional
    public FileUploadCacheResponse duplicateProjectFile(Long userId, Long projectId, Long fileId, @Nullable String newName) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目ID非法");
        ThrowUtils.throwIf(fileId == null || fileId <= 0, ErrorCode.PARAMS_ERROR, "文件ID非法");
        requireProjectOwner(userId, projectId);

        ProjectFileLinkView sourceView = projectFileLinkRepository.listProjectFiles(projectId).stream()
                .filter(v -> v.getFileId() != null && v.getFileId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或已被移除"));

        ObjectStorageFile source = objectStorageFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或不可用"));
        ThrowUtils.throwIf(Boolean.TRUE.equals(source.getDeleted())
                        || source.getStatus() == null
                        || !"available".equalsIgnoreCase(source.getStatus()),
                ErrorCode.NOT_FOUND_ERROR,
                "文件不存在或不可用");

        byte[] bytes = downloadStoredFileBytes(source);
        String duplicateName = resolveDuplicateFileName(newName, sourceView.getDisplayName(), source.getOriginalName());
        InMemoryMultipartFile duplicatedFile = new InMemoryMultipartFile(
                "file",
                duplicateName,
                source.getMimeType(),
                bytes
        );
        String duplicateKey = HashUtils.sha256Hex(
                "duplicate:" + userId + ":" + projectId + ":" + fileId + ":" + UUID.randomUUID());
        ThrowUtils.throwIf(duplicateKey == null || duplicateKey.isBlank(), ErrorCode.SYSTEM_ERROR, "生成复制文件标识失败");

        return storeFileAndBuildResponse(userId, duplicatedFile, projectId, bytes,
                ObjectStorageFileSourceType.COPY.value(), duplicateKey, "files", true, false);
    }

    private void addFileToProjectBatch(Long userId, Long projectId, List<ProjectLinkItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ProjectFileLinkUpsertItem> linkItems = new ArrayList<>();
        Set<Long> fileIds = new HashSet<>();
        for (ProjectLinkItem it : items) {
            if (it == null || it.getFileId() == null || it.getFileId() <= 0) {
                continue;
            }
            ProjectFileLinkUpsertItem link = new ProjectFileLinkUpsertItem();
            link.setFileId(it.getFileId());
            link.setDisplayName(it.getDisplayName());
            link.setKind(it.getKind());
            linkItems.add(link);
            fileIds.add(it.getFileId());
        }
        if (linkItems.isEmpty())
            return;
        log.info("准备批量添加文件到项目，projectId={}, fileIds={}", projectId, fileIds);
        projectFileLinkRepository.upsertLinks(projectId, userId, linkItems);
        projectRepository.updateLastActiveAt(projectId, OffsetDateTime.now());
    }

    @Override
    @Transactional
    public boolean removeFileFromProject(Long userId, Long projectId, String cacheKey) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目ID非法");
        ThrowUtils.throwIf(cacheKey == null || cacheKey.isBlank(), ErrorCode.PARAMS_ERROR, "cacheKey不能为空");
        requireProjectOwner(userId, projectId);

        Long fileId = projectFileLinkRepository.findActiveFileIdByProjectAndCacheKey(projectId, cacheKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或已从项目移除"));
        return projectFileLinkRepository.softDeleteLink(projectId, fileId);
    }

    @Override
    @Transactional(readOnly = true)
    public FilePurgePreviewResponse previewProjectFilePurge(Long userId, Long projectId, Long fileId) {
        requireFilePurgeRepository();
        ThrowUtils.throwIf(fileId == null || fileId <= 0, ErrorCode.PARAMS_ERROR, "文件ID非法");
        requireProjectOwner(userId, projectId);
        ObjectStorageFile file = objectStorageFileRepository.findById(fileId)
                .filter(f -> f.getOwnerUserId() != null && f.getOwnerUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或无权限"));
        requireProjectFileHistory(projectId, fileId);
        FilePurgeRepository.FilePurgeUsageSnapshot usage = filePurgeRepository.collectUsageSnapshot(fileId);
        return buildPurgePreview(projectId, file, usage);
    }

    @Override
    @Transactional
    public FilePurgeResponse purgeProjectFile(Long userId, Long projectId, Long fileId, FilePurgeRequest request) {
        requireFilePurgeRepository();
        ThrowUtils.throwIf(fileId == null || fileId <= 0, ErrorCode.PARAMS_ERROR, "文件ID非法");
        ThrowUtils.throwIf(request == null || !Boolean.TRUE.equals(request.getConfirm()),
                ErrorCode.PARAMS_ERROR,
                "永久删除需要显式确认");
        requireProjectOwner(userId, projectId);

        ObjectStorageFile file = filePurgeRepository.findOwnedFileForUpdate(userId, fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或无权限"));
        requireProjectFileHistory(projectId, fileId);
        if (request.getExpectedCacheKey() != null && !request.getExpectedCacheKey().isBlank()
                && !request.getExpectedCacheKey().trim().equals(file.getSourceKey())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件标识已变化，请刷新后重试");
        }

        FilePurgeRepository.FilePurgeUsageSnapshot usage = filePurgeRepository.collectUsageSnapshot(fileId);
        FilePurgePreviewResponse preview = buildPurgePreview(projectId, file, usage);
        if (Boolean.TRUE.equals(preview.getAlreadyDeleted())) {
            FilePurgeResponse response = new FilePurgeResponse();
            response.setProjectId(projectId);
            response.setFileId(fileId);
            response.setPurged(false);
            response.setStatus("already_deleted");
            response.setScheduledObjectDeleteCount(0);
            response.setPreview(preview);
            return response;
        }
        if (!Boolean.TRUE.equals(preview.getPurgeable())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件仍存在活跃引用，暂不能永久删除");
        }

        List<StoredObjectRef> objectRefs = collectPurgeObjectRefs(file, filePurgeRepository.listDerivativeObjectKeys(fileId));
        filePurgeRepository.revokeAccessGrants(fileId);
        filePurgeRepository.softDeleteTextFragments(fileId);
        filePurgeRepository.softDeleteDerivatives(fileId);
        filePurgeRepository.deleteInactiveReferencesForHardDelete(fileId);
        boolean deleted = filePurgeRepository.hardDeleteFileRecord(userId, fileId);
        ThrowUtils.throwIf(!deleted, ErrorCode.OPERATION_ERROR, "文件永久删除失败，请刷新后重试");

        deleteR2ObjectsAfterCommit(objectRefs);

        FilePurgeResponse response = new FilePurgeResponse();
        response.setProjectId(projectId);
        response.setFileId(fileId);
        response.setPurged(true);
        response.setStatus("purged");
        response.setScheduledObjectDeleteCount(objectRefs.size());
        response.setPreview(preview);
        return response;
    }

    @Override
    @Transactional
    public boolean renameProjectFile(Long userId, Long projectId, Long fileId, String newName) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目ID非法");
        ThrowUtils.throwIf(fileId == null || fileId <= 0, ErrorCode.PARAMS_ERROR, "文件ID非法");
        ThrowUtils.throwIf(newName == null || newName.isBlank(), ErrorCode.PARAMS_ERROR, "文件名不能为空");
        ThrowUtils.throwIf(newName.length() > 255, ErrorCode.PARAMS_ERROR, "文件名不能超过255个字符");
        requireProjectOwner(userId, projectId);

        boolean updated = projectFileLinkRepository.updateDisplayName(projectId, fileId, newName.trim());
        ThrowUtils.throwIf(!updated, ErrorCode.NOT_FOUND_ERROR, "文件不存在或已被移除");
        return true;
    }

    @Override
    public List<ProjectFileMappingDTO> listProjectFiles(Long userId, Long projectId) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目ID非法");
        requireProjectOwner(userId, projectId);

        List<ProjectFileLinkView> list = projectFileLinkRepository.listProjectFiles(projectId);

        List<Long> fileIds = list.stream()
                .map(ProjectFileLinkView::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 批量查询“草稿文档”（按 current_file_id 绑定到该文件版本）避免 N+1
        Map<Long, Document> docsByFileId = documentRepository
                .listByProjectAndCurrentFileIds(projectId, fileIds)
                .stream()
                .filter(d -> d.getCurrentFileId() != null && d.getCurrentFileId() > 0)
                .collect(Collectors.toMap(
                        Document::getCurrentFileId,
                        Function.identity(),
                        // 若异常重复，保留 updated_at 更靠后的那条
                        (a, b) -> {
                            if (a == null)
                                return b;
                            if (b == null)
                                return a;
                            if (a.getUpdatedAt() == null)
                                return b;
                            if (b.getUpdatedAt() == null)
                                return a;
                            return a.getUpdatedAt().isAfter(b.getUpdatedAt()) ? a : b;
                        }));

        // project_file_links 保留了每次 publish 生成的不可变文件版本；项目文件列表只暴露
        // 当前文档版本，历史版本仍由 document_versions/回滚接口管理。
        Set<Long> supersededVersionFileIds = docsByFileId.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(documentRepository.listSupersededVersionFileIdsByProject(projectId, fileIds));
        // 防御极端共享数据：如果某个 file 同时是另一活动文档的 current file，仍应保留。
        supersededVersionFileIds.removeAll(docsByFileId.keySet());
        if (!supersededVersionFileIds.isEmpty()) {
            list = list.stream()
                    .filter(v -> v.getFileId() == null || !supersededVersionFileIds.contains(v.getFileId()))
                    .toList();
        }

        // 批量查询上传者信息（避免N+1问题）
        Set<Long> uploaderIds = list.stream()
                .map(ProjectFileLinkView::getAddedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> uploaders = uploaderIds.isEmpty()
                ? Map.of()
                : userRepository.findByIds(uploaderIds.stream().toList())
                        .stream()
                        .collect(Collectors.toMap(
                                User::getId,
                                Function.identity()));

        return list.stream().map(v -> {
            ProjectFileMappingDTO dto = new ProjectFileMappingDTO();
            dto.setId(v.getFileId());
            dto.setProjectId(v.getProjectId());
            dto.setCacheKey(v.getCacheKey());
            dto.setFileName(v.getDisplayName());
            dto.setAddedAt(v.getAddedAt());
            dto.setSourceId(v.getSourceId());
            dto.setSizeBytes(v.getSizeBytes());
            dto.setObjectKey(v.getObjectKey());

            // ⭐ 文档草稿（若存在）：前端优先使用 documentId 进入编辑态
            if (v.getFileId() != null) {
                Document doc = docsByFileId.get(v.getFileId());
                if (doc != null && doc.getId() != null && doc.getId() > 0) {
                    dto.setDocumentId(doc.getId());
                    dto.setDocumentType(doc.getDocumentType());
                    dto.setDraftUpdatedAt(doc.getDraftUpdatedAt());
                    dto.setDocumentVersion(doc.getVersion());
                }
            }

            dto.setPublicUrl(buildAccessibleUrl(v));
            dto.setStorageChannel(normalizedStorageChannel(v.getStorageChannel()));
            dto.setVisibility(normalizedStorageVisibility(v.getVisibility()));

            // ⭐ 组装上传者信息
            if (v.getAddedBy() != null) {
                User uploader = uploaders.get(v.getAddedBy());
                if (uploader != null) {
                    ProjectFileMappingDTO.UploaderInfo uploaderInfo = new ProjectFileMappingDTO.UploaderInfo();
                    uploaderInfo.setUserId(uploader.getId());
                    uploaderInfo.setName(uploader.getName());
                    uploaderInfo.setEmail(uploader.getEmail());
                    dto.setUploader(uploaderInfo);
                }
            }

            return dto;
        }).toList();
    }

    @Override
    public List<RecentProjectFileDTO> listRecentUserUploadedFiles(Long userId, int limit) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        int safeLimit = limit <= 0
                ? DEFAULT_RECENT_USER_FILE_LIMIT
                : Math.min(limit, MAX_RECENT_USER_FILE_LIMIT);

        return objectStorageFileRepository
                .listRecentUserUploadedFiles(userId, safeLimit)
                .stream()
                .map(this::toRecentProjectFileDTO)
                .toList();
    }

    private RecentProjectFileDTO toRecentProjectFileDTO(ObjectStorageFile file) {
        RecentProjectFileDTO dto = new RecentProjectFileDTO();
        dto.setFileId(file.getId());
        dto.setCacheKey(file.getSourceKey());
        dto.setFileName(file.getOriginalName());
        dto.setMimeType(file.getMimeType());
        dto.setFileExt(file.getFileExt());
        dto.setSizeBytes(file.getSizeBytes());
        dto.setObjectKey(file.getObjectKey());
        dto.setPublicUrl(buildPublicUrl(file));
        dto.setStorageChannel(normalizedStorageChannel(file.getStorageChannel()));
        dto.setVisibility(normalizedStorageVisibility(file.getVisibility()));
        dto.setCreatedAt(file.getCreatedAt());
        return dto;
    }

    private void requireProjectOwner(Long userId, Long projectId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在"));
        if (Boolean.TRUE.equals(project.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        }

        ThrowUtils.throwIf(project.getOwnerId() == null || !project.getOwnerId().equals(userId),
                ErrorCode.NO_AUTH_ERROR, "无权限");
    }

    private FilePurgeRepository requireFilePurgeRepository() {
        if (filePurgeRepository == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件永久删除组件未初始化");
        }
        return filePurgeRepository;
    }

    private void requireProjectFileHistory(Long projectId, Long fileId) {
        if (!requireFilePurgeRepository().existsProjectHistoryLink(projectId, fileId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "文件不属于当前项目");
        }
    }

    private FilePurgePreviewResponse buildPurgePreview(Long projectId,
            ObjectStorageFile file,
            FilePurgeRepository.FilePurgeUsageSnapshot usage) {
        FilePurgePreviewResponse response = new FilePurgePreviewResponse();
        response.setProjectId(projectId);
        response.setFileId(file.getId());
        response.setCacheKey(file.getSourceKey());
        response.setSourceType(file.getSourceType());
        response.setFileName(file.getOriginalName());
        response.setMimeType(file.getMimeType());
        response.setSizeBytes(file.getSizeBytes());
        response.setPublicUrl(buildPublicUrl(file));

        boolean alreadyDeleted = Boolean.TRUE.equals(file.getDeleted())
                || "deleted".equalsIgnoreCase(file.getStatus());
        response.setAlreadyDeleted(alreadyDeleted);
        if (alreadyDeleted) {
            addPurgeBlocker(response, "already_deleted", 1,
                    "文件已经被永久删除，无法再次执行删除");
        }
        if (usage != null) {
            addPurgeBlockerIfPresent(response,
                    "active_project_links",
                    usage.activeProjectLinks(),
                    "文件仍存在项目文件列表引用，请先从所有项目中移除");
            addPurgeBlockerIfPresent(response,
                    "active_documents",
                    usage.activeDocuments(),
                    "文件仍被未删除的 document.current_file_id 引用");
            addPurgeBlockerIfPresent(response,
                    "active_document_versions",
                    usage.activeDocumentVersions(),
                    "文件仍被未删除文档的版本记录引用");
            addPurgeBlockerIfPresent(response,
                    "active_derived_media_source_refs",
                    usage.activeDerivedMediaSourceReferences(),
                    "文件仍被 PDF 派生媒体素材作为源文件引用");
            addPurgeBlockerIfPresent(response,
                    "active_derived_media_file_refs",
                    usage.activeDerivedMediaFileReferences(),
                    "文件仍被 PDF 派生媒体素材作为派生文件引用");
            addPurgeBlockerIfPresent(response,
                    "retained_derived_media_cache_refs",
                    usage.retainedDerivedMediaCacheReferences(),
                    "文件仍在派生媒体素材缓存保留期内");
        }

        response.getWarnings().add("永久删除会删除源文件及解析缓存；再次上传相同文件时需要重新上传和重新解析。");
        response.setPurgeable(response.getBlockers().isEmpty());
        return response;
    }

    private void addPurgeBlockerIfPresent(FilePurgePreviewResponse response,
            String type,
            long count,
            String message) {
        if (count <= 0) {
            return;
        }
        addPurgeBlocker(response, type, count, message);
    }

    private void addPurgeBlocker(FilePurgePreviewResponse response,
            String type,
            long count,
            String message) {
        FilePurgeBlockerDTO blocker = new FilePurgeBlockerDTO();
        blocker.setType(type);
        blocker.setCount(count);
        blocker.setMessage(message);
        response.getBlockers().add(blocker);
    }

    private List<StoredObjectRef> collectPurgeObjectRefs(ObjectStorageFile file, List<String> derivativeKeys) {
        LinkedHashSet<StoredObjectRef> refs = new LinkedHashSet<>();
        if (file != null && file.getObjectKey() != null && !file.getObjectKey().isBlank()) {
            refs.add(new StoredObjectRef(resolveStorageChannel(file), file.getObjectKey().trim()));
        }
        if (derivativeKeys != null) {
            derivativeKeys.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(key -> new StoredObjectRef(StorageChannel.PUBLIC, key))
                    .forEach(refs::add);
        }
        return new ArrayList<>(refs);
    }

    private void deleteR2ObjectsAfterCommit(List<StoredObjectRef> objectRefs) {
        if (objectRefs == null || objectRefs.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteR2ObjectsBestEffort(objectRefs);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteR2ObjectsBestEffort(objectRefs);
            }
        });
    }

    private void deleteR2ObjectsBestEffort(List<StoredObjectRef> objectRefs) {
        for (StoredObjectRef ref : objectRefs) {
            if (ref == null || ref.objectKey() == null || ref.objectKey().isBlank()) {
                continue;
            }
            try {
                r2Client.delete(ref.channel(), ref.objectKey());
            } catch (Exception e) {
                log.warn("[FilePurge] Failed to delete R2 object, channel={}, key={}",
                        ref.channel().value(), ref.objectKey(), e);
            }
        }
    }

    private FileUploadCacheResponse storeFileAndBuildResponse(Long userId,
            MultipartFile file,
            Long projectId,
            byte[] bytes,
            String sourceType,
            String sourceKey,
            String scopePrefix,
            boolean enableAutoCreateDocument,
            boolean extractPdfImages) {
        return storeFileAndBuildResponse(
                userId,
                file,
                projectId,
                bytes,
                sourceType,
                sourceKey,
                scopePrefix,
                enableAutoCreateDocument,
                extractPdfImages,
                defaultStorageOptions(scopePrefix));
    }

    private ObjectStorageWriteOptions defaultStorageOptions(String scopePrefix) {
        return r2Properties != null && r2Properties.effectivePublicDomain() != null
                ? ObjectStorageWriteOptions.publicObject(scopePrefix)
                : ObjectStorageWriteOptions.privateObject(scopePrefix);
    }

    private FileUploadCacheResponse storeFileAndBuildResponse(Long userId,
            MultipartFile file,
            Long projectId,
            byte[] bytes,
            String sourceType,
            String sourceKey,
            String scopePrefix,
            boolean enableAutoCreateDocument,
            boolean extractPdfImages,
            ObjectStorageWriteOptions storageOptions) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        if (file == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "缺少文件");
        }
        ThrowUtils.throwIf(file.isEmpty(), ErrorCode.PARAMS_ERROR, "缺少文件");
        ThrowUtils.throwIf(bytes == null || bytes.length == 0, ErrorCode.PARAMS_ERROR, "文件内容为空");
        ThrowUtils.throwIf(bytes.length > MAX_SIZE_BYTES, ErrorCode.PARAMS_ERROR, "文件大小超出限制（最大50MB）");
        String normalizedSourceType = ObjectStorageFileSourceType.normalize(sourceType);
        ThrowUtils.throwIf(!ObjectStorageFileSourceType.isKnown(normalizedSourceType),
                ErrorCode.PARAMS_ERROR,
                "sourceType不支持");
        ThrowUtils.throwIf(sourceKey == null || sourceKey.isBlank(), ErrorCode.PARAMS_ERROR, "sourceKey不能为空");

        boolean hasProject = projectId != null && projectId > 0;
        StoredFileResolution storedResolution = resolveOrStoreObjectFile(
                userId, normalizedSourceType, sourceKey, scopePrefix, file, bytes, storageOptions);
        ObjectStorageFile stored = storedResolution.stored();
        String publicUrl = storedResolution.publicUrl();
        boolean newlyCreated = storedResolution.newlyCreated();

        if (hasProject) {
            requireProjectOwner(userId, projectId);
            projectFileLinkRepository.upsertLink(projectId, stored.getId(), userId, file.getOriginalFilename(),
                    guessKind(file));
            projectRepository.updateLastActiveAt(projectId, OffsetDateTime.now());
            fileAccessGrantRepository.upsertLink(userId, stored.getId(), projectId);
        } else {
            log.debug("store file without projectId: no user-file link created (sourceKey={})", sourceKey);
        }

        String officeConvertedMarkdown = null;
        if (enableAutoCreateDocument) {
            tryParseAndPersistPdf(stored, file, bytes, newlyCreated);
            tryParseAndPersistMarkdown(stored, file, bytes, newlyCreated);
            officeConvertedMarkdown = tryParseAndPersistOfficeDocument(stored, file, bytes, sourceKey, newlyCreated);
        }

        FileUploadCacheResponse resp = new FileUploadCacheResponse();
        resp.setCacheKey(sourceKey);
        resp.setSourceFileId(stored.getId());
        resp.setPublicUrl(publicUrl);
        populateStorageResponse(resp, stored, publicUrl);
        resp.setFileName(file.getOriginalFilename());
        Long storedSizeBytes = stored.getSizeBytes() == null ? (long) bytes.length : stored.getSizeBytes();
        resp.setFileSize(storedSizeBytes);
        resp.setSizeBytes(storedSizeBytes);
        resp.setMimeType(firstNonBlank(stored.getMimeType(), resolveStoredContentType(file)));
        resp.setFileId("file_" + sourceKey);
        boolean extractionQueued = requestDocumentDerivedImageExtraction(
                userId,
                projectId,
                stored,
                file,
                bytes,
                hasProject,
                extractPdfImages);
        if (extractionQueued) {
            resp.setPdfDerivedAssetsExtractionQueued(true);
        }

        if (!enableAutoCreateDocument || !hasProject) {
            return resp;
        }

        if (isMarkdown(file, stored)) {
            try {
                String draft = trimToMaxChars(decodeUtf8Text(bytes), 2_000_000);
                Long docId = documentAutoCreateService.ensureCreatedForUploadedMarkdown(
                        userId, projectId, stored.getId(), file.getOriginalFilename(), draft);
                resp.setDocumentId(docId);
                resp.setDocumentType(docId == null ? null : DocumentType.MARKDOWN);
            } catch (Exception e) {
                rethrowIfTransactionFatal(e);
                log.warn("[FileService] auto-create document failed, fileId={}, projectId={}",
                        stored.getId(), projectId, e);
            }
        } else if (officeConvertedMarkdown != null && !officeConvertedMarkdown.isBlank()) {
            try {
                String draft = trimToMaxChars(officeConvertedMarkdown, 2_000_000);
                Long docId = documentAutoCreateService.ensureCreatedForUploadedMarkdown(
                        userId, projectId, stored.getId(),
                        toMarkdownFilename(file.getOriginalFilename()), draft);
                resp.setDocumentId(docId);
                resp.setDocumentType(docId == null ? null : DocumentType.MARKDOWN);
            } catch (Exception e) {
                rethrowIfTransactionFatal(e);
                log.warn("[FileService] auto-create document for office file failed, fileId={}, projectId={}",
                        stored.getId(), projectId, e);
            }
        }

        return resp;
    }

    private void populateStorageResponse(FileUploadCacheResponse response,
            ObjectStorageFile stored,
            @Nullable String storedPublicUrl) {
        if (response == null || stored == null) {
            return;
        }
        response.setObjectKey(stored.getObjectKey());
        response.setStorageChannel(normalizedStorageChannel(stored.getStorageChannel()));
        response.setVisibility(normalizedStorageVisibility(stored.getVisibility()));
        String accessUrl = firstNonBlank(storedPublicUrl, buildPublicUrl(stored));
        response.setAccessUrl(accessUrl);
        if (response.getPublicUrl() == null && !StorageChannel.isPrivate(stored.getStorageChannel())) {
            response.setPublicUrl(accessUrl);
        }
    }

    private boolean requestDocumentDerivedImageExtraction(Long userId,
                                                          Long projectId,
                                                          ObjectStorageFile stored,
                                                          MultipartFile file,
                                                          byte[] bytes,
                                                          boolean hasProject,
                                                          boolean extractPdfImages) {
        if (!extractPdfImages || !hasProject || pdfDerivedMediaAssetExtractionService == null) {
            return false;
        }
        if (isPdf(file, stored)) {
            try {
                return pdfDerivedMediaAssetExtractionService.requestExtractionAfterCommit(
                        userId,
                        projectId,
                        stored,
                        bytes,
                        file.getOriginalFilename());
            } catch (Exception e) {
                rethrowIfTransactionFatal(e);
                log.warn("[FileService] failed to enqueue PDF derived image extraction, fileId={}, projectId={}, err={}",
                        stored == null ? null : stored.getId(), projectId, e.getMessage());
                return false;
            }
        }
        if (isDocx(file, stored)) {
            try {
                return pdfDerivedMediaAssetExtractionService.requestDocxExtractionAfterCommit(
                        userId,
                        projectId,
                        stored,
                        bytes,
                        file.getOriginalFilename());
            } catch (Exception e) {
                rethrowIfTransactionFatal(e);
                log.warn("[FileService] failed to enqueue DOCX derived image extraction, fileId={}, projectId={}, err={}",
                        stored == null ? null : stored.getId(), projectId, e.getMessage());
                return false;
            }
        }
        if (isPptx(file, stored)) {
            try {
                return pdfDerivedMediaAssetExtractionService.requestPptxExtractionAfterCommit(
                        userId,
                        projectId,
                        stored,
                        bytes,
                        file.getOriginalFilename());
            } catch (Exception e) {
                rethrowIfTransactionFatal(e);
                log.warn("[FileService] failed to enqueue PPTX derived image extraction, fileId={}, projectId={}, err={}",
                        stored == null ? null : stored.getId(), projectId, e.getMessage());
                return false;
            }
        }
        return false;
    }

    private void rethrowIfTransactionFatal(Exception e) {
        if (e instanceof DataAccessException
                || e instanceof TransactionException
                || e instanceof org.jooq.exception.DataAccessException) {
            throw (RuntimeException) e;
        }
    }

    private StoredFileResolution resolveOrStoreObjectFile(Long userId,
            String sourceType,
            String sourceKey,
            String scopePrefix,
            MultipartFile file,
            byte[] bytes,
            ObjectStorageWriteOptions storageOptions) {
        String ext = resolveFileExt(file);
        ObjectStorageWriteOptions effectiveOptions = storageOptions == null
                ? ObjectStorageWriteOptions.publicObject(scopePrefix)
                : storageOptions;
        ObjectStorageFileWriteResult result = objectStorageFileWriteService.resolveOrStore(
                new ObjectStorageFileWriteRequest(
                        userId,
                        sourceType,
                        sourceKey,
                        file.getOriginalFilename(),
                        resolveStoredContentType(file),
                        ext,
                        (long) bytes.length,
                        bytes,
                        effectiveOptions));
        return new StoredFileResolution(result.stored(), result.publicUrl(), result.newlyCreated());
    }

    private Optional<ObjectStorageFile> resolveAvailableOwnedFileByCacheKey(Long userId, String cacheKey) {
        if (userId == null || userId <= 0 || cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        for (String sourceType : CACHE_KEY_SOURCE_TYPES) {
            Optional<ObjectStorageFile> matched = objectStorageFileRepository
                    .findAvailableByOwnerAndSource(userId, sourceType, cacheKey);
            if (matched.isPresent()) {
                return matched;
            }
        }
        return Optional.empty();
    }

    private byte[] downloadStoredFileBytes(ObjectStorageFile stored) {
        if (stored == null || stored.getObjectKey() == null || stored.getObjectKey().isBlank()) {
            if (stored != null && stored.getId() != null) {
                objectStorageFileRepository.markStale(stored.getId());
            }
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "源文件已被自动清理，请重新上传");
        }
        try {
            return r2Client.downloadBytes(resolveStorageChannel(stored), stored.getObjectKey());
        } catch (BusinessException e) {
            if (stored.getId() != null && ErrorCode.NOT_FOUND_ERROR.getCode() == e.getCode()) {
                objectStorageFileRepository.markStale(stored.getId());
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "源文件已被自动清理，请重新上传");
            }
            throw e;
        }
    }

    private StorageChannel resolveStorageChannel(ObjectStorageFile stored) {
        return stored != null && StorageChannel.isPrivate(stored.getStorageChannel())
                ? StorageChannel.PRIVATE
                : StorageChannel.PUBLIC;
    }

    private String resolveDuplicateFileName(String requestedName, String displayName, String originalName) {
        String fallback = firstNonBlank(displayName, originalName);
        String baseName = firstNonBlank(requestedName, fallback);
        if (baseName == null || baseName.isBlank()) {
            baseName = "copy";
        }
        String preferredExt = firstNonBlank(extractExtension(displayName), extractExtension(originalName));
        if ((preferredExt != null && !preferredExt.isBlank()) && extractExtension(baseName) == null) {
            baseName = baseName.trim() + preferredExt;
        }
        return baseName.trim();
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = fileName.trim();
        int idx = name.lastIndexOf('.');
        if (idx <= 0 || idx >= name.length() - 1) {
            return null;
        }
        return name.substring(idx);
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToMaxChars(String text, int maxChars) {
        if (text == null) {
            return null;
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private void populateStoredFile(ObjectStorageFile fileRecord,
            Long userId,
            String sourceType,
            String sourceKey,
            String objectKey,
            MultipartFile file,
            String ext,
            R2Client.UploadResult up) {
        fileRecord.setOwnerUserId(userId);
        fileRecord.setSourceType(sourceType);
        fileRecord.setSourceKey(sourceKey);
        fileRecord.setStorageType("r2");
        fileRecord.setStorageChannel(StorageChannel.PUBLIC.value());
        fileRecord.setVisibility(StorageVisibility.PUBLIC.value());
        fileRecord.setBucket(r2Properties.effectiveBucket());
        fileRecord.setObjectKey(objectKey);
        fileRecord.setPublicDomain(r2Properties.effectivePublicDomain());
        fileRecord.setPublicPath(objectKey);
        fileRecord.setMimeType(resolveStoredContentType(file));
        fileRecord.setFileExt(ext.replaceFirst("^\\.", ""));
        fileRecord.setSizeBytes(file.getSize());
        fileRecord.setOriginalName(file.getOriginalFilename());
        fileRecord.setStatus("available");
        fileRecord.setDeleted(false);
        if (up != null) {
            fileRecord.setEtag(up.etag());
            fileRecord.setVersionId(up.versionId());
            fileRecord.setLastModified(up.lastModified());
        } else {
            fileRecord.setEtag(null);
            fileRecord.setVersionId(null);
            fileRecord.setLastModified(null);
        }
    }

    private StoredObjectAvailability checkStoredObjectAvailability(ObjectStorageFile stored) {
        if (stored == null || stored.getObjectKey() == null || stored.getObjectKey().isBlank()) {
            return StoredObjectAvailability.MISSING;
        }
        try {
            return r2Client.exists(stored.getObjectKey())
                    ? StoredObjectAvailability.AVAILABLE
                    : StoredObjectAvailability.MISSING;
        } catch (Exception e) {
            log.warn("[FileService] failed to verify object existence, fallback to DB state: fileId={}, key={}, err={}",
                    stored.getId(), stored.getObjectKey(), e.getMessage());
            return StoredObjectAvailability.UNKNOWN;
        }
    }

    private boolean isReusableRecord(ObjectStorageFile stored) {
        return stored != null
                && !Boolean.TRUE.equals(stored.getDeleted())
                && "available".equalsIgnoreCase(stored.getStatus());
    }

    // cacheKey 是前端文件引用 key。真正的“上传内容去重”只在 uploadWithBytesInternal 中使用 bytes。
    private static final List<String> CACHE_KEY_SOURCE_TYPES =
            ObjectStorageFileSourceType.cacheKeyLookupValues();

    private String buildObjectKey(Long userId, String scopePrefix, String sourceKey, String extWithDot) {
        String prefix = r2Properties.effectivePathPrefix();
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String ext = (extWithDot == null || extWithDot.isBlank()) ? "" : extWithDot;

        String scope = (scopePrefix == null || scopePrefix.isBlank()) ? "uploads" : scopePrefix.trim();
        // 路径包含 userId 前缀，避免不同用户相同 hash 的 R2 对象冲突
        String base = scope + "/" + userId + "/" + date + "/" + sourceKey + ext;
        if (prefix == null || prefix.isBlank()) {
            return base;
        }
        return prefix.replaceAll("/$", "") + "/" + base;
    }

    private String resolveFileExt(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            int idx = name.lastIndexOf('.');
            if (idx >= 0 && idx < name.length() - 1) {
                String ext = name.substring(idx).trim();
                if (ext.length() <= 10) {
                    return ext.toLowerCase();
                }
            }
        }
        // 保底：尽量从 contentType 推断
        String ct = file.getContentType();
        if (ct == null) {
            return "";
        }
        String normalizedContentType = ct.toLowerCase(Locale.ROOT);
        return switch (normalizedContentType) {
            case "application/pdf" -> ".pdf";
            case "text/markdown", "text/x-markdown" -> ".md";
            case "text/plain" -> ".txt";
            case "application/json" -> ".json";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private String resolveStoredContentType(MultipartFile file) {
        return file == null ? null : file.getContentType();
    }

    private String guessKind(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && ct.toLowerCase().startsWith("image/")) {
            return "image";
        }
        if (ct != null) {
            String c = ct.toLowerCase(Locale.ROOT);
            if (c.equals("application/pdf")
                    || c.equals("text/markdown") || c.equals("text/x-markdown")
                    || c.equals("application/json")
                    || c.equals("text/plain")
                    || c.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || c.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    || c.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
                return "document";
            }
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.endsWith(".pdf") || n.endsWith(".md")
                    || n.endsWith(".json") || n.endsWith(".txt")
                    || n.endsWith(".docx") || n.endsWith(".xlsx") || n.endsWith(".pptx")) {
                return "document";
            }
        }
        return "file";
    }

    private static void validateProjectMaterialFile(MultipartFile file) {
        String contentType = file == null ? null : file.getContentType();
        String lowerName = file == null || file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(lowerName.endsWith(".deck")
                        || lowerName.endsWith(".deck.json")
                        || lowerName.endsWith(".deck-outline.json")
                        || lowerName.endsWith(".deck.outline")
                        || lowerName.endsWith(".mslides")
                        || lowerName.endsWith(".mslides.outline")
                        || mime.startsWith("application/vnd.deckwise.")
                        || mime.startsWith("application/vnd.paperpuppy.mslides"),
                ErrorCode.INVALID_FILE_TYPE, "幻灯片专用文件格式已停止支持");
        boolean supported = isProjectMaterialImage(contentType, lowerName)
                || "application/pdf".equalsIgnoreCase(contentType)
                || lowerName.endsWith(".pdf")
                || "text/markdown".equalsIgnoreCase(contentType)
                || "text/x-markdown".equalsIgnoreCase(contentType)
                || lowerName.endsWith(".md")
                || "application/json".equalsIgnoreCase(contentType)
                || lowerName.endsWith(".json")
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
                || lowerName.endsWith(".docx")
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equalsIgnoreCase(contentType)
                || lowerName.endsWith(".xlsx")
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equalsIgnoreCase(contentType)
                || lowerName.endsWith(".pptx")
                || "text/plain".equalsIgnoreCase(contentType)
                || lowerName.endsWith(".txt");
        ThrowUtils.throwIf(!supported, ErrorCode.INVALID_FILE_TYPE,
                "仅支持导入图片 / PDF / Markdown / JSON / Word(.docx) / Excel(.xlsx) / PowerPoint(.pptx) / 纯文本(.txt) 文件");
    }

    private static boolean isProjectMaterialImage(String contentType, String lowerName) {
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

    private String guessKindFromExt(String ext, String mimeType) {
        if (mimeType != null && mimeType.toLowerCase().startsWith("image/")) {
            return "image";
        }
        if (ext != null && ext.equalsIgnoreCase("pdf")) {
            return "document";
        }
        if (ext != null && (ext.equalsIgnoreCase("md") || ext.equalsIgnoreCase("json"))) {
            return "document";
        }
        return "file";
    }

    private String buildPublicUrl(ObjectStorageFile file) {
        if (file == null || file.getObjectKey() == null) {
            return null;
        }
        if (objectAccessUrlService != null) {
            if (StorageChannel.isPrivate(file.getStorageChannel())) {
                return objectAccessUrlService.privateFileUrl(file);
            }
            String publicUrl = objectAccessUrlService.publicUrl(file);
            if (publicUrl != null && !publicUrl.isBlank()) {
                return publicUrl;
            }
        }
        if (StorageChannel.isPrivate(file.getStorageChannel())) {
            return null;
        }
        if (file.getPublicDomain() != null && !file.getPublicDomain().isBlank() &&
                file.getPublicPath() != null && !file.getPublicPath().isBlank()) {
            return file.getPublicDomain().replaceAll("/$", "") + "/" + file.getPublicPath().replaceFirst("^/", "");
        }
        return r2Client.buildPublicUrl(resolveStorageChannel(file), file.getObjectKey());
    }

    private String buildAccessibleUrl(ProjectFileLinkView view) {
        if (view == null || view.getObjectKey() == null || view.getObjectKey().isBlank()) {
            return null;
        }
        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(view.getFileId());
        file.setBucket(view.getBucket());
        file.setObjectKey(view.getObjectKey());
        file.setPublicDomain(view.getPublicDomain());
        file.setPublicPath(view.getPublicPath());
        file.setStorageChannel(normalizedStorageChannel(view.getStorageChannel()));
        file.setVisibility(normalizedStorageVisibility(view.getVisibility()));
        return buildPublicUrl(file);
    }

    private String normalizedStorageChannel(String value) {
        String normalized = StorageChannel.normalize(value);
        return StorageChannel.isKnown(normalized) ? normalized : StorageChannel.PUBLIC.value();
    }

    private String normalizedStorageVisibility(String value) {
        String normalized = StorageVisibility.normalize(value);
        return StorageVisibility.isKnown(normalized) ? normalized : StorageVisibility.PUBLIC.value();
    }

    @Override
    public List<String> getFileFragments(Long fileId, int maxFragments) {
        return getFileFragments(fileId, 1, maxFragments);
    }

    @Override
    public List<String> getFileFragments(Long fileId, int startFragment, int maxFragments) {
        if (fileId == null || fileId <= 0) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(maxFragments, 50)); // 限制最大50个切片
        int start = Math.max(startFragment, 1);
        int startIdx = start - 1; // DB 内为 0-based
        return fileTextFragmentRepository.listFragmentContents(fileId, startIdx, limit);
    }

    @Override
    public int countFileFragments(Long fileId) {
        if (fileId == null || fileId <= 0) {
            return 0;
        }
        return fileTextFragmentRepository.countByFileId(fileId);
    }

    private record OfficeConversionConfig(Format inputFormat, String sourceName) {
    }

    private record UploadedMedia(
            String relativePathFromWorkingDirectory,
            String relativePathFromMediaRoot,
            String publicUrl) {
    }

    private record StoredFileResolution(ObjectStorageFile stored, String publicUrl, boolean newlyCreated) {
    }

    private record StoredObjectRef(StorageChannel channel, String objectKey) {
    }

    private enum StoredObjectAvailability {
        AVAILABLE,
        MISSING,
        UNKNOWN
    }
}
