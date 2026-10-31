package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.AgentGrpcClient;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.ChatGrpcCall;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.ChatStreamObserver;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatMetadata;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.ChatCiFileRef;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import com.xiaoyuan.paperpuppybackendintl.service.billing.AiUsageBillingService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import com.xiaoyuan.paperpuppybackendintl.service.session.stream.AgentEvent;
import com.xiaoyuan.paperpuppybackendintl.service.session.stream.AgentEventType;
import com.xiaoyuan.paperpuppybackendintl.service.session.stream.AgentSseBridge;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates the Agent gRPC stream, ordered SSE events, and assistant-message persistence.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentStreamCoordinator {

    private final AgentGrpcClient agentGrpcClient;
    private final ChatExecutionLifecycle chatExecutionLifecycle;
    private final SessionMessageRepository sessionMessageRepository;
    private final SessionService sessionService;
    private final AgentRunPersistenceRepository agentRunPersistenceRepository;
    private final AiUsageBillingService aiUsageBillingService;
    @Qualifier("chatExecutor")
    private final TaskExecutor chatExecutor;
    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;

    public record StartInput(Long userId,
                             Session session,
                             ChatExecutionLifecycle.Handle executionHandle,
                             AiChatRequest request,
                             String requestedServiceTier,
                             String effectiveModel,
                             Long userMessageId) {
    }

    private enum AssistantOutputEntryKind {
        TEXT,
        TOOL
    }

    private record AssistantOutputEntry(AssistantOutputEntryKind kind,
                                        String text,
                                        Map<String, Object> toolEvent) {
        private static AssistantOutputEntry text(String text) {
            return new AssistantOutputEntry(AssistantOutputEntryKind.TEXT, text, null);
        }

        private static AssistantOutputEntry tool(Map<String, Object> toolEvent) {
            return new AssistantOutputEntry(AssistantOutputEntryKind.TOOL, null, toolEvent);
        }
    }

    static final class AssistantOutputAccumulator {
        private final StringBuilder pendingText = new StringBuilder();
        private final StringBuilder fullText = new StringBuilder();
        private final List<AssistantOutputEntry> entries = new ArrayList<>();

        void appendText(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            pendingText.append(chunk);
            fullText.append(chunk);
        }

        void appendToolEvent(Map<String, Object> event) {
            if (event == null || event.isEmpty()) {
                return;
            }
            flushText();
            entries.add(AssistantOutputEntry.tool(event));
        }

        boolean hasAnyOutput() {
            return !pendingText.isEmpty() || !entries.isEmpty();
        }

        String fullText() {
            return fullText.toString();
        }

        List<AssistantOutputEntry> orderedEntries() {
            flushText();
            return List.copyOf(entries);
        }

        private void flushText() {
            if (pendingText.isEmpty()) {
                return;
            }
            entries.add(AssistantOutputEntry.text(pendingText.toString()));
            pendingText.setLength(0);
        }
    }

    public SseEmitter start(StartInput input) {
        return new StreamExecution(input).start();
    }

    private final class StreamExecution {
        private final StartInput input;
        private final SseEmitter emitter = new SseEmitter(0L);
        private final AssistantOutputAccumulator assistantOutput = new AssistantOutputAccumulator();
        private final Map<String, String> responseMeta = new HashMap<>();
        private final List<Map<String, Object>> metadataList = new ArrayList<>();
        private final List<Map<String, Object>> toolEventList = new ArrayList<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final AtomicBoolean finalized = new AtomicBoolean(false);
        private final AtomicBoolean sseConnected = new AtomicBoolean(true);
        private final AtomicBoolean renewStop = new AtomicBoolean(false);
        private final AtomicLong seq = new AtomicLong(0);
        private final AtomicReference<String> endReason = new AtomicReference<>("unknown");
        private final ChatGrpcCall[] callRef = new ChatGrpcCall[1];
        private final AgentSseBridge bridge = new AgentSseBridge(emitter, 512, chatExecutor);

        private StreamExecution(StartInput input) {
            this.input = input;
            if (input.effectiveModel() != null && !input.effectiveModel().isBlank()) {
                responseMeta.put("requested_model", input.effectiveModel());
            }
            if (input.request().getProviderId() != null && !input.request().getProviderId().isBlank()) {
                responseMeta.put("provider", input.request().getProviderId());
            }
            responseMeta.put("requested_service_tier", input.requestedServiceTier());
        }

        private SseEmitter start() {
            Runnable disconnected = this::onSseDisconnected;
            emitter.onCompletion(disconnected);
            emitter.onTimeout(disconnected);
            emitter.onError(error -> disconnected.run());
            startLockRenewal();
            chatExecutor.execute(this::startGrpc);
            return emitter;
        }

        private void onSseDisconnected() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            sseConnected.set(false);
            bridge.markDisconnected();
            endReason.set("sse_disconnected");
            log.info("chat end: sse disconnected -> cancel gRPC and finish: userId={}, sessionId={}, reason={}",
                    input.userId(), input.session().getId(), endReason.get());
            cancelCall("sse disconnected");
            try {
                if (assistantOutput.hasAnyOutput() || !metadataList.isEmpty() || !toolEventList.isEmpty()) {
                    saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta, metadataList,
                            SessionMessageStatus.ABORTED);
                }
            } catch (Exception ignored) {
            } finally {
                finish();
            }
        }

        private void startLockRenewal() {
            long renewStartMs = System.currentTimeMillis();
            long maxRenewDurationMs = 10 * 60 * 1000L;
            chatExecutor.execute(() -> {
                try {
                    while (!renewStop.get()) {
                        Thread.sleep(20_000L);
                        if (renewStop.get()) {
                            break;
                        }
                        if (System.currentTimeMillis() - renewStartMs >= maxRenewDurationMs) {
                            log.warn("chat lock renew reached max duration, cancel job to avoid permanent lock: userId={}, sessionId={}, maxMs={}",
                                    input.userId(), input.session().getId(), maxRenewDurationMs);
                            if (!completed.compareAndSet(false, true)) {
                                return;
                            }
                            cancelCall("renew cap reached");
                            emit(AgentEventType.ERROR, "AI 调用超时");
                            saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta,
                                    metadataList, SessionMessageStatus.ERROR);
                            endReason.set("renew_cap");
                            finish();
                            return;
                        }
                        boolean renewed = chatExecutionLifecycle.renewLock(input.executionHandle());
                        if (!renewed) {
                            Long ttl = chatExecutionLifecycle.lockTtlSeconds(input.executionHandle());
                            log.warn("chat lock renew failed (lost lock?): userId={}, sessionId={}, ttlSec={}, expectedToken={}",
                                    input.userId(), input.session().getId(), ttl,
                                    input.executionHandle().lockToken());
                        }
                    }
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                } catch (Exception exception) {
                    log.warn("chat lock renew loop error: {}", exception.getMessage());
                }
            });
        }

        private void startGrpc() {
            try {
                AiChatRequest request = input.request();
                int contextLength = request.getFullContextJson() == null ? -1 : request.getFullContextJson().length();
                int imageCount = request.getImageUrls() == null ? 0 : request.getImageUrls().size();
                log.info("AI gRPC call start: userId={}, sessionId={}, enableImage={}, imageCount={}, fullContextJsonLen={}",
                        input.userId(), input.session().getId(), request.isEnableImage(), imageCount, contextLength);
                callRef[0] = agentGrpcClient.chatStream(request, new ChatStreamObserver() {
                    @Override
                    public void onText(String chunk) {
                        if (completed.get() || abortIfRequested()) {
                            return;
                        }
                        if (chunk != null && !chunk.isEmpty()) {
                            assistantOutput.appendText(chunk);
                            emit(AgentEventType.TEXT, chunk);
                        }
                    }

                    @Override
                    public void onMetadata(AiChatMetadata metadata) {
                        if (completed.get() || abortIfRequested()) {
                            return;
                        }
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("keyword", metadata != null ? metadata.getKeyword() : null);
                        value.put("info", metadata != null && metadata.getInfo() != null
                                ? metadata.getInfo() : Map.of());
                        value.put("links", getMaps(metadata));
                        metadataList.add(value);
                        emit(AgentEventType.METADATA, value);
                    }

                    @Override
                    public void onToolCall(String callId, String name, String argumentsJson) {
                        if (completed.get() || abortIfRequested()) {
                            return;
                        }
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("phase", "call");
                        payload.put("call_id", callId);
                        payload.put("name", name);
                        payload.put("arguments_json", summarizeToolArgumentsJson(name, argumentsJson));
                        toolEventList.add(payload);
                        assistantOutput.appendToolEvent(payload);
                        emit(AgentEventType.TOOL, payload);
                    }

                    @Override
                    public void onToolResult(String callId, String name, String resultJson) {
                        if (completed.get() || abortIfRequested()) {
                            return;
                        }
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("phase", "result");
                        payload.put("call_id", callId);
                        payload.put("name", name);
                        payload.put("result_json", summarizeToolResultJson(name, resultJson));
                        toolEventList.add(payload);
                        assistantOutput.appendToolEvent(payload);
                        emit(AgentEventType.TOOL, payload);

                        Map<String, Object> artifact = tryExtractUiArtifactMetadata(name, resultJson);
                        if (artifact != null) {
                            metadataList.add(artifact);
                            emit(AgentEventType.METADATA, artifact);
                        }
                    }

                    @Override
                    public void onResponse(Map<String, String> values, List<ChatCiFileRef> ciFileRefs) {
                        captureResponse(values, ciFileRefs);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (completed.get() || abortIfRequested()) {
                            return;
                        }
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        if (throwable instanceof StatusRuntimeException statusException
                                && statusException.getStatus() != null
                                && statusException.getStatus().getCode() == io.grpc.Status.Code.CANCELLED) {
                            endReason.set("grpc_cancelled");
                            emit(AgentEventType.ABORT, "cancelled");
                            saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta,
                                    metadataList, SessionMessageStatus.ABORTED);
                            finish();
                            return;
                        }
                        endReason.set("error");
                        logGrpcError(throwable);
                        emit(AgentEventType.ERROR, "AI 调用失败");
                        saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta,
                                metadataList, SessionMessageStatus.ERROR);
                        finish();
                    }

                    @Override
                    public void onCompleted() {
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        if (chatExecutionLifecycle.isAbortRequested(input.executionHandle())) {
                            endReason.set("abort");
                            emit(AgentEventType.ABORT, "aborted");
                            saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta,
                                    metadataList, SessionMessageStatus.ABORTED);
                            finish();
                            return;
                        }
                        try {
                            endReason.set("completed");
                            log.info("AI gRPC stream completed: userId={}, sessionId={}",
                                    input.userId(), input.session().getId());
                            saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta,
                                    metadataList, SessionMessageStatus.FINAL);
                            try {
                                aiUsageBillingService.recordConversationUsage(
                                        input.userId(), input.session().getId(), input.userMessageId(),
                                        responseMeta, toolEventList);
                            } catch (Exception usageException) {
                                log.warn("Record chat usage failed: userId={}, sessionId={}, err={}",
                                        input.userId(), input.session().getId(), usageException.getMessage());
                            }
                            emit(AgentEventType.DONE, "end");
                        } catch (Exception exception) {
                            log.warn("Finalize chat failed: {}", exception.getMessage());
                        } finally {
                            finish();
                        }
                    }
                });
                chatExecutionLifecycle.registerCall(input.executionHandle(), callRef[0]);
            } catch (Exception exception) {
                log.error("AI gRPC call failed before start: userId={}, sessionId={}, err={}",
                        input.userId(), input.session().getId(), exception.getMessage(), exception);
                emit(AgentEventType.ERROR, "AI 调用失败");
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                endReason.set("start_failed");
                saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta,
                        metadataList, SessionMessageStatus.ERROR);
                finish();
            }
        }

        private boolean abortIfRequested() {
            if (!chatExecutionLifecycle.isAbortRequested(input.executionHandle())) {
                return false;
            }
            if (!completed.compareAndSet(false, true)) {
                return true;
            }
            endReason.set("abort");
            emit(AgentEventType.ABORT, "aborted");
            cancelCall("abort requested");
            saveAssistantMessage(input.userId(), input.session(), assistantOutput, responseMeta,
                    metadataList, SessionMessageStatus.ABORTED);
            finish();
            return true;
        }

        private void captureResponse(Map<String, String> values, List<ChatCiFileRef> ciFileRefs) {
            responseMeta.put("input_tokens", Objects.toString(values.get("input_tokens"), ""));
            responseMeta.put("output_tokens", Objects.toString(values.get("output_tokens"), ""));
            responseMeta.put("total_tokens", Objects.toString(values.get("total_tokens"), ""));
            responseMeta.put("model", Objects.toString(values.get("model"), ""));
            if (input.effectiveModel() != null && !input.effectiveModel().isBlank()) {
                responseMeta.put("requested_model", input.effectiveModel());
            }
            String responseTier = values.get("service_tier");
            responseMeta.put("service_tier", responseTier == null || responseTier.isBlank()
                    ? input.requestedServiceTier() : responseTier);
            String responseId = values.get("openai_response_id");
            if (responseId != null && !responseId.isBlank()) {
                responseMeta.put("openai_response_id", responseId);
            }

            List<Map<String, Object>> ciFiles = new ArrayList<>();
            if (ciFileRefs != null) {
                for (ChatCiFileRef ref : ciFileRefs) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("provider", "openai");
                    item.put("providerResponseId", Objects.toString(responseId, ""));
                    item.put("providerFileId", ref.fileId());
                    item.put("containerId", ref.containerId());
                    item.put("fileName", ref.fileName());
                    item.put("status", "remote");
                    ciFiles.add(item);
                }
            }
            if (!ciFiles.isEmpty()) {
                Map<String, Object> ciMeta = new LinkedHashMap<>();
                ciMeta.put("type", "ci_files");
                ciMeta.put("provider", "openai");
                ciMeta.put("responseId", Objects.toString(responseId, ""));
                ciMeta.put("files", ciFiles);
                metadataList.add(ciMeta);
                emit(AgentEventType.METADATA, ciMeta);
            }
        }

        private void emit(AgentEventType type, Object payload) {
            if (!sseConnected.get()) {
                return;
            }
            if (!bridge.tryEmit(AgentEvent.of(type, seq.incrementAndGet(), payload))) {
                onSseDisconnected();
            }
        }

        private void cancelCall(String reason) {
            try {
                ChatGrpcCall call = callRef[0];
                if (call != null) {
                    call.cancel(reason);
                }
            } catch (Exception ignored) {
            }
        }

        private void logGrpcError(Throwable throwable) {
            if (throwable instanceof StatusRuntimeException statusException
                    && statusException.getStatus() != null) {
                log.error("AI gRPC stream error: userId={}, sessionId={}, status={}, desc={}",
                        input.userId(), input.session().getId(), statusException.getStatus().getCode(),
                        statusException.getStatus().getDescription(), statusException);
            } else {
                log.error("AI gRPC stream error: userId={}, sessionId={}, err={}",
                        input.userId(), input.session().getId(),
                        throwable == null ? "null" : throwable.getClass().getName(), throwable);
            }
        }

        private void finish() {
            if (!finalized.compareAndSet(false, true)) {
                return;
            }
            try {
                chatExecutionLifecycle.finishResources(
                        input.executionHandle(), renewStop,
                        endReason.get());
            } finally {
                try {
                    if (bridge.isConnected()) {
                        bridge.requestStopGracefully();
                        bridge.awaitStop(400, TimeUnit.MILLISECONDS);
                    } else {
                        bridge.stopNow();
                    }
                } catch (Exception ignored) {
                }
                safeComplete(emitter);
                log.info("chat finished: userId={}, sessionId={}, endReason={}, sseConnected={}",
                        input.userId(), input.session().getId(), endReason.get(), bridge.isConnected());
            }
        }
    }

    void saveAssistantMessage(Long userId,
                              Session session,
                              AssistantOutputAccumulator assistantOutput,
                              Map<String, String> responseMeta,
                              List<Map<String, Object>> metadataList,
                              SessionMessageStatus status) {
        String finalRaw = assistantOutput == null ? "" : assistantOutput.fullText();
        List<AssistantOutputEntry> orderedEntries = assistantOutput == null
                ? List.of() : assistantOutput.orderedEntries();

        Integer tokenCount = null;
        JSONB metaJsonb = null;
        try {
            if (responseMeta != null && !responseMeta.isEmpty()) {
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("input_tokens", tryParseInt(responseMeta.get("input_tokens")));
                meta.put("output_tokens", tryParseInt(responseMeta.get("output_tokens")));
                Integer total = tryParseInt(responseMeta.get("total_tokens"));
                meta.put("total_tokens", total);
                meta.put("model", blankToNull(responseMeta.get("model")));
                for (Map.Entry<String, String> entry : responseMeta.entrySet()) {
                    String key = entry.getKey();
                    if (key != null && !key.isBlank() && !meta.containsKey(key)) {
                        meta.put(key, blankToNull(entry.getValue()));
                    }
                }
                metaJsonb = JSONB.valueOf(objectMapper.writeValueAsString(meta));
                tokenCount = total;
            }
        } catch (Exception ignored) {
        }

        List<SessionMessage> toInsert = new ArrayList<>();
        int lastTextIndex = lastTextEntryIndex(orderedEntries);
        for (int index = 0; index < orderedEntries.size(); index++) {
            AssistantOutputEntry entry = orderedEntries.get(index);
            if (entry == null || entry.kind() == null) {
                continue;
            }
            if (entry.kind() == AssistantOutputEntryKind.TEXT) {
                boolean attachMeta = index == lastTextIndex;
                toInsert.add(newAssistantTextMessage(
                        session,
                        Objects.toString(entry.text(), ""),
                        attachMeta ? metaJsonb : null,
                        attachMeta ? tokenCount : null,
                        status));
            } else {
                SessionMessage toolMessage = newToolEventMessage(session, entry.toolEvent(), status);
                if (toolMessage != null) {
                    toInsert.add(toolMessage);
                }
            }
        }
        if (lastTextIndex < 0) {
            toInsert.add(newAssistantTextMessage(session, finalRaw, metaJsonb, tokenCount, status));
        }
        if (metadataList != null && !metadataList.isEmpty()) {
            String metadataJson = writeJson(metadataList);
            SessionMessage metadataMessage = new SessionMessage();
            metadataMessage.setSessionId(session.getId());
            metadataMessage.setRole(SessionMessageRole.ASSISTANT);
            metadataMessage.setType(SessionMessageType.METADATA);
            metadataMessage.setContent(metadataJson);
            metadataMessage.setRawContent(metadataJson);
            metadataMessage.setStatus(status);
            metadataMessage.setDeleted(false);
            toInsert.add(metadataMessage);
        }

        List<SessionMessage> savedMessages = sessionMessageRepository.insertBatchAutoSeq(session.getId(), toInsert);
        bindLatestAgentRunMessageSpan(userId, session, savedMessages);
        try {
            Session current = sessionService.requireSession(userId, session.getId());
            current.setLastActiveAt(OffsetDateTime.now());
            sessionService.updateSession(userId, current.getId(), null);
        } catch (Exception ignored) {
        }
    }

    private void bindLatestAgentRunMessageSpan(Long userId, Session session, List<SessionMessage> savedMessages) {
        if (userId == null || userId <= 0 || session == null || session.getId() == null
                || savedMessages == null || savedMessages.isEmpty()) {
            return;
        }
        List<SessionMessage> sequenced = savedMessages.stream()
                .filter(Objects::nonNull)
                .filter(message -> message.getSeq() != null && message.getSeq() > 0)
                .sorted(Comparator.comparing(SessionMessage::getSeq))
                .toList();
        if (sequenced.isEmpty()) {
            return;
        }
        SessionMessage first = sequenced.getFirst();
        SessionMessage last = sequenced.getLast();
        try {
            agentRunPersistenceRepository.bindLatestSessionAgentRunMessageSpan(
                    userId, session.getId(), first.getId(), last.getId(), first.getSeq(), last.getSeq());
        } catch (Exception exception) {
            log.warn("Bind agent run message span failed: userId={}, sessionId={}, firstSeq={}, lastSeq={}, error={}",
                    userId, session.getId(), first.getSeq(), last.getSeq(), exception.getMessage());
        }
    }

    private SessionMessage newToolEventMessage(Session session,
                                               Map<String, Object> event,
                                               SessionMessageStatus status) {
        if (event == null || event.isEmpty()) {
            return null;
        }
        String phase = Objects.toString(event.get("phase"), "").trim();
        String callId = Objects.toString(event.get("call_id"), "").trim();
        String name = Objects.toString(event.get("name"), "").trim();
        SessionMessage message = new SessionMessage();
        message.setSessionId(session.getId());
        message.setType(SessionMessageType.TOOL_OUTPUT);
        message.setToolCallId(callId.isBlank() ? null : callId);
        message.setName(name.isBlank() ? null : name);
        message.setStatus(status);
        message.setDeleted(false);
        message.setContent(writeJson(event));
        if ("result".equalsIgnoreCase(phase)) {
            message.setRole(SessionMessageRole.TOOL);
            message.setRawContent(Objects.toString(event.get("result_json"), null));
        } else {
            message.setRole(SessionMessageRole.ASSISTANT);
            message.setRawContent(Objects.toString(event.get("arguments_json"), null));
        }
        return message;
    }

    private String summarizeToolArgumentsJson(String name, String argumentsJson) {
        if (!"publish_artifact".equals(name) || argumentsJson == null || argumentsJson.isBlank()) {
            return argumentsJson;
        }
        try {
            var node = objectMapper.readTree(argumentsJson);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("artifact_type", blankToNull(node.path("artifact_type").asText("")));
            summary.put("title", blankToNull(node.path("title").asText("")));
            summary.put("mime_type", blankToNull(node.path("mime_type").asText("")));
            summary.put("display_hint", blankToNull(node.path("display_hint").asText("")));
            summary.put("replace_key", blankToNull(node.path("replace_key").asText("")));
            if (node.has("final")) {
                summary.put("final", node.path("final").asBoolean());
            }
            if (node.has("content")) {
                summary.put("content_length", node.path("content").asText("").length());
            }
            return writeJson(summary);
        } catch (Exception exception) {
            log.warn("summarizeToolArgumentsJson failed: name={}, err={}", name, exception.getMessage());
            return argumentsJson;
        }
    }

    private String summarizeToolResultJson(String name, String resultJson) {
        if (!"publish_artifact".equals(name) || resultJson == null || resultJson.isBlank()) {
            return resultJson;
        }
        try {
            var node = objectMapper.readTree(resultJson);
            Map<String, Object> summary = new LinkedHashMap<>();
            if (node.has("published")) {
                summary.put("published", node.path("published").asBoolean());
            }
            summary.put("artifact_type", blankToNull(node.path("artifact_type").asText("")));
            summary.put("title", blankToNull(node.path("title").asText("")));
            summary.put("replace_key", blankToNull(node.path("replace_key").asText("")));
            if (node.has("content_length")) {
                summary.put("content_length", node.path("content_length").asInt());
            } else if (node.path("artifact").has("content")) {
                summary.put("content_length", node.path("artifact").path("content").asText("").length());
            }
            return writeJson(summary);
        } catch (Exception exception) {
            log.warn("summarizeToolResultJson failed: name={}, err={}", name, exception.getMessage());
            return resultJson;
        }
    }

    private Map<String, Object> tryExtractUiArtifactMetadata(String name, String resultJson) {
        if (!"publish_artifact".equals(name) || resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            var root = objectMapper.readTree(resultJson);
            if (!root.path("published").asBoolean(false)) {
                return null;
            }
            var artifact = root.path("artifact");
            if (!artifact.isObject()) {
                return null;
            }
            String type = artifact.path("type").asText("");
            String artifactType = artifact.path("artifact_type").asText("");
            String title = artifact.path("title").asText("");
            String content = artifact.path("content").asText("");
            if (type.isBlank() || artifactType.isBlank() || title.isBlank() || content.isBlank()) {
                return null;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("type", type);
            metadata.put("source", blankToNull(artifact.path("source").asText("")));
            metadata.put("artifact_type", artifactType);
            metadata.put("title", title);
            metadata.put("content", content);
            metadata.put("mime_type", blankToNull(artifact.path("mime_type").asText("")));
            metadata.put("display_hint", blankToNull(artifact.path("display_hint").asText("")));
            metadata.put("replace_key", blankToNull(artifact.path("replace_key").asText("")));
            if (artifact.has("final")) {
                metadata.put("final", artifact.path("final").asBoolean());
            }
            metadata.put("content_length", artifact.has("content_length")
                    ? artifact.path("content_length").asInt() : content.length());
            return metadata;
        } catch (Exception exception) {
            log.warn("tryExtractUiArtifactMetadata failed: name={}, err={}", name, exception.getMessage());
            return null;
        }
    }

    private static int lastTextEntryIndex(List<AssistantOutputEntry> entries) {
        for (int index = entries == null ? -1 : entries.size() - 1; index >= 0; index--) {
            AssistantOutputEntry entry = entries.get(index);
            if (entry != null && entry.kind() == AssistantOutputEntryKind.TEXT) {
                return index;
            }
        }
        return -1;
    }

    private static SessionMessage newAssistantTextMessage(Session session,
                                                          String text,
                                                          JSONB metadata,
                                                          Integer tokenCount,
                                                          SessionMessageStatus status) {
        String raw = text == null ? "" : text;
        SessionMessage message = new SessionMessage();
        message.setSessionId(session.getId());
        message.setRole(SessionMessageRole.ASSISTANT);
        message.setType(SessionMessageType.TEXT);
        message.setContent(raw);
        message.setMetadata(metadata);
        message.setRawContent(raw);
        message.setStatus(status);
        message.setTokenCount(tokenCount);
        message.setDeleted(false);
        return message;
    }

    private static List<Map<String, String>> getMaps(AiChatMetadata metadata) {
        List<Map<String, String>> links = new ArrayList<>();
        if (metadata != null && metadata.getLinks() != null) {
            for (var source : metadata.getLinks()) {
                Map<String, String> link = new LinkedHashMap<>();
                link.put("title", source.getTitle());
                link.put("url", source.getUrl());
                link.put("description", source.getDescription());
                link.put("resources", source.getResources());
                link.put("publicationInfo", source.getPublicationInfo());
                links.add(link);
            }
        }
        return links;
    }

    private static Integer tryParseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private static void safeComplete(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
