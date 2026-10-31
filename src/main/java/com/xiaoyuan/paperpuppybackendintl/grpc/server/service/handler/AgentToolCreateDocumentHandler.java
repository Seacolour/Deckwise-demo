package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.CreateDocumentResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.utils.file.InMemoryMultipartFile;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@Slf4j
@RequiredArgsConstructor
public class AgentToolCreateDocumentHandler {

    private final FileService fileService;
    private final DocumentService documentService;

    public CreateDocumentResponse createDocument(long userId, long projectId, String title, String content) {
        if (title == null || title.isBlank()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Title cannot be empty")
                    .asRuntimeException();
        }

        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > 200) {
            normalizedTitle = normalizedTitle.substring(0, 200);
        }

        String filename = buildMarkdownFilename(normalizedTitle);
        String effectiveContent = (content == null || content.isBlank())
                ? "# " + normalizedTitle + "\n"
                : content;

        byte[] bytes = effectiveContent.getBytes(StandardCharsets.UTF_8);
        FileUploadCacheResponse uploaded = fileService.uploadGeneratedWithBytes(
                userId,
                new InMemoryMultipartFile("file", filename, "text/markdown", bytes),
                projectId,
                bytes);

        Long documentId = uploaded.getDocumentId();
        if (documentId == null || documentId <= 0) {
            log.warn("[AgentToolService] createDocument: auto-create document not triggered, fallback to DocumentService.create(). fileId={}",
                    uploaded.getSourceFileId());
            DocumentCreateRequest createReq = new DocumentCreateRequest();
            createReq.setProjectId(projectId);
            createReq.setTitle(normalizedTitle);
            createReq.setDraftContent(content);
            DocumentDTO fallbackDoc = documentService.create(userId, createReq);
            documentId = fallbackDoc.getId();
        }

        DocumentDTO doc = documentService.get(userId, documentId, false);

        CreateDocumentResponse.Builder responseBuilder = CreateDocumentResponse.newBuilder()
                .setSuccess(true)
                .setDocumentId(doc.getId());
        if (doc.getTitle() != null) {
            responseBuilder.setTitle(doc.getTitle());
        }
        if (doc.getVersion() != null) {
            responseBuilder.setVersion(doc.getVersion());
        }
        if (doc.getCreatedAt() != null) {
            responseBuilder.setCreatedAt(doc.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } else {
            responseBuilder.setCreatedAt(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));
        }

        log.info("[AgentToolService] createDocument completed: userId={}, projectId={}, documentId={}, title={}, fileId={}",
                userId, projectId, doc.getId(), doc.getTitle(), uploaded.getSourceFileId());
        return responseBuilder.build();
    }

    private String buildMarkdownFilename(String title) {
        String base = (title == null || title.isBlank()) ? "untitled" : title.trim();
        base = base.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").trim();
        base = base.replaceAll("\\s{2,}", " ");
        if (base.isBlank()) {
            base = "untitled";
        }
        if (!base.toLowerCase(Locale.ROOT).endsWith(".md")) {
            base = base + ".md";
        }
        if (base.length() > 200) {
            base = base.substring(0, 197) + ".md";
        }
        return base;
    }
}
