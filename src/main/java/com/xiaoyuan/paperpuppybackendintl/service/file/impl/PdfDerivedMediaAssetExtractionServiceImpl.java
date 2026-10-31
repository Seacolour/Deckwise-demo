package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyuan.paperpuppybackendintl.config.PdfDerivedAssetsProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.entity.PdfDerivedMediaAssetCacheEntry;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ObjectStorageFileSourceType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileDerivedMediaAssetRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.PdfDerivedMediaAssetCacheRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.PdfDerivedMediaAssetExtractionService;
import com.xiaoyuan.paperpuppybackendintl.service.file.jgda.JgdaExtractedImageAsset;
import com.xiaoyuan.paperpuppybackendintl.service.file.jgda.JgdaImagePlacement;
import com.xiaoyuan.paperpuppybackendintl.service.file.jgda.JgdaPdfAssetClient;
import com.xiaoyuan.paperpuppybackendintl.service.file.jgda.JgdaPdfAssetExtractionResult;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageFileWriteRequest;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageFileWriteResult;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageFileWriteService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectStorageWriteOptions;
import com.xiaoyuan.paperpuppybackendintl.utils.HashUtils;
import com.xiaoyuan.paperpuppybackendintl.utils.file.DocxImageExtractor;
import com.xiaoyuan.paperpuppybackendintl.utils.file.PptxImageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfDerivedMediaAssetExtractionServiceImpl implements PdfDerivedMediaAssetExtractionService {

    private static final long MAX_DERIVED_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final int PERSIST_ASSET_CONCURRENCY = 4;
    private static final long EXTRACTION_LOCK_TTL_MINUTES = 30;
    private static final long EXTRACTION_RESULT_TTL_DAYS = 7;
    private static final String EXTRACTION_RESULT_EMPTY = "empty";
    private static final String EXTRACTION_RESULT_FAILED = "failed";
    private static final String PDF_EXTRACTION_KIND = "pdf";
    private static final String DOCX_EXTRACTION_KIND = "docx";
    private static final String PPTX_EXTRACTION_KIND = "pptx";
    private static final String DOCX_DERIVED_PROVIDER = "docx_package_media";
    private static final String DOCX_DERIVED_KIND = "docx_embedded_image";
    private static final List<String> DOCX_SUPPORTED_IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "gif");
    private static final String PPTX_DERIVED_PROVIDER = "pptx_package_media";
    private static final String PPTX_DERIVED_KIND = "pptx_embedded_image";
    private static final List<String> PPTX_SUPPORTED_IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "gif");
    private static final int PPTX_MIN_IMAGE_WIDTH = 512;
    private static final int PPTX_MIN_IMAGE_HEIGHT = 512;

    private final PdfDerivedAssetsProperties properties;
    private final JgdaPdfAssetClient jgdaPdfAssetClient;
    private final ObjectStorageFileRepository objectStorageFileRepository;
    private final ProjectFileLinkRepository projectFileLinkRepository;
    private final FileAccessGrantRepository fileAccessGrantRepository;
    private final FileDerivedMediaAssetRepository fileDerivedMediaAssetRepository;
    private final PdfDerivedMediaAssetCacheRepository pdfDerivedMediaAssetCacheRepository;
    private final RedisClient redisClient;
    private final R2Client r2Client;
    private final StorageR2Properties r2Properties;
    @Qualifier("storageExecutor")
    private final TaskExecutor storageExecutor;
    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;

    @Override
    public boolean requestExtractionAfterCommit(Long userId,
                                                Long projectId,
                                                ObjectStorageFile sourceFile,
                                                byte[] pdfBytes,
                                                String fileName) {
        if (!canRequest(userId, projectId, sourceFile) || pdfBytes == null || pdfBytes.length == 0) {
            return false;
        }
        if (fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0) {
            return false;
        }
        byte[] bytes = pdfBytes.clone();
        String sourceContentHash = HashUtils.sha256Hex(bytes);
        String extractorSignature = extractorSignature();
        if (linkCachedAssetsIfPresent(userId, projectId, sourceFile, sourceContentHash, extractorSignature)) {
            return false;
        }
        Runnable task = () -> extractAndPersist(
                userId,
                projectId,
                sourceFile,
                bytes,
                fileName,
                sourceContentHash,
                extractorSignature);
        runAfterCommit(task);
        return true;
    }


    @Override
    public boolean requestDocxExtractionAfterCommit(Long userId,
                                                    Long projectId,
                                                    ObjectStorageFile sourceFile,
                                                    byte[] docxBytes,
                                                    String fileName) {
        if (!canRequestDocx(userId, projectId, sourceFile) || docxBytes == null || docxBytes.length == 0) {
            return false;
        }
        if (fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0) {
            return false;
        }
        byte[] bytes = docxBytes.clone();
        String sourceContentHash = HashUtils.sha256Hex(bytes);
        String extractorSignature = docxExtractorSignature();
        if (linkCachedAssetsIfPresent(userId, projectId, sourceFile, sourceContentHash, extractorSignature)) {
            return false;
        }
        Runnable task = () -> extractAndPersistDocx(
                userId,
                projectId,
                sourceFile,
                bytes,
                fileName,
                sourceContentHash,
                extractorSignature);
        runAfterCommit(task);
        return true;
    }


    @Override
    public boolean requestPptxExtractionAfterCommit(Long userId,
                                                    Long projectId,
                                                    ObjectStorageFile sourceFile,
                                                    byte[] pptxBytes,
                                                    String fileName) {
        if (!canRequestPptx(userId, projectId, sourceFile) || pptxBytes == null || pptxBytes.length == 0) {
            return false;
        }
        if (fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0) {
            return false;
        }
        byte[] bytes = pptxBytes.clone();
        String sourceContentHash = HashUtils.sha256Hex(bytes);
        String extractorSignature = pptxExtractorSignature();
        if (linkCachedAssetsIfPresent(userId, projectId, sourceFile, sourceContentHash, extractorSignature)) {
            return false;
        }
        Runnable task = () -> extractAndPersistPptx(
                userId,
                projectId,
                sourceFile,
                bytes,
                fileName,
                sourceContentHash,
                extractorSignature);
        runAfterCommit(task);
        return true;
    }



    private boolean canRequest(Long userId, Long projectId, ObjectStorageFile sourceFile) {
        return properties.enabled()
                && (properties.useLocalExtractor() || !properties.extractorBaseUrl().isBlank())
                && userId != null && userId > 0
                && projectId != null && projectId > 0
                && isAvailablePdf(sourceFile);
    }

    private boolean canRequestDocx(Long userId, Long projectId, ObjectStorageFile sourceFile) {
        return properties.enabled()
                && userId != null && userId > 0
                && projectId != null && projectId > 0
                && isAvailableDocx(sourceFile);
    }

    private boolean canRequestPptx(Long userId, Long projectId, ObjectStorageFile sourceFile) {
        return properties.enabled()
                && userId != null && userId > 0
                && projectId != null && projectId > 0
                && isAvailablePptx(sourceFile);
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storageExecutor.execute(task);
                }
            });
        } else {
            storageExecutor.execute(task);
        }
    }

    private void extractAndPersist(Long userId,
                                   Long projectId,
                                   ObjectStorageFile sourceFile,
                                   byte[] pdfBytes,
                                   String fileName,
                                   String sourceContentHash,
                                   String extractorSignature) {
        if (!canRequest(userId, projectId, sourceFile)
                || fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0) {
            return;
        }
        String contentHash = firstNonBlank(sourceContentHash, HashUtils.sha256Hex(pdfBytes));
        String signature = firstNonBlank(extractorSignature, extractorSignature());
        if (linkCachedAssetsIfPresent(userId, projectId, sourceFile, contentHash, signature)) {
            return;
        }
        String lockKey = extractionLockKey(userId, contentHash, signature);
        String lockToken = UUID.randomUUID().toString();
        if (!redisClient.setIfAbsent(lockKey, lockToken, EXTRACTION_LOCK_TTL_MINUTES, TimeUnit.MINUTES)) {
            log.debug("[PdfDerivedAssets] extraction already running: ownerUserId={}, sourceHash={}, signature={}",
                    userId,
                    shortHash(contentHash),
                    signatureHash(signature));
            return;
        }
        try {
            if (fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0
                    || linkCachedAssetsIfPresent(userId, projectId, sourceFile, contentHash, signature)) {
                return;
            }
            JgdaPdfAssetExtractionResult result = jgdaPdfAssetClient.extract(
                    firstNonBlank(fileName, sourceFile.getOriginalName(), "input.pdf"),
                    pdfBytes);
            int saved = persistAssets(userId, projectId, sourceFile, result, contentHash, signature);
            markTerminalIfNoSavedAssets(projectId, sourceFile.getId(), ExtractionKind.PDF, signature,
                    result.getAssets().size(), saved);
            log.info("[PdfDerivedAssets] extraction finished: projectId={}, sourceFileId={}, sourceHash={}, assets={}, saved={}",
                    projectId,
                    sourceFile.getId(),
                    shortHash(contentHash),
                    result.getAssets().size(),
                    saved);
        } catch (Exception e) {
            markExtractionResult(projectId, sourceFile.getId(), ExtractionKind.PDF, signature, EXTRACTION_RESULT_FAILED);
            log.warn("[PdfDerivedAssets] extraction failed: projectId={}, sourceFileId={}, err={}",
                    projectId, sourceFile.getId(), e.getMessage(), e);
        } finally {
            redisClient.deleteIfValue(lockKey, lockToken);
        }
    }

    private void extractAndPersistDocx(Long userId,
                                       Long projectId,
                                       ObjectStorageFile sourceFile,
                                       byte[] docxBytes,
                                       String fileName,
                                       String sourceContentHash,
                                       String extractorSignature) {
        long startedAt = System.nanoTime();
        if (!canRequestDocx(userId, projectId, sourceFile)
                || docxBytes == null
                || docxBytes.length == 0
                || fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0) {
            return;
        }
        String contentHash = firstNonBlank(sourceContentHash, HashUtils.sha256Hex(docxBytes));
        String signature = firstNonBlank(extractorSignature, docxExtractorSignature());
        if (linkCachedAssetsIfPresent(userId, projectId, sourceFile, contentHash, signature)) {
            return;
        }
        String lockKey = docxExtractionLockKey(userId, contentHash, signature);
        String lockToken = UUID.randomUUID().toString();
        if (!redisClient.setIfAbsent(lockKey, lockToken, EXTRACTION_LOCK_TTL_MINUTES, TimeUnit.MINUTES)) {
            log.debug("[DocxDerivedAssets] extraction already running: ownerUserId={}, sourceHash={}, signature={}",
                    userId,
                    shortHash(contentHash),
                    signatureHash(signature));
            return;
        }
        Path tempRoot = null;
        try {
            if (fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0
                    || linkCachedAssetsIfPresent(userId, projectId, sourceFile, contentHash, signature)) {
                return;
            }
            tempRoot = Files.createTempDirectory("paperpuppy-docx-assets-");
            Path docxPath = tempRoot.resolve("source.docx");
            Files.write(docxPath, docxBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Path outputDir = tempRoot.resolve("extracted");
            DocxImageExtractor.ExtractionResult result = new DocxImageExtractor().extract(docxPath, outputDir);
            int saved = persistDocxAssets(userId, projectId, sourceFile, result, outputDir, contentHash, signature);
            markTerminalIfNoSavedAssets(projectId, sourceFile.getId(), ExtractionKind.DOCX, signature,
                    result.images().size(), saved);
            log.info("[DocxDerivedAssets] extraction finished: projectId={}, sourceFileId={}, sourceHash={}, assets={}, saved={}, elapsedMs={}",
                    projectId,
                    sourceFile.getId(),
                    shortHash(contentHash),
                    result.images().size(),
                    saved,
                    elapsedMs(startedAt));
        } catch (Exception e) {
            markExtractionResult(projectId, sourceFile.getId(), ExtractionKind.DOCX, signature, EXTRACTION_RESULT_FAILED);
            log.warn("[DocxDerivedAssets] extraction failed: projectId={}, sourceFileId={}, err={}",
                    projectId, sourceFile.getId(), e.getMessage(), e);
        } finally {
            redisClient.deleteIfValue(lockKey, lockToken);
            cleanupDirectory(tempRoot);
        }
    }

    private void extractAndPersistPptx(Long userId,
                                       Long projectId,
                                       ObjectStorageFile sourceFile,
                                       byte[] pptxBytes,
                                       String fileName,
                                       String sourceContentHash,
                                       String extractorSignature) {
        long startedAt = System.nanoTime();
        if (!canRequestPptx(userId, projectId, sourceFile)
                || pptxBytes == null
                || pptxBytes.length == 0
                || fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0) {
            return;
        }
        String contentHash = firstNonBlank(sourceContentHash, HashUtils.sha256Hex(pptxBytes));
        String signature = firstNonBlank(extractorSignature, pptxExtractorSignature());
        if (linkCachedAssetsIfPresent(userId, projectId, sourceFile, contentHash, signature)) {
            return;
        }
        String lockKey = pptxExtractionLockKey(userId, contentHash, signature);
        String lockToken = UUID.randomUUID().toString();
        if (!redisClient.setIfAbsent(lockKey, lockToken, EXTRACTION_LOCK_TTL_MINUTES, TimeUnit.MINUTES)) {
            log.debug("[PptxDerivedAssets] extraction already running: ownerUserId={}, sourceHash={}, signature={}",
                    userId,
                    shortHash(contentHash),
                    signatureHash(signature));
            return;
        }
        Path tempRoot = null;
        try {
            if (fileDerivedMediaAssetRepository.countAvailableByProjectAndSourceFileId(projectId, sourceFile.getId()) > 0
                    || linkCachedAssetsIfPresent(userId, projectId, sourceFile, contentHash, signature)) {
                return;
            }
            tempRoot = Files.createTempDirectory("paperpuppy-pptx-assets-");
            Path pptxPath = tempRoot.resolve("source.pptx");
            Files.write(pptxPath, pptxBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Path outputDir = tempRoot.resolve("extracted");
            PptxImageExtractor.ExtractionResult result = new PptxImageExtractor().extract(pptxPath, outputDir);
            int saved = persistPptxAssets(userId, projectId, sourceFile, result, outputDir, contentHash, signature);
            markTerminalIfNoSavedAssets(projectId, sourceFile.getId(), ExtractionKind.PPTX, signature,
                    result.images().size(), saved);
            log.info("[PptxDerivedAssets] extraction finished: projectId={}, sourceFileId={}, sourceHash={}, assets={}, saved={}, elapsedMs={}",
                    projectId,
                    sourceFile.getId(),
                    shortHash(contentHash),
                    result.images().size(),
                    saved,
                    elapsedMs(startedAt));
        } catch (Exception e) {
            markExtractionResult(projectId, sourceFile.getId(), ExtractionKind.PPTX, signature, EXTRACTION_RESULT_FAILED);
            log.warn("[PptxDerivedAssets] extraction failed: projectId={}, sourceFileId={}, err={}",
                    projectId, sourceFile.getId(), e.getMessage(), e);
        } finally {
            redisClient.deleteIfValue(lockKey, lockToken);
            cleanupDirectory(tempRoot);
        }
    }

    private int persistAssets(Long userId,
                              Long projectId,
                              ObjectStorageFile sourceFile,
                              JgdaPdfAssetExtractionResult result,
                              String sourceContentHash,
                              String extractorSignature) {
        if (result == null || result.getAssets().isEmpty()) {
            return 0;
        }
        long startedAt = System.nanoTime();
        List<JgdaExtractedImageAsset> assets = selectAssetsToPersist(result.getAssets());
        if (assets.isEmpty()) {
            return 0;
        }
        int concurrency = Math.min(PERSIST_ASSET_CONCURRENCY, assets.size());
        log.debug("[PdfDerivedAssets] persist assets started: projectId={}, sourceFileId={}, assets={}, concurrency={}",
                projectId, sourceFile.getId(), assets.size(), concurrency);
        int saved;
        if (concurrency <= 1 || assets.size() <= 1) {
            saved = 0;
            for (JgdaExtractedImageAsset asset : assets) {
                if (persistOneAsset(userId, projectId, sourceFile, result, asset, sourceContentHash, extractorSignature)) {
                    saved++;
                }
            }
        } else {
            try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
                List<CompletableFuture<Boolean>> futures = assets.stream()
                        .map(asset -> CompletableFuture.supplyAsync(
                                () -> persistOneAsset(userId, projectId, sourceFile, result, asset, sourceContentHash, extractorSignature),
                                executor))
                        .toList();
                saved = 0;
                for (CompletableFuture<Boolean> future : futures) {
                    if (Boolean.TRUE.equals(future.join())) {
                        saved++;
                    }
                }
            }
        }
        log.info("[PdfDerivedAssets] persist assets finished: projectId={}, sourceFileId={}, saved={}, elapsedMs={}",
                projectId, sourceFile.getId(), saved, elapsedMs(startedAt));
        return saved;
    }

    private int persistDocxAssets(Long userId,
                                  Long projectId,
                                  ObjectStorageFile sourceFile,
                                  DocxImageExtractor.ExtractionResult result,
                                  Path outputDir,
                                  String sourceContentHash,
                                  String extractorSignature) {
        if (result == null || result.images().isEmpty()) {
            return 0;
        }
        long startedAt = System.nanoTime();
        List<DocxImageExtractor.ImageAsset> assets = selectDocxAssetsToPersist(result.images());
        if (assets.isEmpty()) {
            return 0;
        }
        int concurrency = Math.min(PERSIST_ASSET_CONCURRENCY, assets.size());
        log.debug("[DocxDerivedAssets] persist assets started: projectId={}, sourceFileId={}, assets={}, concurrency={}",
                projectId, sourceFile.getId(), assets.size(), concurrency);
        int saved;
        if (concurrency <= 1 || assets.size() <= 1) {
            saved = 0;
            for (int i = 0; i < assets.size(); i++) {
                if (persistOneDocxAsset(userId, projectId, sourceFile, result, outputDir, assets.get(i), i + 1,
                        sourceContentHash, extractorSignature)) {
                    saved++;
                }
            }
        } else {
            try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < assets.size(); i++) {
                    DocxImageExtractor.ImageAsset asset = assets.get(i);
                    int assetIndex = i + 1;
                    futures.add(CompletableFuture.supplyAsync(
                            () -> persistOneDocxAsset(userId, projectId, sourceFile, result, outputDir, asset, assetIndex,
                                    sourceContentHash, extractorSignature),
                            executor));
                }
                saved = 0;
                for (CompletableFuture<Boolean> future : futures) {
                    if (Boolean.TRUE.equals(future.join())) {
                        saved++;
                    }
                }
            }
        }
        log.info("[DocxDerivedAssets] persist assets finished: projectId={}, sourceFileId={}, saved={}, elapsedMs={}",
                projectId, sourceFile.getId(), saved, elapsedMs(startedAt));
        return saved;
    }

    private int persistPptxAssets(Long userId,
                                  Long projectId,
                                  ObjectStorageFile sourceFile,
                                  PptxImageExtractor.ExtractionResult result,
                                  Path outputDir,
                                  String sourceContentHash,
                                  String extractorSignature) {
        if (result == null || result.images().isEmpty()) {
            return 0;
        }
        long startedAt = System.nanoTime();
        List<PptxImageExtractor.ImageAsset> assets = selectPptxAssetsToPersist(result.images());
        if (assets.isEmpty()) {
            return 0;
        }
        int concurrency = Math.min(PERSIST_ASSET_CONCURRENCY, assets.size());
        log.debug("[PptxDerivedAssets] persist assets started: projectId={}, sourceFileId={}, assets={}, concurrency={}",
                projectId, sourceFile.getId(), assets.size(), concurrency);
        int saved;
        if (concurrency <= 1 || assets.size() <= 1) {
            saved = 0;
            for (int i = 0; i < assets.size(); i++) {
                if (persistOnePptxAsset(userId, projectId, sourceFile, result, outputDir, assets.get(i), i + 1,
                        sourceContentHash, extractorSignature)) {
                    saved++;
                }
            }
        } else {
            try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
                List<CompletableFuture<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < assets.size(); i++) {
                    PptxImageExtractor.ImageAsset asset = assets.get(i);
                    int assetIndex = i + 1;
                    futures.add(CompletableFuture.supplyAsync(
                            () -> persistOnePptxAsset(userId, projectId, sourceFile, result, outputDir, asset, assetIndex,
                                    sourceContentHash, extractorSignature),
                            executor));
                }
                saved = 0;
                for (CompletableFuture<Boolean> future : futures) {
                    if (Boolean.TRUE.equals(future.join())) {
                        saved++;
                    }
                }
            }
        }
        log.info("[PptxDerivedAssets] persist assets finished: projectId={}, sourceFileId={}, saved={}, elapsedMs={}",
                projectId, sourceFile.getId(), saved, elapsedMs(startedAt));
        return saved;
    }

    private List<JgdaExtractedImageAsset> selectAssetsToPersist(List<JgdaExtractedImageAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<JgdaExtractedImageAsset> selected = new ArrayList<>();
        for (JgdaExtractedImageAsset asset : assets) {
            if (selected.size() >= properties.maxAssets()) {
                break;
            }
            if (!isUsableAsset(asset) || !seen.add(assetKey(asset))) {
                continue;
            }
            selected.add(asset);
        }
        return List.copyOf(selected);
    }

    private List<DocxImageExtractor.ImageAsset> selectDocxAssetsToPersist(List<DocxImageExtractor.ImageAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<DocxImageExtractor.ImageAsset> candidates = assets.stream()
                .filter(this::isUsableDocxAsset)
                .sorted(Comparator
                        .comparing((DocxImageExtractor.ImageAsset asset) -> !asset.referenced())
                        .thenComparing(asset -> Objects.toString(asset.packagePath(), "")))
                .toList();
        List<DocxImageExtractor.ImageAsset> selected = new ArrayList<>();
        for (DocxImageExtractor.ImageAsset asset : candidates) {
            if (selected.size() >= properties.maxAssets()) {
                break;
            }
            if (!seen.add(docxAssetKey(asset))) {
                continue;
            }
            selected.add(asset);
        }
        return List.copyOf(selected);
    }

    private List<PptxImageExtractor.ImageAsset> selectPptxAssetsToPersist(List<PptxImageExtractor.ImageAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<PptxImageExtractor.ImageAsset> candidates = assets.stream()
                .filter(this::isUsablePptxAsset)
                .sorted(Comparator
                        .comparing((PptxImageExtractor.ImageAsset asset) -> !asset.referenced())
                        .thenComparing(Comparator.comparingLong(PdfDerivedMediaAssetExtractionServiceImpl::pptxPixelArea).reversed())
                        .thenComparing(Comparator.comparingLong(PptxImageExtractor.ImageAsset::sizeBytes).reversed())
                        .thenComparing(Comparator.comparingInt(PptxImageExtractor.ImageAsset::useCount).reversed())
                        .thenComparing(asset -> Objects.toString(asset.packagePath(), "")))
                .toList();
        List<PptxImageExtractor.ImageAsset> selected = new ArrayList<>();
        for (PptxImageExtractor.ImageAsset asset : candidates) {
            if (selected.size() >= properties.maxAssets()) {
                break;
            }
            if (!seen.add(pptxAssetKey(asset))) {
                continue;
            }
            selected.add(asset);
        }
        return List.copyOf(selected);
    }

    private boolean persistOneAsset(Long userId,
                                    Long projectId,
                                    ObjectStorageFile sourceFile,
                                    JgdaPdfAssetExtractionResult result,
                                    JgdaExtractedImageAsset asset,
                                    String sourceContentHash,
                                    String extractorSignature) {
        long startedAt = System.nanoTime();
        try {
            byte[] imageBytes = Files.readAllBytes(Path.of(asset.getPath()).toAbsolutePath().normalize());
            if (imageBytes.length == 0 || imageBytes.length > MAX_DERIVED_IMAGE_BYTES) {
                return false;
            }
            long storeStartedAt = System.nanoTime();
            ObjectStorageFile derivedFile = storeDerivedImageFile(
                    userId,
                    projectId,
                    sourceFile,
                    asset,
                    imageBytes,
                    sourceContentHash,
                    extractorSignature);
            long storeMs = elapsedMs(storeStartedAt);
            if (derivedFile == null || derivedFile.getId() == null) {
                return false;
            }
            long rowStartedAt = System.nanoTime();
            Optional<PdfDerivedMediaAssetCacheEntry> cacheEntry = pdfDerivedMediaAssetCacheRepository.insertAvailable(
                    toCacheEntry(userId, sourceContentHash, extractorSignature, sourceFile, derivedFile, asset, result));
            boolean linked = cacheEntry
                    .map(entry -> linkCacheAssetsToProject(userId, projectId, sourceFile, List.of(entry)) > 0)
                    .orElse(false);
            log.debug("[PdfDerivedAssets] persist asset finished: sourceFileId={}, page={}, assetIndex={}, bytes={}, storeMs={}, rowMs={}, elapsedMs={}, linked={}",
                    sourceFile.getId(),
                    asset.getPageNumber(),
                    asset.getAssetIndex(),
                    imageBytes.length,
                    storeMs,
                    elapsedMs(rowStartedAt),
                    elapsedMs(startedAt),
                    linked);
            return linked;
        } catch (Exception e) {
            log.warn("[PdfDerivedAssets] failed to persist extracted asset, projectId={}, sourceFileId={}, asset={}, err={}",
                    projectId, sourceFile.getId(), asset == null ? "" : asset.getFileName(), e.getMessage());
            return false;
        }
    }

    private boolean persistOneDocxAsset(Long userId,
                                        Long projectId,
                                        ObjectStorageFile sourceFile,
                                        DocxImageExtractor.ExtractionResult result,
                                        Path outputDir,
                                        DocxImageExtractor.ImageAsset asset,
                                        int assetIndex,
                                        String sourceContentHash,
                                        String extractorSignature) {
        long startedAt = System.nanoTime();
        try {
            Path outputRoot = outputDir.toAbsolutePath().normalize();
            Path imagePath = outputRoot.resolve(asset.outputRelativePath()).normalize();
            if (!imagePath.startsWith(outputRoot)) {
                return false;
            }
            byte[] imageBytes = Files.readAllBytes(imagePath);
            if (imageBytes.length == 0 || imageBytes.length > MAX_DERIVED_IMAGE_BYTES) {
                return false;
            }
            long storeStartedAt = System.nanoTime();
            ObjectStorageFile derivedFile = storeDocxDerivedImageFile(
                    userId,
                    sourceFile,
                    asset,
                    imageBytes,
                    sourceContentHash,
                    extractorSignature);
            long storeMs = elapsedMs(storeStartedAt);
            if (derivedFile == null || derivedFile.getId() == null) {
                return false;
            }
            long rowStartedAt = System.nanoTime();
            Optional<PdfDerivedMediaAssetCacheEntry> cacheEntry = pdfDerivedMediaAssetCacheRepository.insertAvailable(
                    toDocxCacheEntry(userId, sourceContentHash, extractorSignature, sourceFile, derivedFile, asset, assetIndex, result));
            boolean linked = cacheEntry
                    .map(entry -> linkCacheAssetsToProject(userId, projectId, sourceFile, List.of(entry)) > 0)
                    .orElse(false);
            log.debug("[DocxDerivedAssets] persist asset finished: sourceFileId={}, assetIndex={}, packagePath={}, bytes={}, storeMs={}, rowMs={}, elapsedMs={}, linked={}",
                    sourceFile.getId(),
                    assetIndex,
                    asset.packagePath(),
                    imageBytes.length,
                    storeMs,
                    elapsedMs(rowStartedAt),
                    elapsedMs(startedAt),
                    linked);
            return linked;
        } catch (Exception e) {
            log.warn("[DocxDerivedAssets] failed to persist extracted asset, projectId={}, sourceFileId={}, asset={}, err={}",
                    projectId, sourceFile.getId(), asset == null ? "" : asset.fileName(), e.getMessage());
            return false;
        }
    }

    private boolean persistOnePptxAsset(Long userId,
                                        Long projectId,
                                        ObjectStorageFile sourceFile,
                                        PptxImageExtractor.ExtractionResult result,
                                        Path outputDir,
                                        PptxImageExtractor.ImageAsset asset,
                                        int assetIndex,
                                        String sourceContentHash,
                                        String extractorSignature) {
        long startedAt = System.nanoTime();
        try {
            Path outputRoot = outputDir.toAbsolutePath().normalize();
            Path imagePath = outputRoot.resolve(asset.outputRelativePath()).normalize();
            if (!imagePath.startsWith(outputRoot)) {
                return false;
            }
            byte[] imageBytes = Files.readAllBytes(imagePath);
            if (imageBytes.length == 0 || imageBytes.length > MAX_DERIVED_IMAGE_BYTES) {
                return false;
            }
            long storeStartedAt = System.nanoTime();
            ObjectStorageFile derivedFile = storePptxDerivedImageFile(
                    userId,
                    sourceFile,
                    asset,
                    imageBytes,
                    sourceContentHash,
                    extractorSignature);
            long storeMs = elapsedMs(storeStartedAt);
            if (derivedFile == null || derivedFile.getId() == null) {
                return false;
            }
            long rowStartedAt = System.nanoTime();
            Optional<PdfDerivedMediaAssetCacheEntry> cacheEntry = pdfDerivedMediaAssetCacheRepository.insertAvailable(
                    toPptxCacheEntry(userId, sourceContentHash, extractorSignature, sourceFile, derivedFile, asset, assetIndex, result));
            boolean linked = cacheEntry
                    .map(entry -> linkCacheAssetsToProject(userId, projectId, sourceFile, List.of(entry)) > 0)
                    .orElse(false);
            log.debug("[PptxDerivedAssets] persist asset finished: sourceFileId={}, assetIndex={}, packagePath={}, bytes={}, storeMs={}, rowMs={}, elapsedMs={}, linked={}",
                    sourceFile.getId(),
                    assetIndex,
                    asset.packagePath(),
                    imageBytes.length,
                    storeMs,
                    elapsedMs(rowStartedAt),
                    elapsedMs(startedAt),
                    linked);
            return linked;
        } catch (Exception e) {
            log.warn("[PptxDerivedAssets] failed to persist extracted asset, projectId={}, sourceFileId={}, asset={}, err={}",
                    projectId, sourceFile.getId(), asset == null ? "" : asset.fileName(), e.getMessage());
            return false;
        }
    }

    private ObjectStorageFile storeDerivedImageFile(Long userId,
                                                    Long projectId,
                                                    ObjectStorageFile sourceFile,
                                                    JgdaExtractedImageAsset asset,
                                                    byte[] imageBytes,
                                                    String sourceContentHash,
                                                    String extractorSignature) {
        String imageHash = firstNonBlank(asset.getSha256(), HashUtils.sha256Hex(imageBytes));
        String sourceKey = HashUtils.sha256Hex("pdf-derived:" + userId + ":" + sourceContentHash + ":"
                + signatureHash(extractorSignature) + ":"
                + asset.getKind() + ":" + asset.getPageNumber() + ":" + asset.getAssetIndex() + ":" + imageHash);
        String sourceType = ObjectStorageFileSourceType.DERIVED.value();
        String ext = normalizeImageExt(asset.getFormat(), asset.getFileName());
        ObjectStorageFile stored = writePrivateDerivedImage(
                userId,
                sourceType,
                sourceKey,
                firstNonBlank(asset.getFileName(), "pdf-image-" + sourceFile.getId() + "." + ext),
                contentType(ext),
                ext,
                imageBytes,
                "pdf-assets");
        if (stored == null || stored.getId() == null) {
            return null;
        }
        projectFileLinkRepository.upsertLink(projectId, stored.getId(), userId, stored.getOriginalName(), "image");
        fileAccessGrantRepository.upsertLink(userId, stored.getId(), projectId);
        return stored;
    }

    private ObjectStorageFile storeDocxDerivedImageFile(Long userId,
                                                        ObjectStorageFile sourceFile,
                                                        DocxImageExtractor.ImageAsset asset,
                                                        byte[] imageBytes,
                                                        String sourceContentHash,
                                                        String extractorSignature) {
        String imageHash = firstNonBlank(asset.sha256(), HashUtils.sha256Hex(imageBytes));
        String ext = normalizeDocxImageExt(asset.extension(), asset.fileName(), asset.contentType());
        String sourceKey = HashUtils.sha256Hex("docx-derived:" + userId + ":" + sourceContentHash + ":"
                + signatureHash(extractorSignature) + ":"
                + asset.packagePath() + ":" + imageHash);
        String sourceType = ObjectStorageFileSourceType.DERIVED.value();
        String contentType = contentTypeForDocxExt(ext);
        ObjectStorageFile stored = writePrivateDerivedImage(
                userId,
                sourceType,
                sourceKey,
                firstNonBlank(asset.fileName(), "docx-image-" + sourceFile.getId() + "." + ext),
                contentType,
                ext,
                imageBytes,
                "docx-assets");
        return stored == null || stored.getId() == null ? null : stored;
    }

    private ObjectStorageFile storePptxDerivedImageFile(Long userId,
                                                        ObjectStorageFile sourceFile,
                                                        PptxImageExtractor.ImageAsset asset,
                                                        byte[] imageBytes,
                                                        String sourceContentHash,
                                                        String extractorSignature) {
        String imageHash = firstNonBlank(asset.sha256(), HashUtils.sha256Hex(imageBytes));
        String ext = normalizePptxImageExt(asset.extension(), asset.fileName(), asset.contentType());
        String sourceKey = HashUtils.sha256Hex("pptx-derived:" + userId + ":" + sourceContentHash + ":"
                + signatureHash(extractorSignature) + ":" 
                + asset.packagePath() + ":" + imageHash);
        String sourceType = ObjectStorageFileSourceType.DERIVED.value();
        String contentType = contentTypeForPptxExt(ext);
        ObjectStorageFile stored = writePrivateDerivedImage(
                userId,
                sourceType,
                sourceKey,
                firstNonBlank(asset.fileName(), "pptx-image-" + sourceFile.getId() + "." + ext),
                contentType,
                ext,
                imageBytes,
                "pptx-assets");
        return stored == null || stored.getId() == null ? null : stored;
    }

    private ObjectStorageFile writePrivateDerivedImage(Long userId,
                                                       String sourceType,
                                                       String sourceKey,
                                                       String originalName,
                                                       String contentType,
                                                       String ext,
                                                       byte[] imageBytes,
                                                       String scopePrefix) {
        ObjectStorageFileWriteResult result = new ObjectStorageFileWriteService(
                r2Client,
                r2Properties,
                objectStorageFileRepository)
                .resolveOrStore(new ObjectStorageFileWriteRequest(
                        userId,
                        sourceType,
                        sourceKey,
                        originalName,
                        contentType,
                        ext,
                        (long) imageBytes.length,
                        imageBytes,
                        ObjectStorageWriteOptions.privateObject(scopePrefix)));
        return result == null ? null : result.stored();
    }

    private PdfDerivedMediaAssetCacheEntry toCacheEntry(Long userId,
                                                       String sourceContentHash,
                                                       String extractorSignature,
                                                       ObjectStorageFile sourceFile,
                                                       ObjectStorageFile derivedFile,
                                                       JgdaExtractedImageAsset asset,
                                                       JgdaPdfAssetExtractionResult result) {
        PdfDerivedMediaAssetCacheEntry row = new PdfDerivedMediaAssetCacheEntry();
        row.setOwnerUserId(userId);
        row.setSourceContentHash(sourceContentHash);
        row.setSourceFileId(sourceFile.getId());
        row.setExtractorSignature(extractorSignature);
        row.setDerivedFileId(derivedFile.getId());
        row.setKind(mapKind(asset.getKind()));
        row.setProvider("jgda");
        row.setPageNumber(asset.getPageNumber());
        row.setAssetIndex(asset.getAssetIndex());
        row.setBbox(toBboxJson(asset.getPlacement()));
        row.setCaption(resolveCaption(asset));
        row.setDescription(resolveDescription(asset));
        row.setContentHash(firstNonBlank(asset.getSha256(), derivedFile.getSourceKey()));
        row.setScore(score(asset));
        row.setStatus("available");
        row.setMetadata(toMetadata(asset, result));
        row.setDeleted(false);
        return row;
    }

    private PdfDerivedMediaAssetCacheEntry toDocxCacheEntry(Long userId,
                                                           String sourceContentHash,
                                                           String extractorSignature,
                                                           ObjectStorageFile sourceFile,
                                                           ObjectStorageFile derivedFile,
                                                           DocxImageExtractor.ImageAsset asset,
                                                           int assetIndex,
                                                           DocxImageExtractor.ExtractionResult result) {
        PdfDerivedMediaAssetCacheEntry row = new PdfDerivedMediaAssetCacheEntry();
        row.setOwnerUserId(userId);
        row.setSourceContentHash(sourceContentHash);
        row.setSourceFileId(sourceFile.getId());
        row.setExtractorSignature(extractorSignature);
        row.setDerivedFileId(derivedFile.getId());
        row.setKind(DOCX_DERIVED_KIND);
        row.setProvider(DOCX_DERIVED_PROVIDER);
        row.setPageNumber(1);
        row.setAssetIndex(Math.max(1, assetIndex));
        row.setBbox(null);
        row.setCaption(resolveDocxCaption(asset));
        row.setDescription(resolveDocxDescription(asset));
        row.setContentHash(firstNonBlank(asset.sha256(), derivedFile.getSourceKey()));
        row.setScore(docxScore(asset));
        row.setStatus("available");
        row.setMetadata(toDocxMetadata(asset, result));
        row.setDeleted(false);
        return row;
    }

    private PdfDerivedMediaAssetCacheEntry toPptxCacheEntry(Long userId,
                                                           String sourceContentHash,
                                                           String extractorSignature,
                                                           ObjectStorageFile sourceFile,
                                                           ObjectStorageFile derivedFile,
                                                           PptxImageExtractor.ImageAsset asset,
                                                           int assetIndex,
                                                           PptxImageExtractor.ExtractionResult result) {
        PdfDerivedMediaAssetCacheEntry row = new PdfDerivedMediaAssetCacheEntry();
        row.setOwnerUserId(userId);
        row.setSourceContentHash(sourceContentHash);
        row.setSourceFileId(sourceFile.getId());
        row.setExtractorSignature(extractorSignature);
        row.setDerivedFileId(derivedFile.getId());
        row.setKind(PPTX_DERIVED_KIND);
        row.setProvider(PPTX_DERIVED_PROVIDER);
        row.setPageNumber(resolvePptxPageNumber(asset));
        row.setAssetIndex(Math.max(1, assetIndex));
        row.setBbox(null);
        row.setCaption(resolvePptxCaption(asset));
        row.setDescription(resolvePptxDescription(asset));
        row.setContentHash(firstNonBlank(asset.sha256(), derivedFile.getSourceKey()));
        row.setScore(pptxScore(asset));
        row.setStatus("available");
        row.setMetadata(toPptxMetadata(asset, result));
        row.setDeleted(false);
        return row;
    }

    private JSONB toBboxJson(JgdaImagePlacement placement) {
        if (placement == null) {
            return null;
        }
        ObjectNode bbox = objectMapper.createObjectNode();
        bbox.put("left", placement.getNormalizedLeft());
        bbox.put("top", placement.getNormalizedTop());
        bbox.put("width", placement.getNormalizedWidth());
        bbox.put("height", placement.getNormalizedHeight());
        bbox.put("pageWidth", placement.getPageWidth());
        bbox.put("pageHeight", placement.getPageHeight());
        return JSONB.valueOf(bbox.toString());
    }

    private JSONB toMetadata(JgdaExtractedImageAsset asset, JgdaPdfAssetExtractionResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("extractor", "jgda");
        node.put("extractorOutputDir", result.getOutputDir() == null ? "" : result.getOutputDir());
        node.put("assetKind", asset.getKind() == null ? "" : asset.getKind());
        node.put("sourceName", asset.getSourceName() == null ? "" : asset.getSourceName());
        node.put("description", asset.getDescription() == null ? "" : asset.getDescription());
        node.put("width", asset.getWidth());
        node.put("height", asset.getHeight());
        node.put("fileSizeBytes", asset.getFileSizeBytes());
        node.put("relativePath", asset.getRelativePath() == null ? "" : asset.getRelativePath());
        node.put("documentAiProcessedPageCount", result.getDocumentAiProcessedPageCount());
        node.put("documentAiDetectedRegionCount", result.getDocumentAiDetectedRegionCount());
        node.put("documentAiCropCount", result.getDocumentAiCropCount());
        return JSONB.valueOf(node.toString());
    }

    private JSONB toDocxMetadata(DocxImageExtractor.ImageAsset asset,
                                 DocxImageExtractor.ExtractionResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("extractor", DOCX_DERIVED_PROVIDER);
        node.put("packagePath", asset.packagePath() == null ? "" : asset.packagePath());
        node.put("outputRelativePath", asset.outputRelativePath() == null ? "" : asset.outputRelativePath());
        node.put("originalFileName", asset.fileName() == null ? "" : asset.fileName());
        node.put("extension", asset.extension() == null ? "" : asset.extension());
        node.put("contentType", asset.contentType() == null ? "" : asset.contentType());
        node.put("width", asset.width() == null ? 0 : asset.width());
        node.put("height", asset.height() == null ? 0 : asset.height());
        node.put("fileSizeBytes", asset.sizeBytes());
        node.put("rasterReadable", asset.rasterReadable());
        node.put("referenced", asset.referenced());
        node.put("useCount", asset.useCount());
        node.put("totalPackageImages", result == null ? 0 : result.images().size());
        node.put("referencedImageCount", result == null ? 0 : result.referencedImageCount());
        node.put("vectorLikeCount", result == null ? 0 : result.vectorLikeCount());
        ArrayNode uses = node.putArray("uses");
        if (asset.uses() != null) {
            for (DocxImageExtractor.ImageUse use : asset.uses().stream().limit(3).toList()) {
                ObjectNode item = uses.addObject();
                item.put("index", use.index());
                item.put("sourcePart", use.sourcePart() == null ? "" : use.sourcePart());
                item.put("relationshipId", use.relationshipId() == null ? "" : use.relationshipId());
                item.put("docPrName", use.docPrName() == null ? "" : use.docPrName());
                item.put("docPrTitle", use.docPrTitle() == null ? "" : use.docPrTitle());
                item.put("docPrDescription", use.docPrDescription() == null ? "" : use.docPrDescription());
                item.put("paragraphText", use.paragraphText() == null ? "" : use.paragraphText());
            }
        }
        return JSONB.valueOf(node.toString());
    }

    private JSONB toPptxMetadata(PptxImageExtractor.ImageAsset asset,
                                 PptxImageExtractor.ExtractionResult result) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("extractor", PPTX_DERIVED_PROVIDER);
        node.put("packagePath", asset.packagePath() == null ? "" : asset.packagePath());
        node.put("outputRelativePath", asset.outputRelativePath() == null ? "" : asset.outputRelativePath());
        node.put("originalFileName", asset.fileName() == null ? "" : asset.fileName());
        node.put("extension", asset.extension() == null ? "" : asset.extension());
        node.put("contentType", asset.contentType() == null ? "" : asset.contentType());
        node.put("width", asset.width() == null ? 0 : asset.width());
        node.put("height", asset.height() == null ? 0 : asset.height());
        node.put("fileSizeBytes", asset.sizeBytes());
        node.put("rasterReadable", asset.rasterReadable());
        node.put("referenced", asset.referenced());
        node.put("useCount", asset.useCount());
        node.put("totalPackageImages", result == null ? 0 : result.images().size());
        node.put("referencedImageCount", result == null ? 0 : result.referencedImageCount());
        node.put("vectorLikeCount", result == null ? 0 : result.vectorLikeCount());
        ArrayNode uses = node.putArray("uses");
        if (asset.uses() != null) {
            for (PptxImageExtractor.ImageUse use : asset.uses().stream().limit(3).toList()) {
                ObjectNode item = uses.addObject();
                item.put("index", use.index());
                item.put("sourcePart", use.sourcePart() == null ? "" : use.sourcePart());
                item.put("slideNumber", use.slideNumber() == null ? 0 : use.slideNumber());
                item.put("relationshipId", use.relationshipId() == null ? "" : use.relationshipId());
                item.put("shapeName", use.shapeName() == null ? "" : use.shapeName());
                item.put("shapeTitle", use.shapeTitle() == null ? "" : use.shapeTitle());
                item.put("shapeDescription", use.shapeDescription() == null ? "" : use.shapeDescription());
                item.put("nearbyText", use.nearbyText() == null ? "" : use.nearbyText());
            }
        }
        return JSONB.valueOf(node.toString());
    }

    private byte[] downloadSourceBytes(ObjectStorageFile source) {
        if (source == null || source.getObjectKey() == null || source.getObjectKey().isBlank()) {
            return new byte[0];
        }
        StorageChannel channel = StorageChannel.isPrivate(source.getStorageChannel())
                ? StorageChannel.PRIVATE
                : StorageChannel.PUBLIC;
        return r2Client.downloadBytes(channel, source.getObjectKey());
    }

    private boolean isUsableAsset(JgdaExtractedImageAsset asset) {
        if (asset == null || asset.getPageNumber() <= 0 || asset.getAssetIndex() <= 0) {
            return false;
        }
        String kind = normalize(asset.getKind());
        if (!kind.equals("document-ai-crop") && !kind.equals("openai-crop")
                && !kind.equals("figure-crop") && !kind.equals("embedded-image")) {
            return false;
        }
        if (asset.getPath() == null || asset.getPath().isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(asset.getPath()).toAbsolutePath().normalize();
            return Files.isRegularFile(path);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isUsableDocxAsset(DocxImageExtractor.ImageAsset asset) {
        if (asset == null
                || asset.sizeBytes() <= 0
                || asset.sizeBytes() > MAX_DERIVED_IMAGE_BYTES
                || !asset.rasterReadable()
                || asset.vectorLike()) {
            return false;
        }
        String ext = normalize(asset.extension()).replaceFirst("^\\.", "");
        if (!DOCX_SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
            return false;
        }
        Integer width = asset.width();
        Integer height = asset.height();
        return width != null
                && height != null
                && width >= properties.minEmbeddedWidth()
                && height >= properties.minEmbeddedHeight();
    }

    private boolean isUsablePptxAsset(PptxImageExtractor.ImageAsset asset) {
        if (asset == null
                || asset.sizeBytes() <= 0
                || asset.sizeBytes() > MAX_DERIVED_IMAGE_BYTES
                || !asset.rasterReadable()
                || asset.vectorLike()) {
            return false;
        }
        String ext = normalize(asset.extension()).replaceFirst("^\\.", "");
        if (!PPTX_SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
            return false;
        }
        Integer width = asset.width();
        Integer height = asset.height();
        return width != null
                && height != null
                && width >= properties.minEmbeddedWidth()
                && height >= properties.minEmbeddedHeight()
                && width >= PPTX_MIN_IMAGE_WIDTH
                && height >= PPTX_MIN_IMAGE_HEIGHT;
    }

    private static long pptxPixelArea(PptxImageExtractor.ImageAsset asset) {
        if (asset == null || asset.width() == null || asset.height() == null) {
            return 0;
        }
        return (long) Math.max(0, asset.width()) * (long) Math.max(0, asset.height());
    }




    private boolean isAvailablePdf(ObjectStorageFile file) {
        if (file == null || file.getId() == null || file.getId() <= 0
                || Boolean.TRUE.equals(file.getDeleted())
                || !"available".equalsIgnoreCase(Objects.toString(file.getStatus(), ""))) {
            return false;
        }
        String ext = normalize(file.getFileExt()).replaceFirst("^\\.", "");
        String mimeType = normalize(file.getMimeType());
        return "pdf".equals(ext) || "application/pdf".equals(mimeType);
    }

    private boolean isAvailableDocx(ObjectStorageFile file) {
        if (file == null || file.getId() == null || file.getId() <= 0
                || Boolean.TRUE.equals(file.getDeleted())
                || !"available".equalsIgnoreCase(Objects.toString(file.getStatus(), ""))) {
            return false;
        }
        String ext = normalize(file.getFileExt()).replaceFirst("^\\.", "");
        String mimeType = normalize(file.getMimeType());
        return "docx".equals(ext)
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(mimeType);
    }

    private boolean isAvailablePptx(ObjectStorageFile file) {
        if (file == null || file.getId() == null || file.getId() <= 0
                || Boolean.TRUE.equals(file.getDeleted())
                || !"available".equalsIgnoreCase(Objects.toString(file.getStatus(), ""))) {
            return false;
        }
        String ext = normalize(file.getFileExt()).replaceFirst("^\\.", "");
        String mimeType = normalize(file.getMimeType());
        return "pptx".equals(ext)
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(mimeType);
    }

    private String mapKind(String kind) {
        return switch (normalize(kind)) {
            case "embedded-image" -> "pdf_embedded_image";
            case "page-render" -> "pdf_page_snapshot";
            default -> "pdf_image_block";
        };
    }

    private BigDecimal score(JgdaExtractedImageAsset asset) {
        String kind = normalize(asset.getKind());
        double base = switch (kind) {
            case "document-ai-crop", "openai-crop" -> 0.95;
            case "figure-crop" -> 0.85;
            case "embedded-image" -> 0.70;
            default -> 0.50;
        };
        JgdaImagePlacement placement = asset.getPlacement();
        if (placement != null) {
            double area = placement.getNormalizedWidth() * placement.getNormalizedHeight();
            if (area > 0.03 && area < 0.80) {
                base += 0.03;
            }
        }
        return BigDecimal.valueOf(Math.min(0.999, base));
    }

    private BigDecimal docxScore(DocxImageExtractor.ImageAsset asset) {
        double base = asset.referenced() ? 0.82 : 0.60;
        if (!isBlank(resolveDocxDescription(asset))) {
            base += 0.04;
        }
        if (asset.width() != null && asset.height() != null) {
            long area = (long) asset.width() * (long) asset.height();
            if (area >= 120_000) {
                base += 0.05;
            } else if (area >= 20_000) {
                base += 0.03;
            }
        }
        return BigDecimal.valueOf(Math.min(0.999, base));
    }

    private BigDecimal pptxScore(PptxImageExtractor.ImageAsset asset) {
        double base = asset.referenced() ? 0.84 : 0.60;
        if (!isBlank(resolvePptxDescription(asset))) {
            base += 0.04;
        }
        if (asset.width() != null && asset.height() != null) {
            long area = (long) asset.width() * (long) asset.height();
            if (area >= 120_000) {
                base += 0.05;
            } else if (area >= 20_000) {
                base += 0.03;
            }
        }
        return BigDecimal.valueOf(Math.min(0.999, base));
    }

    private String resolveCaption(JgdaExtractedImageAsset asset) {
        return "figure-crop".equals(normalize(asset.getKind())) ? blankToNull(asset.getSourceName()) : null;
    }

    private String resolveDescription(JgdaExtractedImageAsset asset) {
        if ("figure-crop".equals(normalize(asset.getKind()))) {
            return null;
        }
        return firstNonBlank(asset.getDescription(), asset.getSourceName());
    }

    private String resolveDocxCaption(DocxImageExtractor.ImageAsset asset) {
        DocxImageExtractor.ImageUse use = firstDocxUse(asset);
        if (use == null) {
            return null;
        }
        return truncate(blankToNull(firstNonBlank(use.docPrTitle(), use.docPrName())), 240);
    }

    private String resolveDocxDescription(DocxImageExtractor.ImageAsset asset) {
        DocxImageExtractor.ImageUse use = firstDocxUse(asset);
        if (use == null) {
            return truncate(blankToNull(asset.fileName()), 500);
        }
        return truncate(blankToNull(firstNonBlank(
                use.docPrDescription(),
                use.paragraphText(),
                asset.fileName())), 500);
    }

    private DocxImageExtractor.ImageUse firstDocxUse(DocxImageExtractor.ImageAsset asset) {
        if (asset == null || asset.uses() == null || asset.uses().isEmpty()) {
            return null;
        }
        return asset.uses().getFirst();
    }

    private String resolvePptxCaption(PptxImageExtractor.ImageAsset asset) {
        PptxImageExtractor.ImageUse use = firstPptxUse(asset);
        if (use == null) {
            return null;
        }
        return truncate(blankToNull(firstNonBlank(use.shapeTitle(), use.shapeName())), 240);
    }

    private String resolvePptxDescription(PptxImageExtractor.ImageAsset asset) {
        PptxImageExtractor.ImageUse use = firstPptxUse(asset);
        if (use == null) {
            return truncate(blankToNull(asset.fileName()), 500);
        }
        return truncate(blankToNull(firstNonBlank(
                use.shapeDescription(),
                use.nearbyText(),
                asset.fileName())), 500);
    }

    private Integer resolvePptxPageNumber(PptxImageExtractor.ImageAsset asset) {
        PptxImageExtractor.ImageUse use = firstPptxUse(asset);
        return use == null || use.slideNumber() == null || use.slideNumber() <= 0 ? 1 : use.slideNumber();
    }

    private PptxImageExtractor.ImageUse firstPptxUse(PptxImageExtractor.ImageAsset asset) {
        if (asset == null || asset.uses() == null || asset.uses().isEmpty()) {
            return null;
        }
        return asset.uses().getFirst();
    }

    private String normalizeImageExt(String format, String fileName) {
        String ext = normalize(format).replaceFirst("^\\.", "");
        if (!ext.isBlank() && List.of("png", "jpg", "jpeg", "webp").contains(ext)) {
            return "jpeg".equals(ext) ? "jpg" : ext;
        }
        String name = normalize(fileName);
        int idx = name.lastIndexOf('.');
        if (idx >= 0 && idx < name.length() - 1) {
            ext = name.substring(idx + 1);
            if (List.of("png", "jpg", "jpeg", "webp").contains(ext)) {
                return "jpeg".equals(ext) ? "jpg" : ext;
            }
        }
        return "png";
    }

    private String normalizeDocxImageExt(String extension, String fileName, String contentType) {
        String ext = normalize(extension).replaceFirst("^\\.", "");
        if (!ext.isBlank() && DOCX_SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
            return "jpeg".equals(ext) ? "jpg" : ext;
        }
        String name = normalize(fileName);
        int idx = name.lastIndexOf('.');
        if (idx >= 0 && idx < name.length() - 1) {
            ext = name.substring(idx + 1);
            if (DOCX_SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
                return "jpeg".equals(ext) ? "jpg" : ext;
            }
        }
        String mimeType = normalize(contentType);
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private String normalizePptxImageExt(String extension, String fileName, String contentType) {
        String ext = normalize(extension).replaceFirst("^\\.", "");
        if (!ext.isBlank() && PPTX_SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
            return "jpeg".equals(ext) ? "jpg" : ext;
        }
        String name = normalize(fileName);
        int idx = name.lastIndexOf('.');
        if (idx >= 0 && idx < name.length() - 1) {
            ext = name.substring(idx + 1);
            if (PPTX_SUPPORTED_IMAGE_EXTENSIONS.contains(ext)) {
                return "jpeg".equals(ext) ? "jpg" : ext;
            }
        }
        String mimeType = normalize(contentType);
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }

    private String contentType(String ext) {
        return switch (normalize(ext)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }

    private String contentTypeForDocxExt(String ext) {
        return switch (normalize(ext)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/png";
        };
    }

    private String contentTypeForPptxExt(String ext) {
        return switch (normalize(ext)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/png";
        };
    }

    private String assetKey(JgdaExtractedImageAsset asset) {
        return asset.getKind() + ":" + asset.getPageNumber() + ":" + asset.getAssetIndex() + ":"
                + firstNonBlank(asset.getSha256(), asset.getPath());
    }

    private String docxAssetKey(DocxImageExtractor.ImageAsset asset) {
        return firstNonBlank(asset.sha256(), asset.packagePath()) + ":" + normalize(asset.extension());
    }

    private String pptxAssetKey(PptxImageExtractor.ImageAsset asset) {
        return firstNonBlank(asset.sha256(), asset.packagePath()) + ":" + normalize(asset.extension());
    }

    private boolean linkCachedAssetsIfPresent(Long userId,
                                              Long projectId,
                                              ObjectStorageFile sourceFile,
                                              String sourceContentHash,
                                              String extractorSignature) {
        if (userId == null || userId <= 0
                || projectId == null || projectId <= 0
                || sourceFile == null || sourceFile.getId() == null || sourceFile.getId() <= 0
                || isBlank(sourceContentHash)
                || isBlank(extractorSignature)) {
            return false;
        }
        List<PdfDerivedMediaAssetCacheEntry> cachedAssets =
                pdfDerivedMediaAssetCacheRepository.findAvailableByOwnerHashAndSignature(
                        userId,
                        sourceContentHash,
                        extractorSignature,
                        properties.maxAssets());
        int linked = linkCacheAssetsToProject(userId, projectId, sourceFile, cachedAssets);
        if (linked > 0) {
            log.info("[PdfDerivedAssets] cache hit linked: projectId={}, sourceFileId={}, sourceHash={}, assets={}",
                    projectId,
                    sourceFile.getId(),
                    shortHash(sourceContentHash),
                    linked);
            return true;
        }
        return false;
    }

    private int linkCacheAssetsToProject(Long userId,
                                         Long projectId,
                                         ObjectStorageFile sourceFile,
                                         List<PdfDerivedMediaAssetCacheEntry> cachedAssets) {
        if (cachedAssets == null || cachedAssets.isEmpty()
                || projectId == null || projectId <= 0
                || sourceFile == null || sourceFile.getId() == null || sourceFile.getId() <= 0) {
            return 0;
        }
        List<Long> cacheAssetIds = cachedAssets.stream()
                .filter(Objects::nonNull)
                .filter(asset -> asset.getId() != null && asset.getId() > 0)
                .filter(asset -> asset.getDerivedFileId() != null && asset.getDerivedFileId() > 0)
                .map(PdfDerivedMediaAssetCacheEntry::getId)
                .toList();
        if (cacheAssetIds.isEmpty()) {
            return 0;
        }
        for (PdfDerivedMediaAssetCacheEntry asset : cachedAssets) {
            if (asset == null || asset.getDerivedFileId() == null || asset.getDerivedFileId() <= 0) {
                continue;
            }
            projectFileLinkRepository.upsertLink(
                    projectId,
                    asset.getDerivedFileId(),
                    userId,
                    firstNonBlank(asset.getDerivedFileName(), fallbackDerivedFileName(asset)),
                    "image");
            fileAccessGrantRepository.upsertLink(userId, asset.getDerivedFileId(), projectId);
        }
        pdfDerivedMediaAssetCacheRepository.linkAssetsToProject(projectId, sourceFile.getId(), userId, cacheAssetIds);
        return cacheAssetIds.size();
    }

    private void markTerminalIfNoSavedAssets(Long projectId,
                                             Long sourceFileId,
                                             ExtractionKind kind,
                                             String extractorSignature,
                                             int detectedAssets,
                                             int savedAssets) {
        if (savedAssets > 0) {
            return;
        }
        String result = detectedAssets <= 0 ? EXTRACTION_RESULT_EMPTY : EXTRACTION_RESULT_FAILED;
        markExtractionResult(projectId, sourceFileId, kind, extractorSignature, result);
    }

    private void markExtractionResult(Long projectId,
                                      Long sourceFileId,
                                      ExtractionKind kind,
                                      String extractorSignature,
                                      String result) {
        if (projectId == null || projectId <= 0
                || sourceFileId == null || sourceFileId <= 0
                || kind == null
                || !isTerminalExtractionResult(result)) {
            return;
        }
        String key = extractionResultKey(projectId, sourceFileId, kind, extractorSignature);
        try {
            redisClient.set(key, result, EXTRACTION_RESULT_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("[DerivedMediaAssets] failed to write extraction terminal marker: projectId={}, sourceFileId={}, kind={}, result={}, err={}",
                    projectId, sourceFileId, kind.key(), result, e.getMessage());
            return;
        }
        log.info("[DerivedMediaAssets] extraction terminal: projectId={}, sourceFileId={}, kind={}, result={}",
                projectId, sourceFileId, kind.key(), result);
    }

    private boolean isTerminalExtractionResult(String result) {
        String normalized = normalize(result);
        return EXTRACTION_RESULT_EMPTY.equals(normalized) || EXTRACTION_RESULT_FAILED.equals(normalized);
    }

    private String extractionResultKey(Long projectId, Long sourceFileId, ExtractionKind kind) {
        String extractorSignature = switch (kind) {
            case PDF -> extractorSignature();
            case DOCX -> docxExtractorSignature();
            case PPTX -> pptxExtractorSignature();
        };
        return extractionResultKey(projectId, sourceFileId, kind, extractorSignature);
    }

    private String extractionResultKey(Long projectId,
                                       Long sourceFileId,
                                       ExtractionKind kind,
                                       String extractorSignature) {
        return RedisKey.derivedMediaExtractionResult(
                kind.key(),
                projectId,
                sourceFileId,
                signatureHash(firstNonBlank(extractorSignature, kind.key())));
    }


    private String extractorSignature() {
        return String.join("|",
                "pdf-derived:v1",
                "mode=" + properties.extractorMode(),
                "provider=" + normalize(properties.imageDetectionProvider()),
                "maxAssets=" + properties.maxAssets(),
                "maxPages=" + properties.maxPages(),
                "dpi=" + properties.dpi(),
                "renderPages=" + properties.renderPages(),
                "extractEmbedded=" + properties.extractEmbeddedImages(),
                "cropFigures=" + properties.cropFigures(),
                "documentAi=" + properties.enableDocumentAi(),
                "documentAiMaxPages=" + properties.documentAiMaxPages(),
                "documentAiCropMargin=" + properties.documentAiCropMargin(),
                "documentAiMinArea=" + properties.documentAiMinPageAreaRatio(),
                "openAiMaxPages=" + properties.openAiMaxPages(),
                "openAiModel=" + normalize(properties.openAiModel()),
                "openAiDetectionDpi=" + properties.openAiDetectionRenderDpi(),
                "openAiRenderDpi=" + properties.openAiRenderDpi(),
                "openAiCropMargin=" + properties.openAiCropMargin(),
                "openAiMinArea=" + properties.openAiMinPageAreaRatio(),
                "minEmbeddedWidth=" + properties.minEmbeddedWidth(),
                "minEmbeddedHeight=" + properties.minEmbeddedHeight(),
                "minEmbeddedBytes=" + properties.minEmbeddedFileSizeBytes(),
                "minEmbeddedArea=" + properties.minEmbeddedPageAreaRatio(),
                "dedupeEmbedded=" + properties.deduplicateEmbeddedImages());
    }

    private String docxExtractorSignature() {
        return String.join("|",
                "docx-derived:v1",
                "provider=" + DOCX_DERIVED_PROVIDER,
                "maxAssets=" + properties.maxAssets(),
                "minWidth=" + properties.minEmbeddedWidth(),
                "minHeight=" + properties.minEmbeddedHeight(),
                "maxBytes=" + MAX_DERIVED_IMAGE_BYTES,
                "formats=" + String.join(",", DOCX_SUPPORTED_IMAGE_EXTENSIONS),
                "skipVector=true");
    }

    private String pptxExtractorSignature() {
        return String.join("|",
                "pptx-derived:v1",
                "provider=" + PPTX_DERIVED_PROVIDER,
                "maxAssets=" + properties.maxAssets(),
                "minWidth=" + properties.minEmbeddedWidth(),
                "minHeight=" + properties.minEmbeddedHeight(),
                "pptxMinWidth=" + PPTX_MIN_IMAGE_WIDTH,
                "pptxMinHeight=" + PPTX_MIN_IMAGE_HEIGHT,
                "maxBytes=" + MAX_DERIVED_IMAGE_BYTES,
                "formats=" + String.join(",", PPTX_SUPPORTED_IMAGE_EXTENSIONS),
                "skipVector=true");
    }

    private String fallbackDerivedFileName(PdfDerivedMediaAssetCacheEntry asset) {
        if (asset != null && (DOCX_DERIVED_KIND.equals(normalize(asset.getKind()))
                || DOCX_DERIVED_PROVIDER.equals(normalize(asset.getProvider())))) {
            return "docx-image-" + asset.getDerivedFileId() + ".png";
        }
        if (asset != null && (PPTX_DERIVED_KIND.equals(normalize(asset.getKind()))
                || PPTX_DERIVED_PROVIDER.equals(normalize(asset.getProvider())))) {
            return "pptx-image-" + asset.getDerivedFileId() + ".png";
        }
        return "pdf-image-" + (asset == null ? "" : asset.getDerivedFileId()) + ".png";
    }

    private String signatureHash(String extractorSignature) {
        return HashUtils.sha256Hex(firstNonBlank(extractorSignature, "default"));
    }

    private String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "";
        }
        String trimmed = hash.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long elapsedMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }

    private String extractionLockKey(Long userId, String sourceContentHash, String extractorSignature) {
        return RedisKey.lock("pdf-derived-assets:" + userId + ":" + sourceContentHash + ":" + signatureHash(extractorSignature));
    }

    private String docxExtractionLockKey(Long userId, String sourceContentHash, String extractorSignature) {
        return RedisKey.lock("docx-derived-assets:" + userId + ":" + sourceContentHash + ":" + signatureHash(extractorSignature));
    }

    private String pptxExtractionLockKey(Long userId, String sourceContentHash, String extractorSignature) {
        return RedisKey.lock("pptx-derived-assets:" + userId + ":" + sourceContentHash + ":" + signatureHash(extractorSignature));
    }

    private enum ExtractionKind {
        PDF(PDF_EXTRACTION_KIND),
        DOCX(DOCX_EXTRACTION_KIND),
        PPTX(PPTX_EXTRACTION_KIND);

        private final String key;

        ExtractionKind(String key) {
            this.key = key;
        }

        private String key() {
            return key;
        }
    }

    private void cleanupDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
