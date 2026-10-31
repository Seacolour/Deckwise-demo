package com.xiaoyuan.paperpuppybackendintl.grpc.server.service;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.*;
import com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent 工具调用 gRPC 服务实现。
 * 
 * <p>
 * 职责：封装对现有 Service 的调用，提供给 Agent 模块使用。
 * </p>
 * 
 * <p>
 * 安全说明：所有响应都经过脱敏处理，不返回用户隐私信息。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolServiceGrpcImpl extends AgentToolServiceGrpc.AgentToolServiceImplBase {

    private final AgentToolFileHandler fileHandler;
    private final AgentToolDocumentHandler documentHandler;
    private final AgentToolCreateDocumentHandler createDocumentHandler;
    private final AgentToolBookHandler toolBookHandler;
    private final AgentToolUserPreferenceHandler userPreferenceHandler;
    private final AgentToolJsonStructureHandler jsonStructureHandler;
    private final AgentToolAgentRunHandler agentRunHandler;
    @Override
    public void listProjectFiles(ListProjectFilesRequest request,
            StreamObserver<ListProjectFilesResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();

            log.info("[AgentToolService] listProjectFiles: userId={}, projectId={}", userId, projectId);

            ListProjectFilesResponse response = fileHandler.listProjectFiles(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("[AgentToolService] listProjectFiles completed: userId={}, projectId={}, fileCount={}",
                    userId, projectId, response.getFilesCount());

        } catch (Exception e) {
            log.error("[AgentToolService] listProjectFiles failed: userId={}, projectId={}, error={}",
                    request.getUserId(), request.getProjectId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to list project files", e);
        }
    }

    @Override
    public void getDocument(GetDocumentRequest request,
            StreamObserver<GetDocumentResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();
            long documentId = request.getDocumentId();
            boolean readDraft = request.getReadDraft();

            log.info("[AgentToolService] getDocument: userId={}, projectId={}, documentId={}, readDraft={}",
                    userId, projectId, documentId, readDraft);

            GetDocumentResponse response = documentHandler.getDocument(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("[AgentToolService] getDocument completed: userId={}, documentId={}, titleLen={}, contentLen={}",
                    userId, documentId,
                    response.getTitle().length(),
                    response.getContent().length());

        } catch (Exception e) {
            log.error("[AgentToolService] getDocument failed: userId={}, documentId={}, error={}",
                    request.getUserId(), request.getDocumentId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to get document", e);
        }
    }

    @Override
    public void getFileContent(com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetFileContentRequest request,
            StreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetFileContentResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();
            long fileId = request.getFileId();
            int maxFragments = request.getMaxFragments() > 0 ? request.getMaxFragments() : 10;
            int startFragment = request.getStartFragment() > 0 ? request.getStartFragment() : 1;

            log.info("[AgentToolService] getFileContent: userId={}, projectId={}, fileId={}, startFragment={}, maxFragments={}",
                    userId, projectId, fileId, startFragment, maxFragments);

            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetFileContentResponse response = fileHandler
                    .getFileContent(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info(
                    "[AgentToolService] getFileContent completed: userId={}, fileId={}, returnedFragments={}, totalFragments={}, contentLen={}",
                    userId, fileId, response.getReturnedFragments(), response.getTotalFragments(), response.getContent().length());

        } catch (Exception e) {
            log.error("[AgentToolService] getFileContent failed: userId={}, fileId={}, error={}",
                    request.getUserId(), request.getFileId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to get file content", e);
        }
    }

    @Override
    public void getImage(GetImageRequest request, StreamObserver<GetImageResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();
            long fileId = request.getFileId();

            log.info("[AgentToolService] getImage: userId={}, projectId={}, fileId={}", userId, projectId, fileId);

            responseObserver.onNext(fileHandler.getImage(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] getImage failed: userId={}, fileId={}, error={}",
                    request.getUserId(), request.getFileId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to get image", e);
        }
    }





    @Override
    public void getFileUrl(GetFileUrlRequest request, StreamObserver<GetFileUrlResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();
            long fileId = request.getFileId();

            log.info("[AgentToolService] getFileUrl: userId={}, projectId={}, fileId={}", userId, projectId, fileId);

            responseObserver.onNext(fileHandler.getFileUrl(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] getFileUrl failed: userId={}, fileId={}, error={}",
                    request.getUserId(), request.getFileId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to get file url", e);
        }
    }

    @Override
    public void updateDocumentDraft(
            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentDraftRequest request,
            StreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentDraftResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();
            long documentId = request.getDocumentId();
            String content = request.getContent();
            String saveMessage = request.getSaveMessage();

            log.info(
                    "[AgentToolService] updateDocumentDraft: userId={}, projectId={}, documentId={}, contentLen={}, saveMessage={}",
                    userId, projectId, documentId, content != null ? content.length() : 0, saveMessage);

            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentDraftResponse response = documentHandler
                    .updateDocumentDraft(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("[AgentToolService] updateDocumentDraft completed: userId={}, documentId={}, newVersion={}",
                    userId, documentId, response.getNewVersion());

        } catch (Exception e) {
            log.error("[AgentToolService] updateDocumentDraft failed: userId={}, documentId={}, error={}",
                    request.getUserId(), request.getDocumentId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to update document draft", e);
        }
    }

    @Override
    public void updateDocumentSummary(
            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentSummaryRequest request,
            StreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.UpdateDocumentSummaryResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();
            long documentId = request.getDocumentId();
            String summary = request.getSummary();

            log.info("[AgentToolService] updateDocumentSummary: userId={}, projectId={}, documentId={}, summaryLen={}",
                    userId, projectId, documentId, summary != null ? summary.length() : 0);

            responseObserver.onNext(documentHandler.updateDocumentSummary(request));
            responseObserver.onCompleted();

            log.info("[AgentToolService] updateDocumentSummary completed: documentId={}", documentId);

        } catch (Exception e) {
            log.error("[AgentToolService] updateDocumentSummary failed: userId={}, documentId={}, error={}",
                    request.getUserId(), request.getDocumentId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to update document summary", e);
        }
    }

    /**
     * 创建 Markdown 文档（Agent 调用）。
     *
     * <p>实现策略：复用 FileService.uploadGeneratedWithBytes() 走完整文件上传链路，
     * 使 Agent 创建的文档拥有独立的 generated 文件身份，避免污染用户上传内容缓存：
     * <ol>
     *   <li>Markdown 内容上传到 R2（object_storage_files）</li>
     *   <li>建立项目-文件关联（project_file_links）</li>
     *   <li>建立用户-文件授权（file_access_grants）</li>
     *   <li>Markdown 文本分片落库（file_text_fragments）</li>
     *   <li>自动创建 Document + DocumentVersion v1（DocumentAutoCreateService）</li>
     *   <li>异步入队摘要生成</li>
     * </ol>
     * </p>
     */
    @Override
    public void createDocument(
            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.CreateDocumentRequest request,
            StreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.CreateDocumentResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            long projectId = request.getProjectId();
            String title = request.getTitle();
            String content = request.getContent();

            log.info("[AgentToolService] createDocument: userId={}, projectId={}, title={}, contentLen={}",
                    userId, projectId, title, content != null ? content.length() : 0);

            responseObserver.onNext(createDocumentHandler.createDocument(userId, projectId, title, content));
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("[AgentToolService] createDocument failed: userId={}, projectId={}, error={}",
                    request.getUserId(), request.getProjectId(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to create document", e);
        }
    }

    // ========================= User Rules =========================

    @Override
    public void manageUserRules(
            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserRulesRequest request,
            StreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserRulesResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            String action = request.getAction();

            log.info("[AgentToolService] manageUserRules: userId={}, action={}", userId, action);

            responseObserver.onNext(userPreferenceHandler.manageUserRules(request));
            responseObserver.onCompleted();

            log.info("[AgentToolService] manageUserRules completed: userId={}, action={}", userId, action);

        } catch (Exception e) {
            log.error("[AgentToolService] manageUserRules failed: userId={}, action={}, error={}",
                    request.getUserId(), request.getAction(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to manage user rules", e);
        }
    }

    // ========================= User Memory =========================

    @Override
    public void manageUserMemory(
            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserMemoryRequest request,
            StreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ManageUserMemoryResponse> responseObserver) {
        try {
            long userId = request.getUserId();
            String action = request.getAction();

            log.info("[AgentToolService] manageUserMemory: userId={}, action={}", userId, action);

            responseObserver.onNext(userPreferenceHandler.manageUserMemory(request));
            responseObserver.onCompleted();

            log.info("[AgentToolService] manageUserMemory completed: userId={}, action={}", userId, action);

        } catch (Exception e) {
            log.error("[AgentToolService] manageUserMemory failed: userId={}, action={}, error={}",
                    request.getUserId(), request.getAction(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to manage user memory", e);
        }
    }

    @Override
    public void readToolBook(
            com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ReadToolBookRequest request,
            StreamObserver<com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ReadToolBookResponse> responseObserver) {
        try {
            log.info("[AgentToolService] readToolBook: userId={}, projectId={}, bookKey={}, mode={}, nodeKey={}",
                    request.getUserId(), request.getProjectId(), request.getBookKey(), request.getMode(), request.getNodeKey());

            responseObserver.onNext(toolBookHandler.readToolBook(request));
            responseObserver.onCompleted();

            log.info("[AgentToolService] readToolBook completed: bookKey={}, mode={}",
                    request.getBookKey(), request.getMode());

        } catch (Exception e) {
            log.error("[AgentToolService] readToolBook failed: bookKey={}, mode={}, error={}",
                    request.getBookKey(), request.getMode(), e.getMessage(), e);
            propagateError(responseObserver, "Failed to read tool book", e);
        }
    }









    @Override
    public void jsonStructureRead(JsonStructureReadRequest request,
            StreamObserver<JsonStructureReadResponse> responseObserver) {
        try {
            log.info("[AgentToolService] jsonStructureRead: userId={}, projectId={}, fileId={}, documentId={}, path={}",
                    request.getUserId(), request.getProjectId(), request.getFileId(), request.getDocumentId(), request.getPath());
            responseObserver.onNext(jsonStructureHandler.jsonStructureRead(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] jsonStructureRead failed: fileId={}, documentId={}, err={}",
                    request.getFileId(), request.getDocumentId(), e.getMessage(), e);
            propagateError(responseObserver, "jsonStructureRead failed", e);
        }
    }

    @Override
    public void jsonSubtreeRead(JsonSubtreeReadRequest request,
            StreamObserver<JsonSubtreeReadResponse> responseObserver) {
        try {
            log.info("[AgentToolService] jsonSubtreeRead: userId={}, projectId={}, fileId={}, documentId={}, path={}",
                    request.getUserId(), request.getProjectId(), request.getFileId(), request.getDocumentId(), request.getPath());
            responseObserver.onNext(jsonStructureHandler.jsonSubtreeRead(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] jsonSubtreeRead failed: fileId={}, documentId={}, err={}",
                    request.getFileId(), request.getDocumentId(), e.getMessage(), e);
            propagateError(responseObserver, "jsonSubtreeRead failed", e);
        }
    }


    @Override
    public void upsertAgentRun(UpsertAgentRunRequest request,
            StreamObserver<AgentPersistenceAck> responseObserver) {
        try {
            agentRunHandler.upsertRun(request);
            responseObserver.onNext(AgentPersistenceAck.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] upsertAgentRun failed: runId={}, userId={}, err={}",
                    request.getAgentRunId(), request.getUserId(), e.getMessage(), e);
            propagateError(responseObserver, "upsertAgentRun failed", e);
        }
    }

    @Override
    public void finishAgentRun(FinishAgentRunRequest request,
            StreamObserver<AgentPersistenceAck> responseObserver) {
        try {
            agentRunHandler.finishRun(request);
            responseObserver.onNext(AgentPersistenceAck.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] finishAgentRun failed: runId={}, status={}, err={}",
                    request.getAgentRunId(), request.getStatus(), e.getMessage(), e);
            propagateError(responseObserver, "finishAgentRun failed", e);
        }
    }

    @Override
    public void recordAgentModelCall(RecordAgentModelCallRequest request,
            StreamObserver<AgentPersistenceAck> responseObserver) {
        try {
            agentRunHandler.recordModelCall(request);
            responseObserver.onNext(AgentPersistenceAck.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] recordAgentModelCall failed: runId={}, model={}, err={}",
                    request.getAgentRunId(), request.getModel(), e.getMessage(), e);
            propagateError(responseObserver, "recordAgentModelCall failed", e);
        }
    }

    @Override
    public void recordAgentContextCompaction(RecordAgentContextCompactionRequest request,
            StreamObserver<AgentPersistenceAck> responseObserver) {
        try {
            agentRunHandler.recordContextCompaction(request);
            responseObserver.onNext(AgentPersistenceAck.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] recordAgentContextCompaction failed: runId={}, sessionId={}, responseId={}, err={}",
                    request.getAgentRunId(), request.getSessionId(), request.getResponseId(), e.getMessage(), e);
            propagateError(responseObserver, "recordAgentContextCompaction failed", e);
        }
    }

    @Override
    public void recordAgentToolCall(RecordAgentToolCallRequest request,
            StreamObserver<AgentPersistenceAck> responseObserver) {
        try {
            agentRunHandler.recordToolCall(request);
            responseObserver.onNext(AgentPersistenceAck.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] recordAgentToolCall failed: runId={}, callId={}, tool={}, err={}",
                    request.getAgentRunId(), request.getCallId(), request.getToolName(), e.getMessage(), e);
            propagateError(responseObserver, "recordAgentToolCall failed", e);
        }
    }

    @Override
    public void upsertAgentLedgerSnapshot(UpsertAgentLedgerSnapshotRequest request,
            StreamObserver<AgentPersistenceAck> responseObserver) {
        try {
            agentRunHandler.upsertLedgerSnapshot(request);
            responseObserver.onNext(AgentPersistenceAck.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[AgentToolService] upsertAgentLedgerSnapshot failed: runId={}, err={}",
                    request.getAgentRunId(), e.getMessage(), e);
            propagateError(responseObserver, "upsertAgentLedgerSnapshot failed", e);
        }
    }

    @Override
    public void openAgentArtifactView(OpenAgentArtifactViewRequest request,
            StreamObserver<OpenAgentArtifactViewResponse> responseObserver) {
        try {
            log.info("[AgentToolService] openAgentArtifactView: userId={}, sessionId={}, projectId={}, viewKey={}, documentId={}, viewType={}, mode={}",
                    request.getUserId(),
                    request.getSessionId(),
                    request.getProjectId(),
                    request.getViewKey(),
                    request.getDocumentId(),
                    request.getViewType(),
                    request.getMode());
            OpenAgentArtifactViewResponse response = agentRunHandler.openArtifactView(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("[AgentToolService] openAgentArtifactView completed: userId={}, sessionId={}, success={}, resultLen={}",
                    request.getUserId(),
                    request.getSessionId(),
                    response.getSuccess(),
                    response.getResultJson().length());
        } catch (Exception e) {
            log.error("[AgentToolService] openAgentArtifactView failed: userId={}, sessionId={}, viewKey={}, err={}",
                    request.getUserId(), request.getSessionId(), request.getViewKey(), e.getMessage(), e);
            responseObserver.onNext(OpenAgentArtifactViewResponse.newBuilder()
                    .setSuccess(false)
                    .setError("openAgentArtifactView failed: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private void propagateError(StreamObserver<?> responseObserver, String prefix, Exception e) {
        if (e instanceof StatusRuntimeException sre) {
            responseObserver.onError(sre);
            return;
        }
        responseObserver.onError(Status.INTERNAL
                .withDescription(prefix + ": " + e.getMessage())
                .withCause(e)
                .asRuntimeException());
    }
}
