package com.xiaoyuan.paperpuppyagent.grpc.client;

import com.xiaoyuan.paperpuppyagent.grpc.backend.AgentToolServiceGrpc;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetDocumentRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetDocumentResponse;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetImageRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.GetImageResponse;
import com.xiaoyuan.paperpuppyagent.grpc.backend.ListProjectFilesRequest;
import com.xiaoyuan.paperpuppyagent.grpc.backend.ListProjectFilesResponse;
import com.xiaoyuan.paperpuppyagent.grpc.config.BackendToolGrpcProperties;
import io.grpc.Deadline;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Backend Tool gRPC 客户端封装。
 * 
 * <p>
 * 职责：封装对 Backend AgentToolService 的调用，提供给工具使用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackendToolGrpcClient {

        private final AgentToolServiceGrpc.AgentToolServiceBlockingStub stub;
        private final BackendToolGrpcProperties props;

        /**
         * 列出项目文件。
         */
        public ListProjectFilesResponse listProjectFiles(long userId, long projectId) {
                try {
                        ListProjectFilesRequest request = ListProjectFilesRequest.newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .build();

                        log.info("[BackendToolGrpcClient] listProjectFiles: userId={}, projectId={}", userId,
                                        projectId);

                        ListProjectFilesResponse response = withDeadline().listProjectFiles(request);

                        log.info("[BackendToolGrpcClient] listProjectFiles completed: userId={}, projectId={}, fileCount={}",
                                        userId, projectId, response.getFilesCount());

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] listProjectFiles failed: userId={}, projectId={}, status={}, error={}",
                                        userId, projectId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to list project files: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] listProjectFiles failed: userId={}, projectId={}, error={}",
                                        userId, projectId, e.getMessage(), e);
                        throw new RuntimeException("Failed to list project files: " + e.getMessage(), e);
                }
        }

        /**
         * 读取文档内容。
         */
        public GetDocumentResponse getDocument(long userId, long projectId, long documentId, boolean readDraft) {
                try {
                        GetDocumentRequest request = GetDocumentRequest.newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setDocumentId(documentId)
                                        .setReadDraft(readDraft)
                                        .build();

                        log.info("[BackendToolGrpcClient] getDocument: userId={}, projectId={}, documentId={}, readDraft={}",
                                        userId, projectId, documentId, readDraft);

                        GetDocumentResponse response = withDeadline().getDocument(request);

                        log.info(
                                        "[BackendToolGrpcClient] getDocument completed: userId={}, documentId={}, titleLen={}, contentLen={}",
                                        userId, documentId,
                                        response.getTitle() != null ? response.getTitle().length() : 0,
                                        response.getContent() != null ? response.getContent().length() : 0);

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] getDocument failed: userId={}, documentId={}, status={}, error={}",
                                        userId, documentId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to get document: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] getDocument failed: userId={}, documentId={}, error={}",
                                        userId, documentId, e.getMessage(), e);
                        throw new RuntimeException("Failed to get document: " + e.getMessage(), e);
                }
        }

        /**
         * 获取图片 URL（用于多模态输入）。
         */
        public GetImageResponse getImage(long userId, long projectId, long fileId) {
                try {
                        GetImageRequest request = GetImageRequest.newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setFileId(fileId)
                                        .build();

                        log.info("[BackendToolGrpcClient] getImage: userId={}, projectId={}, fileId={}", userId, projectId, fileId);

                        GetImageResponse response = withDeadline().getImage(request);

                        log.info("[BackendToolGrpcClient] getImage completed: userId={}, fileId={}, urlLen={}",
                                        userId, fileId,
                                        response.getImageUrl() != null ? response.getImageUrl().length() : 0);

                        return response;
                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] getImage failed: userId={}, fileId={}, status={}, error={}",
                                        userId, fileId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to get image: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] getImage failed: userId={}, fileId={}, error={}",
                                        userId, fileId, e.getMessage(), e);
                        throw new RuntimeException("Failed to get image: " + e.getMessage(), e);
                }
        }

        /**
         * 搜索项目图片候选。
         */
        public com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileUrlResponse getFileUrl(
                        long userId, long projectId, long fileId) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileUrlRequest request =
                                com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileUrlRequest.newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setFileId(fileId)
                                        .build();

                        log.info("[BackendToolGrpcClient] getFileUrl: userId={}, projectId={}, fileId={}", userId, projectId, fileId);

                        com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileUrlResponse response = withDeadline().getFileUrl(request);

                        log.info("[BackendToolGrpcClient] getFileUrl completed: userId={}, fileId={}, urlLen={}",
                                        userId, fileId,
                                        response.getFileUrl() != null ? response.getFileUrl().length() : 0);

                        return response;
                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] getFileUrl failed: userId={}, fileId={}, status={}, error={}",
                                        userId, fileId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to get file url: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] getFileUrl failed: userId={}, fileId={}, error={}",
                                        userId, fileId, e.getMessage(), e);
                        throw new RuntimeException("Failed to get file url: " + e.getMessage(), e);
                }
        }

        /**
         * 读取文件切片内容（用于 PDF 等无 Document 副本的文件）
         */
        public com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileContentResponse getFileContent(
                        long userId, long projectId, long fileId, int startFragment, int maxFragments) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileContentRequest request = com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileContentRequest
                                        .newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setFileId(fileId)
                                        .setMaxFragments(maxFragments)
                                        .setStartFragment(startFragment)
                                        .build();

                        log.info("[BackendToolGrpcClient] getFileContent: userId={}, projectId={}, fileId={}, startFragment={}, maxFragments={}",
                                        userId, projectId, fileId, startFragment, maxFragments);

                        com.xiaoyuan.paperpuppyagent.grpc.backend.GetFileContentResponse response = withDeadline()
                                        .getFileContent(request);

                        log.info(
                                        "[BackendToolGrpcClient] getFileContent completed: userId={}, fileId={}, returnedFragments={}, contentLen={}",
                                        userId, fileId, response.getReturnedFragments(),
                                        response.getContent() != null ? response.getContent().length() : 0);

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] getFileContent failed: userId={}, fileId={}, status={}, error={}",
                                        userId, fileId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to get file content: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] getFileContent failed: userId={}, fileId={}, error={}",
                                        userId, fileId, e.getMessage(), e);
                        throw new RuntimeException("Failed to get file content: " + e.getMessage(), e);
                }
        }

        /**
         * 更新文档草稿
         */
        public com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentDraftResponse updateDocumentDraft(
                        long userId, long projectId, long documentId, int expectedVersion, String content,
                        String saveMessage) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentDraftRequest.Builder requestBuilder = com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentDraftRequest
                                        .newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setDocumentId(documentId)
                                        .setContent(content)
                                        .setExpectedVersion(expectedVersion);

                        if (saveMessage != null && !saveMessage.isEmpty()) {
                                requestBuilder.setSaveMessage(saveMessage);
                        }

                        log.info("[BackendToolGrpcClient] updateDocumentDraft: userId={}, documentId={}, contentLen={}",
                                        userId, documentId, content.length());

                        com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentDraftResponse response = withDeadline()
                                        .updateDocumentDraft(requestBuilder.build());

                        log.info("[BackendToolGrpcClient] updateDocumentDraft completed: documentId={}, newVersion={}",
                                        documentId, response.getNewVersion());

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] updateDocumentDraft failed: documentId={}, status={}, error={}",
                                        documentId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to update document draft: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] updateDocumentDraft failed: documentId={}, error={}",
                                        documentId, e.getMessage(), e);
                        throw new RuntimeException("Failed to update document draft: " + e.getMessage(), e);
                }
        }

        /**
         * 更新文档摘要
         */
        public com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentSummaryResponse updateDocumentSummary(
                        long userId, long projectId, long documentId, String summary) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentSummaryRequest request = com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentSummaryRequest
                                        .newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setDocumentId(documentId)
                                        .setSummary(summary)
                                        .build();

                        log.info("[BackendToolGrpcClient] updateDocumentSummary: userId={}, documentId={}, summaryLen={}",
                                        userId, documentId, summary.length());

                        com.xiaoyuan.paperpuppyagent.grpc.backend.UpdateDocumentSummaryResponse response = withDeadline()
                                        .updateDocumentSummary(request);

                        log.info("[BackendToolGrpcClient] updateDocumentSummary completed: documentId={}, success={}",
                                        documentId, response.getSuccess());

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] updateDocumentSummary failed: documentId={}, status={}, error={}",
                                        documentId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to update document summary: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] updateDocumentSummary failed: documentId={}, error={}",
                                        documentId, e.getMessage(), e);
                        throw new RuntimeException("Failed to update document summary: " + e.getMessage(), e);
                }
        }

        /**
         * 创建 Markdown 文档
         */
        public com.xiaoyuan.paperpuppyagent.grpc.backend.CreateDocumentResponse createDocument(
                        long userId, long projectId, String title, String content) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.CreateDocumentRequest.Builder requestBuilder =
                                        com.xiaoyuan.paperpuppyagent.grpc.backend.CreateDocumentRequest.newBuilder()
                                                        .setUserId(userId)
                                                        .setProjectId(projectId)
                                                        .setTitle(title);

                        if (content != null && !content.isEmpty()) {
                                requestBuilder.setContent(content);
                        }

                        log.info("[BackendToolGrpcClient] createDocument: userId={}, projectId={}, title={}, contentLen={}",
                                        userId, projectId, title, content != null ? content.length() : 0);

                        com.xiaoyuan.paperpuppyagent.grpc.backend.CreateDocumentResponse response = withDeadline()
                                        .createDocument(requestBuilder.build());

                        log.info("[BackendToolGrpcClient] createDocument completed: userId={}, projectId={}, documentId={}",
                                        userId, projectId, response.getDocumentId());

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] createDocument failed: userId={}, projectId={}, status={}, error={}",
                                        userId, projectId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to create document: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] createDocument failed: userId={}, projectId={}, error={}",
                                        userId, projectId, e.getMessage(), e);
                        throw new RuntimeException("Failed to create document: " + e.getMessage(), e);
                }
        }

        public com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserRulesResponse manageUserRules(
                        long userId, String action, long ruleId, String content) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserRulesRequest.Builder requestBuilder =
                                        com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserRulesRequest.newBuilder()
                                                        .setUserId(userId)
                                                        .setAction(action);
                        if (ruleId > 0) {
                                requestBuilder.setRuleId(ruleId);
                        }
                        if (content != null && !content.isEmpty()) {
                                requestBuilder.setContent(content);
                        }

                        log.info("[BackendToolGrpcClient] manageUserRules: userId={}, action={}, ruleId={}", userId, action, ruleId);

                        com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserRulesResponse response = withDeadline()
                                        .manageUserRules(requestBuilder.build());

                        log.info("[BackendToolGrpcClient] manageUserRules completed: userId={}, action={}, success={}, msg={}",
                                        userId, action, response.getSuccess(), response.getMessage());

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] manageUserRules failed: userId={}, action={}, status={}, error={}",
                                        userId, action, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to manage user rules: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] manageUserRules failed: userId={}, action={}, error={}",
                                        userId, action, e.getMessage(), e);
                        throw new RuntimeException("Failed to manage user rules: " + e.getMessage(), e);
                }
        }

        /**
         * 管理用户记忆
         */
        public com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserMemoryResponse manageUserMemory(
                        long userId, String action, long memoryId, String title, String content) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserMemoryRequest.Builder requestBuilder =
                                        com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserMemoryRequest.newBuilder()
                                                        .setUserId(userId)
                                                        .setAction(action);
                        if (memoryId > 0) {
                                requestBuilder.setMemoryId(memoryId);
                        }
                        if (title != null && !title.isEmpty()) {
                                requestBuilder.setTitle(title);
                        }
                        if (content != null && !content.isEmpty()) {
                                requestBuilder.setContent(content);
                        }

                        log.info("[BackendToolGrpcClient] manageUserMemory: userId={}, action={}, memoryId={}", userId, action, memoryId);

                        com.xiaoyuan.paperpuppyagent.grpc.backend.ManageUserMemoryResponse response = withDeadline()
                                        .manageUserMemory(requestBuilder.build());

                        log.info("[BackendToolGrpcClient] manageUserMemory completed: userId={}, action={}, success={}, msg={}",
                                        userId, action, response.getSuccess(), response.getMessage());

                        return response;

                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] manageUserMemory failed: userId={}, action={}, status={}, error={}",
                                        userId, action, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to manage user memory: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] manageUserMemory failed: userId={}, action={}, error={}",
                                        userId, action, e.getMessage(), e);
                        throw new RuntimeException("Failed to manage user memory: " + e.getMessage(), e);
                }
        }

        public com.xiaoyuan.paperpuppyagent.grpc.backend.ReadToolBookResponse readToolBook(
                        long userId, long projectId, String bookKey, String mode, String nodeKey, int maxChars) {
                try {
                        com.xiaoyuan.paperpuppyagent.grpc.backend.ReadToolBookRequest.Builder requestBuilder =
                                        com.xiaoyuan.paperpuppyagent.grpc.backend.ReadToolBookRequest.newBuilder()
                                                        .setUserId(userId)
                                                        .setProjectId(projectId);
                        if (bookKey != null && !bookKey.isBlank()) {
                                requestBuilder.setBookKey(bookKey);
                        }
                        if (mode != null && !mode.isBlank()) {
                                requestBuilder.setMode(mode);
                        }
                        if (nodeKey != null && !nodeKey.isBlank()) {
                                requestBuilder.setNodeKey(nodeKey);
                        }
                        if (maxChars > 0) {
                                requestBuilder.setMaxChars(maxChars);
                        }

                        log.info("[BackendToolGrpcClient] readToolBook: userId={}, projectId={}, mode={}, bookKey={}, nodeKey={}, maxChars={}",
                                        userId, projectId, mode, bookKey, nodeKey, maxChars);

                        com.xiaoyuan.paperpuppyagent.grpc.backend.ReadToolBookResponse response = withDeadline()
                                        .readToolBook(requestBuilder.build());

                        log.info("[BackendToolGrpcClient] readToolBook completed: userId={}, mode={}, bookKey={}, success={}",
                                        userId, response.getMode(), response.getBookKey(), response.getSuccess());

                        return response;
                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] readToolBook failed: userId={}, mode={}, bookKey={}, status={}, error={}",
                                        userId, mode, bookKey, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to read tool book: " + e.getMessage(), e);
                } catch (Exception e) {
                        log.error("[BackendToolGrpcClient] readToolBook failed: userId={}, mode={}, bookKey={}, error={}",
                                        userId, mode, bookKey, e.getMessage(), e);
                        throw new RuntimeException("Failed to read tool book: " + e.getMessage(), e);
                }
        }















        public com.xiaoyuan.paperpuppyagent.grpc.backend.JsonStructureReadResponse jsonStructureRead(
                        long userId, long projectId, long fileId, long documentId, String path,
                        int maxDepth, int sampleArrayItems, int maxKeysPerObject) {
                try {
                        var request = com.xiaoyuan.paperpuppyagent.grpc.backend.JsonStructureReadRequest.newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setFileId(fileId)
                                        .setDocumentId(documentId)
                                        .setPath(path == null ? "/" : path)
                                        .setMaxDepth(maxDepth)
                                        .setSampleArrayItems(sampleArrayItems)
                                        .setMaxKeysPerObject(maxKeysPerObject)
                                        .build();
                        log.info("[BackendToolGrpcClient] jsonStructureRead: userId={}, projectId={}, fileId={}, documentId={}, path={}",
                                        userId, projectId, fileId, documentId, path);
                        return withDeadlineLong().jsonStructureRead(request);
                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] jsonStructureRead failed: fileId={}, documentId={}, status={}, err={}",
                                        fileId, documentId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to read json structure: " + e.getMessage(), e);
                }
        }

        public com.xiaoyuan.paperpuppyagent.grpc.backend.JsonSubtreeReadResponse jsonSubtreeRead(
                        long userId, long projectId, long fileId, long documentId, String path,
                        int maxBytes, int maxNodes) {
                try {
                        var request = com.xiaoyuan.paperpuppyagent.grpc.backend.JsonSubtreeReadRequest.newBuilder()
                                        .setUserId(userId)
                                        .setProjectId(projectId)
                                        .setFileId(fileId)
                                        .setDocumentId(documentId)
                                        .setPath(path == null ? "/" : path)
                                        .setMaxBytes(maxBytes)
                                        .setMaxNodes(maxNodes)
                                        .build();
                        log.info("[BackendToolGrpcClient] jsonSubtreeRead: userId={}, projectId={}, fileId={}, documentId={}, path={}",
                                        userId, projectId, fileId, documentId, path);
                        return withDeadlineLong().jsonSubtreeRead(request);
                } catch (StatusRuntimeException e) {
                        log.error("[BackendToolGrpcClient] jsonSubtreeRead failed: fileId={}, documentId={}, status={}, err={}",
                                        fileId, documentId, e.getStatus(), e.getMessage(), e);
                        throw new RuntimeException("Failed to read json subtree: " + e.getMessage(), e);
                }
        }

        public void upsertAgentRun(com.xiaoyuan.paperpuppyagent.grpc.backend.UpsertAgentRunRequest request) {
                try {
                        withDeadline().upsertAgentRun(request);
                } catch (StatusRuntimeException e) {
                        throw new RuntimeException("upsertAgentRun failed: " + e.getMessage(), e);
                }
        }

        public void finishAgentRun(com.xiaoyuan.paperpuppyagent.grpc.backend.FinishAgentRunRequest request) {
                try {
                        withDeadline().finishAgentRun(request);
                } catch (StatusRuntimeException e) {
                        throw new RuntimeException("finishAgentRun failed: " + e.getMessage(), e);
                }
        }

        public void recordAgentModelCall(com.xiaoyuan.paperpuppyagent.grpc.backend.RecordAgentModelCallRequest request) {
                try {
                        withDeadline().recordAgentModelCall(request);
                } catch (StatusRuntimeException e) {
                        throw new RuntimeException("recordAgentModelCall failed: " + e.getMessage(), e);
                }
        }

        public void recordAgentContextCompaction(
                        com.xiaoyuan.paperpuppyagent.grpc.backend.RecordAgentContextCompactionRequest request) {
                try {
                        withDeadline().recordAgentContextCompaction(request);
                } catch (StatusRuntimeException e) {
                        throw new RuntimeException("recordAgentContextCompaction failed: " + e.getMessage(), e);
                }
        }

        public void recordAgentToolCall(com.xiaoyuan.paperpuppyagent.grpc.backend.RecordAgentToolCallRequest request) {
                try {
                        withDeadline().recordAgentToolCall(request);
                } catch (StatusRuntimeException e) {
                        throw new RuntimeException("recordAgentToolCall failed: " + e.getMessage(), e);
                }
        }

        public void upsertAgentLedgerSnapshot(
                        com.xiaoyuan.paperpuppyagent.grpc.backend.UpsertAgentLedgerSnapshotRequest request) {
                try {
                        withDeadline().upsertAgentLedgerSnapshot(request);
                } catch (StatusRuntimeException e) {
                        throw new RuntimeException("upsertAgentLedgerSnapshot failed: " + e.getMessage(), e);
                }
        }

        public com.xiaoyuan.paperpuppyagent.grpc.backend.OpenAgentArtifactViewResponse openAgentArtifactView(
                        long userId,
                        long sessionId,
                        long projectId,
                        String viewKey,
                        long documentId,
                        String viewType,
                        String mode,
                        String path,
                        int maxChars) {
                try {
                        var request = com.xiaoyuan.paperpuppyagent.grpc.backend.OpenAgentArtifactViewRequest.newBuilder()
                                        .setUserId(userId)
                                        .setSessionId(sessionId)
                                        .setProjectId(projectId)
                                        .setViewKey(viewKey == null ? "" : viewKey)
                                        .setDocumentId(documentId)
                                        .setViewType(viewType == null ? "" : viewType)
                                        .setMode(mode == null ? "" : mode)
                                        .setPath(path == null ? "" : path)
                                        .setMaxChars(maxChars)
                                        .build();
                        log.info("[BackendToolGrpcClient] openAgentArtifactView: userId={}, sessionId={}, projectId={}, viewKey={}, documentId={}, viewType={}, mode={}",
                                        userId, sessionId, projectId, viewKey, documentId, viewType, mode);
                        return withDeadline().openAgentArtifactView(request);
                } catch (StatusRuntimeException e) {
                        throw new RuntimeException("openAgentArtifactView failed: " + e.getMessage(), e);
                }
        }

        private static final long DEFAULT_DEADLINE_MS = 30000L;
        private static final long LONG_DEADLINE_MS = 120000L;

        private AgentToolServiceGrpc.AgentToolServiceBlockingStub withDeadline() {
                long deadlineMs = props != null && props.deadlineMs() > 0 ? props.deadlineMs() : DEFAULT_DEADLINE_MS;
                return stub.withDeadline(Deadline.after(deadlineMs, TimeUnit.MILLISECONDS));
        }

        private AgentToolServiceGrpc.AgentToolServiceBlockingStub withDeadlineLong() {
                return stub.withDeadline(Deadline.after(LONG_DEADLINE_MS, TimeUnit.MILLISECONDS));
        }

}
