package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ObjectStorageFileSourceType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileTextFragmentRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.utils.file.TokenApproxChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Owns chat attachment validation, persistence metadata, and model context preparation.
 *
 * <p>This component deliberately has no knowledge of SSE, Redis locks, billing, or Agent
 * stream lifecycle. It validates all requested files before the caller persists the user turn.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatAttachmentService {

    private static final int MAX_FILES = 5;
    private static final int MAX_IMAGES = 5;
    private static final int FILE_CONTEXT_TOKEN_BUDGET = 12_000;
    private static final int MAX_FRAGMENTS_PER_FILE = 6;
    private static final List<String> CACHE_KEY_SOURCE_TYPES =
            ObjectStorageFileSourceType.cacheKeyLookupValues();

    private final ObjectStorageFileRepository objectStorageFileRepository;
    private final ProjectFileLinkRepository projectFileLinkRepository;
    private final FileTextFragmentRepository fileTextFragmentRepository;
    private final FileAccessGrantRepository fileAccessGrantRepository;
    private final R2Client r2Client;
    private final ObjectMapper objectMapper;

    public record AttachmentMeta(String kind, String cacheKey) {
    }

    public record PreparedAttachmentWrites(List<SessionMessage> messages,
                                           List<String> newFileKeys,
                                           List<String> newImageKeys) {
        public PreparedAttachmentWrites {
            messages = messages == null ? List.of() : List.copyOf(messages);
            newFileKeys = newFileKeys == null ? List.of() : List.copyOf(newFileKeys);
            newImageKeys = newImageKeys == null ? List.of() : List.copyOf(newImageKeys);
        }
    }

    public record FileContextResult(List<Map<String, Object>> items,
                                    List<String> imageUrls,
                                    List<String> fileUrls) {
        public FileContextResult {
            items = items == null ? List.of() : List.copyOf(items);
            imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
            fileUrls = fileUrls == null ? List.of() : List.copyOf(fileUrls);
        }
    }

    public List<AttachmentMeta> extractAttachmentMetas(List<SessionMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<AttachmentMeta> out = new ArrayList<>();
        for (SessionMessage message : history) {
            if (!isAttachmentMetaMessage(message)) {
                continue;
            }
            try {
                var node = objectMapper.readTree(message.getRawContent());
                String kind = node.path("kind").asText(null);
                String cacheKey = normalizeCacheKey(node.path("cacheKey").asText(null));
                if (kind == null || kind.isBlank() || cacheKey == null) {
                    continue;
                }
                out.add(new AttachmentMeta(kind.trim().toLowerCase(Locale.ROOT), cacheKey));
            } catch (Exception ignored) {
                // Ignore malformed legacy/system messages; only attachment-v1 is authoritative here.
            }
        }
        return List.copyOf(out);
    }

    public PreparedAttachmentWrites prepareAttachmentWrites(Long userId,
                                                             Session session,
                                                             List<String> requestedFileKeys,
                                                             List<String> requestedImageKeys,
                                                             List<String> historyFileKeys,
                                                             List<String> historyImageKeys) {
        Set<String> existingFileKeys = new HashSet<>(historyFileKeys == null ? List.of() : historyFileKeys);
        Set<String> existingImageKeys = new HashSet<>(historyImageKeys == null ? List.of() : historyImageKeys);
        List<SessionMessage> pendingMessages = new ArrayList<>();
        List<String> newFileKeys = new ArrayList<>();
        List<String> newImageKeys = new ArrayList<>();

        if (requestedFileKeys != null) {
            for (String sourceKey : requestedFileKeys) {
                String cacheKey = normalizeCacheKey(sourceKey);
                if (cacheKey == null || existingFileKeys.contains(cacheKey)) {
                    continue;
                }
                ObjectStorageFile file = findProjectScopedFileByKey(userId, session.getProjectId(), cacheKey);
                if (file == null || file.getId() == null) {
                    log.warn("file key not found (skip persist): cacheKey={}, normalizedKey={}", sourceKey, cacheKey);
                    continue;
                }
                assertR2ObjectExistsOrMarkStale(file);
                if (isImageFile(file)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片请使用 imageCacheKeys 传递");
                }
                assertProjectAccess(userId, session.getProjectId(), file, "当前文件不属于当前项目，请先将文件添加到项目后再引用");

                pendingMessages.add(attachmentMessage(session, file, cacheKey, "file", null));
                newFileKeys.add(cacheKey);
                existingFileKeys.add(cacheKey);
            }
        }

        if (requestedImageKeys != null) {
            for (String sourceKey : requestedImageKeys) {
                String cacheKey = normalizeCacheKey(sourceKey);
                if (cacheKey == null || existingImageKeys.contains(cacheKey)) {
                    continue;
                }
                ObjectStorageFile file = findProjectScopedFileByKey(userId, session.getProjectId(), cacheKey);
                if (file == null || file.getId() == null) {
                    log.warn("imageCacheKeys not found (skip persist): cacheKey={}, normalizedKey={}", sourceKey, cacheKey);
                    continue;
                }
                assertR2ObjectExistsOrMarkStale(file);
                if (!isImageFile(file)) {
                    throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "imageCacheKeys 仅支持图片文件");
                }
                assertProjectAccess(userId, session.getProjectId(), file, "当前图片不属于当前项目，请先将图片添加到项目后再引用");
                String publicUrl = buildPublicUrl(file);
                if (publicUrl == null || publicUrl.isBlank()) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片 publicUrl 生成失败");
                }

                pendingMessages.add(attachmentMessage(session, file, cacheKey, "image", publicUrl));
                newImageKeys.add(cacheKey);
                existingImageKeys.add(cacheKey);
            }
        }

        return new PreparedAttachmentWrites(pendingMessages, newFileKeys, newImageKeys);
    }

    public List<String> mergeRecentKeys(List<String> historyKeys, List<String> requestKeys, int max) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addNonBlank(keys, historyKeys);
        addNonBlank(keys, requestKeys);
        if (keys.size() <= max) {
            return List.copyOf(keys);
        }
        List<String> all = new ArrayList<>(keys);
        return List.copyOf(all.subList(all.size() - max, all.size()));
    }

    public FileContextResult buildFileContext(Long userId,
                                              Session session,
                                              List<String> fileKeys,
                                              List<String> imageKeys) {
        List<String> safeFileKeys = fileKeys == null ? List.of() : fileKeys;
        List<String> safeImageKeys = imageKeys == null ? List.of() : imageKeys;
        if (safeFileKeys.isEmpty() && safeImageKeys.isEmpty()) {
            return new FileContextResult(List.of(), List.of(), List.of());
        }
        if (safeFileKeys.size() > MAX_FILES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多支持同时引用 5 个文件");
        }
        if (safeImageKeys.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多支持同时引用 5 张图片");
        }

        Long projectId = session.getProjectId();
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> imageUrls = collectImageUrls(userId, projectId, safeImageKeys);
        List<String> fileUrls = new ArrayList<>();
        List<FileBundle> bundles = collectFileBundles(userId, projectId, safeFileKeys, fileUrls);
        appendFragmentContext(items, bundles);
        return new FileContextResult(items, imageUrls, fileUrls);
    }

    public ObjectStorageFile findProjectScopedFileByKey(Long userId, Long projectId, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return null;
        }
        if (projectId != null && projectId > 0) {
            ObjectStorageFile linked = projectFileLinkRepository.findActiveFileIdByProjectAndCacheKey(projectId, sourceKey)
                    .flatMap(objectStorageFileRepository::findById)
                    .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                    .filter(file -> file.getStatus() != null && "available".equalsIgnoreCase(file.getStatus()))
                    .orElse(null);
            if (linked != null) {
                return linked;
            }
        }
        return findAvailableFileByKey(userId, sourceKey);
    }

    private List<String> collectImageUrls(Long userId, Long projectId, List<String> imageKeys) {
        List<String> imageUrls = new ArrayList<>();
        for (String sourceKey : imageKeys) {
            String cacheKey = normalizeCacheKey(sourceKey);
            if (cacheKey == null) {
                continue;
            }
            ObjectStorageFile file = findProjectScopedFileByKey(userId, projectId, cacheKey);
            if (file == null || file.getId() == null) {
                log.warn("imageCacheKeys not found: cacheKey={}, normalizedKey={}", sourceKey, cacheKey);
                continue;
            }
            assertR2ObjectExistsOrMarkStale(file);
            if (!isImageFile(file)) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "imageCacheKeys 仅支持图片文件");
            }
            assertProjectAccess(userId, projectId, file, "当前图片不属于当前项目，请先将图片添加到项目后再引用");
            String publicUrl = buildPublicUrl(file);
            if (publicUrl == null || publicUrl.isBlank()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片 publicUrl 生成失败");
            }
            imageUrls.add(publicUrl);
        }
        return imageUrls;
    }

    private List<FileBundle> collectFileBundles(Long userId,
                                                Long projectId,
                                                List<String> fileKeys,
                                                List<String> fileUrls) {
        List<FileBundle> bundles = new ArrayList<>();
        for (String sourceKey : fileKeys) {
            String cacheKey = normalizeCacheKey(sourceKey);
            if (cacheKey == null) {
                continue;
            }
            ObjectStorageFile file = findProjectScopedFileByKey(userId, projectId, cacheKey);
            if (file == null || file.getId() == null) {
                log.warn("file key not found: cacheKey={}, normalizedKey={}", sourceKey, cacheKey);
                continue;
            }
            assertR2ObjectExistsOrMarkStale(file);
            if (isImageFile(file)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片请使用 imageCacheKeys 传递");
            }
            assertProjectAccess(userId, projectId, file, "当前文件不属于当前项目，请先将文件添加到项目后再引用");

            String publicUrl = buildPublicUrl(file);
            if (publicUrl != null && !publicUrl.isBlank()) {
                log.info("[FileContext] 文件走 input_file URL 模式: fileId={}, cacheKey={}, url={}",
                        file.getId(), cacheKey, publicUrl);
                fileUrls.add(publicUrl);
                continue;
            }

            List<String> fragments = fileTextFragmentRepository.listFragmentContents(file.getId(), MAX_FRAGMENTS_PER_FILE);
            if (fragments == null || fragments.isEmpty()) {
                log.warn("file fragments not found: fileId={}, cacheKey={}", file.getId(), cacheKey);
                continue;
            }
            List<Integer> tokens = fragments.stream()
                    .map(fragment -> fragment == null || fragment.isBlank()
                            ? 0
                            : Math.max(TokenApproxChunker.estimateTokensPublic(fragment), 0))
                    .toList();
            bundles.add(new FileBundle(cacheKey, fragments, tokens));
        }
        return bundles;
    }

    private void appendFragmentContext(List<Map<String, Object>> items, List<FileBundle> bundles) {
        if (bundles.isEmpty()) {
            return;
        }
        int usedTokens = 0;
        int fileCount = bundles.size();
        int perFileBudget = FILE_CONTEXT_TOKEN_BUDGET / fileCount;
        int remainder = FILE_CONTEXT_TOKEN_BUDGET % fileCount;
        List<Integer> fileUsed = new ArrayList<>(java.util.Collections.nCopies(fileCount, 0));

        for (int fileIndex = 0; fileIndex < fileCount; fileIndex++) {
            FileBundle bundle = bundles.get(fileIndex);
            int fileBudget = perFileBudget + (fileIndex < remainder ? 1 : 0);
            while (bundle.hasNext()) {
                int tokens = bundle.nextTokens();
                if (tokens <= 0) {
                    bundle.skip();
                    continue;
                }
                int currentFileUsage = fileUsed.get(fileIndex);
                if (currentFileUsage + tokens > fileBudget || usedTokens + tokens > FILE_CONTEXT_TOKEN_BUDGET) {
                    break;
                }
                items.add(bundle.nextItem());
                fileUsed.set(fileIndex, currentFileUsage + tokens);
                usedTokens += tokens;
            }
        }

        int remaining = FILE_CONTEXT_TOKEN_BUDGET - usedTokens;
        boolean progressed = true;
        while (remaining > 0 && progressed) {
            progressed = false;
            for (FileBundle bundle : bundles) {
                while (bundle.hasNext()) {
                    int tokens = bundle.nextTokens();
                    if (tokens <= 0) {
                        bundle.skip();
                        continue;
                    }
                    if (tokens > remaining) {
                        break;
                    }
                    items.add(bundle.nextItem());
                    remaining -= tokens;
                    progressed = true;
                    break;
                }
                if (remaining == 0) {
                    break;
                }
            }
        }
    }

    private SessionMessage attachmentMessage(Session session,
                                             ObjectStorageFile file,
                                             String cacheKey,
                                             String kind,
                                             String publicUrl) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("pp", "attachment-v1");
        raw.put("kind", kind);
        raw.put("cacheKey", cacheKey);
        if (publicUrl != null) {
            raw.put("publicUrl", publicUrl);
        }
        raw.put("display", "image".equals(kind) ? "card" : "inline");
        raw.put("scope", "project");
        raw.put("fileName", file.getOriginalName());
        raw.put("mimeType", file.getMimeType());
        raw.put("fileSize", file.getSizeBytes());
        raw.put("fileId", file.getId());

        SessionMessage message = new SessionMessage();
        message.setSessionId(session.getId());
        message.setRole(SessionMessageRole.SYSTEM);
        message.setType("image".equals(kind) ? SessionMessageType.IMAGE : SessionMessageType.TEXT);
        message.setContent(publicUrl != null ? publicUrl : file.getOriginalName());
        message.setRawContent(writeJson(raw));
        message.setStatus(SessionMessageStatus.FINAL);
        message.setDeleted(false);
        return message;
    }

    public boolean isAttachmentMetaMessage(SessionMessage message) {
        if (message == null || message.getRole() != SessionMessageRole.SYSTEM) {
            return false;
        }
        String raw = message.getRawContent();
        return raw != null
                && !raw.isBlank()
                && raw.contains("\"pp\":\"attachment-v1\"")
                && raw.contains("\"cacheKey\"");
    }

    private ObjectStorageFile findAvailableFileByKey(Long userId, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return null;
        }
        for (String sourceType : CACHE_KEY_SOURCE_TYPES) {
            ObjectStorageFile file = objectStorageFileRepository
                    .findAvailableByOwnerAndSource(userId, sourceType, sourceKey)
                    .orElse(null);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    private void assertProjectAccess(Long userId, Long projectId, ObjectStorageFile file, String message) {
        if (projectFileLinkRepository.existsActiveLink(projectId, file.getId())) {
            return;
        }
        if (!fileAccessGrantRepository.hasValidLink(userId, file.getId(), projectId)) {
            log.warn("no grant for file usage: userId={}, projectId={}, fileId={}", userId, projectId, file.getId());
            throw new BusinessException(ErrorCode.FILE_NOT_IN_SESSION, message);
        }
    }

    private void assertR2ObjectExistsOrMarkStale(ObjectStorageFile file) {
        if (file == null || file.getId() == null || file.getId() <= 0) {
            return;
        }
        String objectKey = file.getObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            objectStorageFileRepository.markStale(file.getId());
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件已被自动清理，请重新上传");
        }
        try {
            if (!r2Client.exists(objectKey)) {
                objectStorageFileRepository.markStale(file.getId());
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件已被自动清理，请重新上传");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("R2 exists check failed, skip stale-mark: fileId={}, key={}, err={}",
                    file.getId(), objectKey, exception.getMessage());
        }
    }

    private String normalizeCacheKey(String input) {
        if (input == null) {
            return null;
        }
        String key = input.trim();
        if (key.startsWith("file_")) {
            key = key.substring("file_".length()).trim();
        }
        return key.isBlank() ? null : key.toLowerCase(Locale.ROOT);
    }

    private boolean isImageFile(ObjectStorageFile file) {
        if (file == null) {
            return false;
        }
        String mimeType = file.getMimeType();
        if (mimeType != null && !mimeType.isBlank()) {
            return mimeType.toLowerCase(Locale.ROOT).startsWith("image/");
        }
        String extension = file.getFileExt();
        if (extension == null) {
            return false;
        }
        String normalized = extension.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
        return Set.of("png", "jpg", "jpeg", "webp", "gif").contains(normalized);
    }

    private String buildPublicUrl(ObjectStorageFile file) {
        if (file == null) {
            return null;
        }
        String domain = file.getPublicDomain();
        String path = file.getPublicPath();
        if (domain != null && !domain.isBlank() && path != null && !path.isBlank()) {
            return domain.replaceAll("/$", "") + "/" + path.replaceFirst("^/", "");
        }
        String objectKey = file.getObjectKey();
        if (domain != null && !domain.isBlank() && objectKey != null && !objectKey.isBlank()) {
            return domain.replaceAll("/$", "") + "/" + objectKey.replaceFirst("^/", "");
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON serialization failed");
        }
    }

    private static void addNonBlank(Set<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        values.stream().filter(value -> value != null && !value.isBlank()).forEach(target::add);
    }

    private static final class FileBundle {
        private final String cacheKey;
        private final List<String> fragments;
        private final List<Integer> tokens;
        private int nextIndex;

        private FileBundle(String cacheKey, List<String> fragments, List<Integer> tokens) {
            this.cacheKey = cacheKey;
            this.fragments = fragments;
            this.tokens = tokens;
        }

        private boolean hasNext() {
            return nextIndex < fragments.size();
        }

        private int nextTokens() {
            return tokens.get(nextIndex);
        }

        private void skip() {
            nextIndex++;
        }

        private Map<String, Object> nextItem() {
            int fragmentIndex = nextIndex;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", "system");
            item.put("type", "text");
            item.put("content", "【FILE " + cacheKey + " · part " + (fragmentIndex + 1) + "】\n"
                    + fragments.get(fragmentIndex));
            nextIndex++;
            return item;
        }
    }
}
