package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetDocumentRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetDocumentResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentDraftRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentDraftResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentSummaryRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentSummaryResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDraftUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.repository.FileTextFragmentRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AgentToolDocumentHandler {

    private final DocumentService documentService;
    private final FileTextFragmentRepository fileTextFragmentRepository;

    public GetDocumentResponse getDocument(GetDocumentRequest request) {
        long userId = request.getUserId();
        long projectId = request.getProjectId();
        long documentId = request.getDocumentId();
        boolean readDraft = request.getReadDraft();

        DocumentDTO doc = documentService.get(userId, documentId, false);
        if (doc == null) {
            throw Status.NOT_FOUND.withDescription("Document not found: " + documentId).asRuntimeException();
        }

        if (projectId > 0 && doc.getProjectId() != null && doc.getProjectId() > 0
                && !doc.getProjectId().equals(projectId)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("getDocument: document not in project (documentId=" + documentId
                            + ", projectId=" + projectId + ")")
                    .asRuntimeException();
        }

        GetDocumentResponse.Builder responseBuilder = GetDocumentResponse.newBuilder()
                .setDocumentId(doc.getId());
        if (doc.getTitle() != null) {
            responseBuilder.setTitle(doc.getTitle());
        }
        responseBuilder.setContent(resolveContent(doc, readDraft));
        if (doc.getVersion() != null) {
            responseBuilder.setVersion(doc.getVersion());
        }
        if (doc.getUpdatedAt() != null) {
            responseBuilder.setUpdatedAt(doc.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } else if (doc.getDraftUpdatedAt() != null) {
            responseBuilder.setUpdatedAt(doc.getDraftUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        return responseBuilder.build();
    }

    public UpdateDocumentDraftResponse updateDocumentDraft(UpdateDocumentDraftRequest request) {
        long userId = request.getUserId();
        long projectId = request.getProjectId();
        long documentId = request.getDocumentId();
        String content = request.getContent();
        int expectedVersion = request.getExpectedVersion();

        if (content.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription("Content cannot be empty").asRuntimeException();
        }

        DocumentDTO currentDoc = documentService.get(userId, documentId, false);
        if (projectId > 0 && currentDoc != null && currentDoc.getProjectId() != null
                && currentDoc.getProjectId() > 0
                && !currentDoc.getProjectId().equals(projectId)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("updateDocumentDraft: document not in project (documentId=" + documentId
                            + ", projectId=" + projectId + ")")
                    .asRuntimeException();
        }
        if (currentDoc == null) {
            throw Status.NOT_FOUND.withDescription("Document not found: " + documentId).asRuntimeException();
        }
        if (currentDoc.getVersion() == null || currentDoc.getVersion() <= 0) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("updateDocumentDraft: document version is missing (documentId=" + documentId + ")")
                    .asRuntimeException();
        }

        DocumentDraftUpdateRequest updateRequest = new DocumentDraftUpdateRequest();
        updateRequest.setVersion(expectedVersion > 0 ? expectedVersion : currentDoc.getVersion());
        updateRequest.setDraftContent(content);

        DocumentDTO doc = documentService.updateDraft(userId, documentId, updateRequest);

        UpdateDocumentDraftResponse.Builder responseBuilder = UpdateDocumentDraftResponse.newBuilder()
                .setSuccess(true)
                .setDocumentId(doc.getId())
                .setNewVersion(doc.getVersion());
        if (doc.getUpdatedAt() != null) {
            responseBuilder.setUpdatedAt(doc.getUpdatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        return responseBuilder.build();
    }

    public UpdateDocumentSummaryResponse updateDocumentSummary(UpdateDocumentSummaryRequest request) {
        long userId = request.getUserId();
        long documentId = request.getDocumentId();
        String summary = request.getSummary();

        if (summary.isBlank()) {
            throw Status.INVALID_ARGUMENT.withDescription("Summary cannot be empty").asRuntimeException();
        }

        documentService.updateSummary(userId, documentId, summary);

        return UpdateDocumentSummaryResponse.newBuilder()
                .setSuccess(true)
                .setDocumentId(documentId)
                .setUpdatedAt(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()))
                .build();
    }

    private String resolveContent(DocumentDTO doc, boolean readDraft) {
        if (readDraft) {
            return Objects.toString(doc.getDraftContent(), "");
        }

        Long currentFileId = doc.getCurrentFileId();
        if (currentFileId == null || currentFileId <= 0) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("Document has not been published yet: documentId=" + doc.getId())
                    .asRuntimeException();
        }

        int totalFragments = fileTextFragmentRepository.countByFileId(currentFileId);
        if (totalFragments <= 0) {
            return "";
        }
        List<String> fragments = fileTextFragmentRepository.listFragmentContents(currentFileId, totalFragments);
        return String.join("\n\n", fragments);
    }
}
