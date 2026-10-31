package com.xiaoyuan.paperpuppybackendintl.service.toolbook.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookManifestSpec;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookNodeContentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookNodeDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookReadingPathDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookSummaryDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookTocDTO;
import com.xiaoyuan.paperpuppybackendintl.service.toolbook.ToolBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolBookServiceImpl implements ToolBookService {

    @Value("${app.tool-book.manifest-pattern:classpath*:tool-books/*/manifest.json}")
    private String manifestPattern;
    @Value("${app.tool-book.default-node-max-chars:12000}")
    private int defaultNodeMaxChars;
    private final ResourceLoader resourceLoader;
    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;
    private volatile Map<String, LoadedToolBook> cachedBooks;

    @Override
    public List<ToolBookSummaryDTO> listBooks() {
        return getLoadedBooks().values().stream()
                .map(LoadedToolBook::summary)
                .toList();
    }

    @Override
    public ToolBookTocDTO getBookToc(String bookKey) {
        LoadedToolBook book = requireBook(bookKey);
        ToolBookTocDTO dto = new ToolBookTocDTO();
        dto.setBook(book.summary());
        dto.setRecommendedReadingPaths(book.readingPaths());
        dto.setNodes(book.sortedNodes());
        return dto;
    }

    @Override
    public ToolBookNodeContentDTO readNode(String bookKey, String nodeKey, Integer maxChars) {
        LoadedToolBook book = requireBook(bookKey);
        ToolBookManifestSpec.NodeSpec node = requireNode(book, nodeKey);

        ToolBookNodeContentDTO dto = new ToolBookNodeContentDTO();
        dto.setBook(book.summary());
        dto.setNode(toNodeDto(node));
        dto.setBreadcrumbs(buildBreadcrumbs(book, node));
        dto.setChildren(buildChildren(book, node.getNodeKey()));
        int appliedMaxChars = normalizeMaxChars(maxChars);
        dto.setMaxCharsApplied(appliedMaxChars);
        String content = readNodeContent(book, node);
        if (content == null) {
            dto.setContentMd(null);
            dto.setContentLength(0);
            dto.setTruncated(false);
            return dto;
        }
        dto.setContentLength(content.length());
        if (content.length() > appliedMaxChars) {
            dto.setContentMd(content.substring(0, appliedMaxChars));
            dto.setTruncated(true);
        } else {
            dto.setContentMd(content);
            dto.setTruncated(false);
        }
        return dto;
    }

    public void evictCache() {
        cachedBooks = null;
    }

    private LoadedToolBook requireBook(String bookKey) {
        if (!StringUtils.hasText(bookKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "bookKey 不能为空");
        }
        LoadedToolBook book = getLoadedBooks().get(bookKey.trim());
        if (book == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "工具书不存在: " + bookKey);
        }
        return book;
    }

    private ToolBookManifestSpec.NodeSpec requireNode(LoadedToolBook book, String nodeKey) {
        if (!StringUtils.hasText(nodeKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "nodeKey 不能为空");
        }
        ToolBookManifestSpec.NodeSpec node = book.nodeMap().get(nodeKey.trim());
        if (node == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                    "工具书节点不存在: " + book.summary().getBookKey() + "/" + nodeKey);
        }
        return node;
    }

    private Map<String, LoadedToolBook> getLoadedBooks() {
        if (cachedBooks != null) {
            return cachedBooks;
        }
        synchronized (this) {
            if (cachedBooks != null) {
                return cachedBooks;
            }
            cachedBooks = loadBooks();
            return cachedBooks;
        }
    }

    private Map<String, LoadedToolBook> loadBooks() {
        Map<String, LoadedToolBook> books = new LinkedHashMap<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            Resource[] manifests = resolver.getResources(manifestPattern);
            for (Resource manifestResource : manifests) {
                ToolBookManifestSpec manifest = readManifest(manifestResource);
                if (manifest == null || !StringUtils.hasText(manifest.getBookKey())) {
                    continue;
                }
                String bookKey = manifest.getBookKey().trim();
                if (books.containsKey(bookKey)) {
                    log.warn("[ToolBook] duplicate bookKey ignored: {}", bookKey);
                    continue;
                }
                books.put(bookKey, buildLoadedBook(manifestResource, manifest));
            }
            log.info("[ToolBook] loaded books: count={}, pattern={}", books.size(), manifestPattern);
            return books;
        } catch (IOException e) {
            log.error("[ToolBook] failed to scan manifests: pattern={}", manifestPattern, e);
            return Map.of();
        }
    }

    private ToolBookManifestSpec readManifest(Resource manifestResource) {
        try (InputStream is = manifestResource.getInputStream()) {
            return objectMapper.readValue(is, ToolBookManifestSpec.class);
        } catch (IOException e) {
            try {
                log.warn("[ToolBook] failed to read manifest: {}", manifestResource.getURI(), e);
            } catch (IOException ignored) {
                log.warn("[ToolBook] failed to read manifest resource", e);
            }
            return null;
        }
    }

    private LoadedToolBook buildLoadedBook(Resource manifestResource, ToolBookManifestSpec manifest) {
        List<ToolBookManifestSpec.NodeSpec> manifestNodes = manifest.getNodes() == null
                ? List.of()
                : manifest.getNodes();

        Map<String, ToolBookManifestSpec.NodeSpec> nodeMap = new LinkedHashMap<>();
        for (ToolBookManifestSpec.NodeSpec node : manifestNodes) {
            if (!StringUtils.hasText(node.getNodeKey())) {
                continue;
            }
            nodeMap.put(node.getNodeKey(), node);
        }

        List<ToolBookNodeDTO> sortedNodes = manifestNodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> StringUtils.hasText(node.getNodeKey()))
                .sorted(nodeComparator())
                .map(this::toNodeDto)
                .toList();

        List<ToolBookReadingPathDTO> readingPaths = manifest.getRecommendedReadingPaths() == null
                ? List.of()
                : manifest.getRecommendedReadingPaths().stream()
                .filter(Objects::nonNull)
                .map(this::toReadingPathDto)
                .toList();

        return new LoadedToolBook(
                manifestResource,
                manifest,
                toSummaryDto(manifest),
                nodeMap,
                sortedNodes,
                readingPaths
        );
    }

    private List<ToolBookNodeDTO> buildBreadcrumbs(LoadedToolBook book, ToolBookManifestSpec.NodeSpec node) {
        List<ToolBookNodeDTO> breadcrumbs = new ArrayList<>();
        ToolBookManifestSpec.NodeSpec current = node;
        while (current != null) {
            breadcrumbs.addFirst(toNodeDto(current));
            if (!StringUtils.hasText(current.getParentKey())) {
                break;
            }
            current = book.nodeMap().get(current.getParentKey());
        }
        return breadcrumbs;
    }

    private List<ToolBookNodeDTO> buildChildren(LoadedToolBook book, String parentKey) {
        return book.nodeMap().values().stream()
                .filter(node -> Objects.equals(parentKey, node.getParentKey()))
                .sorted(nodeComparator())
                .map(this::toNodeDto)
                .toList();
    }

    private String readNodeContent(LoadedToolBook book, ToolBookManifestSpec.NodeSpec node) {
        if (!StringUtils.hasText(node.getPath())) {
            return null;
        }
        try {
            Resource resource = book.manifestResource().createRelative(node.getPath());
            if (!resource.exists()) {
                log.warn("[ToolBook] node content not found: bookKey={}, nodeKey={}, path={}",
                        book.summary().getBookKey(), node.getNodeKey(), node.getPath());
                return null;
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("[ToolBook] failed to read node content: bookKey={}, nodeKey={}, path={}",
                    book.summary().getBookKey(), node.getNodeKey(), node.getPath(), e);
            return null;
        }
    }

    private int normalizeMaxChars(Integer maxChars) {
        if (maxChars != null && maxChars > 0) {
            return maxChars;
        }
        return Math.max(1, defaultNodeMaxChars);
    }

    private ToolBookSummaryDTO toSummaryDto(ToolBookManifestSpec manifest) {
        ToolBookSummaryDTO dto = new ToolBookSummaryDTO();
        dto.setBookKey(manifest.getBookKey());
        dto.setTitle(manifest.getTitle());
        dto.setDescription(manifest.getDescription());
        dto.setStatus(manifest.getStatus());
        dto.setVersion(manifest.getVersion());
        dto.setDefaultEntryNodeKey(manifest.getDefaultEntryNodeKey());
        dto.setAliases(copyList(manifest.getAliases()));
        dto.setUseWhen(copyList(manifest.getUseWhen()));
        dto.setAvoidWhen(copyList(manifest.getAvoidWhen()));
        return dto;
    }

    private ToolBookNodeDTO toNodeDto(ToolBookManifestSpec.NodeSpec node) {
        ToolBookNodeDTO dto = new ToolBookNodeDTO();
        dto.setNodeKey(node.getNodeKey());
        dto.setParentKey(node.getParentKey());
        dto.setNodeType(node.getNodeType());
        dto.setTitle(node.getTitle());
        dto.setSummary(node.getSummary());
        dto.setDepth(node.getDepth());
        dto.setSortOrder(node.getSortOrder());
        dto.setTags(copyList(node.getTags()));
        return dto;
    }

    private ToolBookReadingPathDTO toReadingPathDto(ToolBookManifestSpec.ReadingPathSpec spec) {
        ToolBookReadingPathDTO dto = new ToolBookReadingPathDTO();
        dto.setScenario(spec.getScenario());
        dto.setNodeKeys(copyList(spec.getNodeKeys()));
        return dto;
    }

    private List<String> copyList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private Comparator<ToolBookManifestSpec.NodeSpec> nodeComparator() {
        return Comparator
                .comparing((ToolBookManifestSpec.NodeSpec node) -> node.getDepth() == null ? Integer.MAX_VALUE : node.getDepth())
                .thenComparing(node -> node.getSortOrder() == null ? Integer.MAX_VALUE : node.getSortOrder())
                .thenComparing(node -> node.getTitle() == null ? "" : node.getTitle());
    }

    private record LoadedToolBook(
            Resource manifestResource,
            ToolBookManifestSpec manifest,
            ToolBookSummaryDTO summary,
            Map<String, ToolBookManifestSpec.NodeSpec> nodeMap,
            List<ToolBookNodeDTO> sortedNodes,
            List<ToolBookReadingPathDTO> readingPaths
    ) {
    }
}
