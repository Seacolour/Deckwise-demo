package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageClientContext;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the trusted canonical context passed from Backend chat sessions to the Agent runtime.
 *
 * <p>This service owns transient client context validation, persisted projections, provider
 * compaction reuse, local history compaction, and Canonical Conversation v1 rendering. It does
 * not persist chat messages or interact with SSE, Redis, billing, or the Agent transport.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatContextAssembler {

    private static final int MAX_CLIENT_CONTEXT_STRING_LENGTH = 256;
    private static final int MAX_REFERENCED_DOCUMENTS = 5;

    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;
    private final DocumentService documentService;
    private final AgentRunPersistenceRepository agentRunPersistenceRepository;
    private final ChatAttachmentService chatAttachmentService;

    @Value("${chat.context-compaction.enabled:true}")
    private boolean contextCompactionEnabled = true;

    @Value("${chat.context-compaction.max-text-messages:12}")
    private int contextCompactionMaxTextMessages = 12;

    @Value("${chat.context-compaction.max-tool-pairs:4}")
    private int contextCompactionMaxToolPairs = 4;

    @Value("${chat.context-compaction.max-history-chars:15000}")
    private int contextCompactionMaxHistoryChars = 15_000;

    @Value("${chat.context-compaction.agent-ledger-projection-enabled:true}")
    private boolean agentLedgerProjectionEnabled = true;

    @Value("${chat.context-compaction.max-agent-ledger-projection-chars:6000}")
    private int maxAgentLedgerProjectionChars = 6_000;

    @Value("${chat.context-compaction.artifact-projection-enabled:true}")
    private boolean artifactProjectionEnabled = true;

    @Value("${chat.context-compaction.max-artifact-projection-chars:6000}")
    private int maxArtifactProjectionChars = 6_000;

    @Value("${chat.context-compaction.agent-ledger-projection-max-age-hours:72}")
    private int agentLedgerProjectionMaxAgeHours = 72;

    @Value("${chat.provider-context-compaction.enabled:false}")
    private boolean providerContextCompactionEnabled = false;

    @Value("${chat.provider-context-compaction.provider:openai}")
    private String providerContextCompactionProvider = "openai";

    @Value("${chat.provider-context-compaction.max-delta-messages:80}")
    private int providerContextCompactionMaxDeltaMessages = 80;

    public record AgentContextBuildResult(String fullContextJson,
                                          boolean providerWindowUsed,
                                          Long compactionId,
                                          int candidateHistoryCount,
                                          int selectedHistoryCount,
                                          int selectedHistoryChars,
                                          int sourceLastSeq) {
    }

    private record ProviderContextWindowItem(Map<String, Object> item,
                                             Long compactionId,
                                             int sourceLastSeq) {
    }

    private enum ContextMessageGroupKind {
        USER_TEXT,
        ASSISTANT_TEXT,
        TOOL_PAIR
    }

    private record ContextMessageGroup(ContextMessageGroupKind kind,
                                       int firstIndex,
                                       List<SessionMessage> messages) {
        private int estimatedChars() {
            int total = 0;
            for (SessionMessage message : messages) {
                total += estimateMessageChars(message);
            }
            return total;
        }
    }

    private static final class ToolPairCandidate {
        private int callIndex = -1;
        private SessionMessage callMessage;
        private int resultIndex = -1;
        private SessionMessage resultMessage;

        private boolean complete() {
            return callMessage != null && resultMessage != null;
        }

        private int firstIndex() {
            if (callIndex < 0) {
                return resultIndex;
            }
            if (resultIndex < 0) {
                return callIndex;
            }
            return Math.min(callIndex, resultIndex);
        }

        private List<SessionMessage> messagesInOriginalOrder() {
            if (!complete()) {
                return List.of();
            }
            return callIndex <= resultIndex
                    ? List.of(callMessage, resultMessage)
                    : List.of(resultMessage, callMessage);
        }
    }

    public List<Map<String, Object>> buildClientContextItems(Long userId,
                                                              Session session,
                                                              UserMessageSendRequest req) {
        UserMessageClientContext clientContext = req == null ? null : req.getClientContext();
        if (clientContext == null) {
            return List.of();
        }

        try {
            Map<String, Object> payload = sanitizeClientContext(userId, session, clientContext);
            if (payload.isEmpty()) {
                return List.of();
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", "system");
            item.put("type", "text");
            item.put("content", buildClientContextPrompt(payload));
            return List.of(item);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Client context ignored: userId={}, sessionId={}, err={}",
                    userId, session == null ? null : session.getId(), e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> buildPersistedAgentProjectionItems(Long userId, Session session) {
        if (userId == null || userId <= 0 || session == null || session.getId() == null) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int taskProjectionChars = 0;
        int artifactProjectionChars = 0;
        try {
            OffsetDateTime minUpdatedAt = agentLedgerProjectionMaxAgeHours <= 0
                    ? null
                    : OffsetDateTime.now().minusHours(agentLedgerProjectionMaxAgeHours);
            if (agentLedgerProjectionEnabled && maxAgentLedgerProjectionChars > 0) {
                Optional<String> taskProjection = agentRunPersistenceRepository
                        .findLatestTaskProjectionJson(
                                userId,
                                session.getId(),
                                session.getProjectId(),
                                minUpdatedAt,
                                Math.max(maxAgentLedgerProjectionChars, 0));
                if (taskProjection.isPresent()) {
                    taskProjectionChars = taskProjection.orElseThrow().length();
                    items.add(buildPersistedAgentTaskProjectionItem(taskProjection.orElseThrow()));
                }
            }
            if (artifactProjectionEnabled && maxArtifactProjectionChars > 0) {
                Optional<String> artifactProjection = agentRunPersistenceRepository
                        .findLatestArtifactProjectionJson(
                                userId,
                                session.getId(),
                                session.getProjectId(),
                                minUpdatedAt,
                                Math.max(maxArtifactProjectionChars, 0));
                if (artifactProjection.isPresent()) {
                    artifactProjectionChars = artifactProjection.orElseThrow().length();
                    items.add(buildPersistedArtifactProjectionItem(artifactProjection.orElseThrow()));
                }
            }
        } catch (Exception e) {
            log.warn("Agent persisted projections ignored: userId={}, sessionId={}, err={}",
                    userId, session.getId(), e.getMessage());
        }
        if (!items.isEmpty()) {
            log.info("Agent persisted projections injected: userId={}, sessionId={}, projectionItems={}, taskProjectionChars={}, artifactProjectionChars={}, renderedContextChars={}, maxTaskChars={}, maxArtifactChars={}, maxAgeHours={}",
                    userId,
                    session.getId(),
                    items.size(),
                    taskProjectionChars,
                    artifactProjectionChars,
                    estimateExtraItemChars(items),
                    Math.max(maxAgentLedgerProjectionChars, 0),
                    Math.max(maxArtifactProjectionChars, 0),
                    Math.max(agentLedgerProjectionMaxAgeHours, 0));
        }
        return items;
    }

    public AgentContextBuildResult buildAgentContextJson(Long userId,
                                                          Session session,
                                                          List<SessionMessage> history,
                                                          List<Map<String, Object>> extraContextItems) {
        int candidates = history == null ? 0 : history.size();
        Optional<ProviderContextWindowItem> providerWindow = buildProviderContextWindowItem(userId, session);
        AgentContextBuildResult result;
        if (providerWindow.isPresent()) {
            ProviderContextWindowItem window = providerWindow.get();
            List<Map<String, Object>> extraItems = new ArrayList<>();
            if (extraContextItems != null && !extraContextItems.isEmpty()) {
                extraItems.addAll(extraContextItems);
            }
            extraItems.add(window.item());
            List<SessionMessage> deltaMessages = selectProviderContextDeltaMessages(history, window.sourceLastSeq());
            result = new AgentContextBuildResult(
                    buildCanonicalV1(deltaMessages, extraItems),
                    true,
                    window.compactionId(),
                    candidates,
                    deltaMessages.size(),
                    estimateMessagesChars(deltaMessages),
                    window.sourceLastSeq());
        } else {
            List<SessionMessage> selectedHistory = selectContextMessages(history);
            result = new AgentContextBuildResult(
                    buildCanonicalV1(selectedHistory, extraContextItems),
                    false,
                    null,
                    candidates,
                    selectedHistory.size(),
                    estimateMessagesChars(selectedHistory),
                    0);
        }
        logContextSelection(userId, session, result);
        return result;
    }

    private void logContextSelection(Long userId, Session session, AgentContextBuildResult result) {
        if (result.providerWindowUsed()) {
            log.info("Chat provider context compaction reused: userId={}, sessionId={}, compactionId={}, sourceLastSeq={}, candidates={}, deltaSelected={}, deltaChars={}, maxDeltaMessages={}",
                    userId,
                    session == null ? null : session.getId(),
                    result.compactionId(),
                    result.sourceLastSeq(),
                    result.candidateHistoryCount(),
                    result.selectedHistoryCount(),
                    result.selectedHistoryChars(),
                    Math.max(providerContextCompactionMaxDeltaMessages, 0));
        } else if (contextCompactionEnabled) {
            log.info("Chat context compacted: userId={}, sessionId={}, candidates={}, selected={}, selectedChars={}, maxTextMessages={}, maxToolPairs={}, maxHistoryChars={}",
                    userId,
                    session == null ? null : session.getId(),
                    result.candidateHistoryCount(),
                    result.selectedHistoryCount(),
                    result.selectedHistoryChars(),
                    Math.max(contextCompactionMaxTextMessages, 0),
                    Math.max(contextCompactionMaxToolPairs, 0),
                    Math.max(contextCompactionMaxHistoryChars, 0));
        }
    }

    public List<SessionMessage> selectContextMessages(List<SessionMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        if (!contextCompactionEnabled) {
            return history;
        }

        List<ContextMessageGroup> textGroups = new ArrayList<>();
        Map<String, ToolPairCandidate> toolPairs = new LinkedHashMap<>();
        for (int i = 0; i < history.size(); i++) {
            SessionMessage message = history.get(i);
            if (!isFinalVisibleHistoryMessage(message)) {
                continue;
            }
            if (isContextTextMessage(message)) {
                ContextMessageGroupKind kind = message.getRole() == SessionMessageRole.USER
                        ? ContextMessageGroupKind.USER_TEXT
                        : ContextMessageGroupKind.ASSISTANT_TEXT;
                textGroups.add(new ContextMessageGroup(kind, i, List.of(message)));
                continue;
            }
            if (!isContextToolMessage(message)) {
                continue;
            }
            String callId = Objects.toString(message.getToolCallId(), "").trim();
            ToolPairCandidate pair = toolPairs.computeIfAbsent(callId, ignored -> new ToolPairCandidate());
            if (message.getRole() == SessionMessageRole.ASSISTANT) {
                pair.callIndex = i;
                pair.callMessage = message;
            } else if (message.getRole() == SessionMessageRole.TOOL) {
                pair.resultIndex = i;
                pair.resultMessage = message;
            }
        }

        List<ContextMessageGroup> toolGroups = new ArrayList<>();
        for (ToolPairCandidate pair : toolPairs.values()) {
            if (pair.complete()) {
                toolGroups.add(new ContextMessageGroup(
                        ContextMessageGroupKind.TOOL_PAIR,
                        pair.firstIndex(),
                        pair.messagesInOriginalOrder()));
            }
        }
        toolGroups.sort(Comparator.comparingInt(ContextMessageGroup::firstIndex));

        List<ContextMessageGroup> selectedGroups = new ArrayList<>();
        selectedGroups.addAll(retainLastGroups(textGroups, Math.max(contextCompactionMaxTextMessages, 0)));
        selectedGroups.addAll(retainLastGroups(toolGroups, Math.max(contextCompactionMaxToolPairs, 0)));
        selectedGroups.sort(Comparator.comparingInt(ContextMessageGroup::firstIndex));

        applyContextHistoryBudget(selectedGroups, Math.max(contextCompactionMaxHistoryChars, 0));

        List<SessionMessage> selected = new ArrayList<>();
        for (ContextMessageGroup group : selectedGroups) {
            selected.addAll(group.messages());
        }
        return List.copyOf(selected);
    }

    public String buildCanonicalV1(List<SessionMessage> messages, List<Map<String, Object>> extraItems) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", "v1");
            List<Map<String, Object>> items = new ArrayList<>();

            if (extraItems != null && !extraItems.isEmpty()) {
                items.addAll(extraItems);
            }

            Set<String> pairedCallIds = new HashSet<>();
            for (SessionMessage message : messages) {
                if (message == null) {
                    continue;
                }
                if (SessionMessageType.TOOL_OUTPUT.equals(message.getType())
                        && SessionMessageRole.TOOL.equals(message.getRole())
                        && message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
                    pairedCallIds.add(message.getToolCallId());
                }
            }

            for (SessionMessage message : messages) {
                if (message == null) {
                    continue;
                }
                if (message.getStatus() != null && message.getStatus() != SessionMessageStatus.FINAL) {
                    continue;
                }
                if (chatAttachmentService.isAttachmentMetaMessage(message)) {
                    continue;
                }
                String role = message.getRole() != null ? message.getRole().toDatabaseValue() : null;
                String type = message.getType() != null ? message.getType().toDatabaseValue() : "text";
                String content = message.getContent();
                String raw = message.getRawContent();
                if (content == null || content.isBlank()) {
                    if (raw != null && !raw.isBlank()) {
                        content = raw;
                    }
                }
                if (role == null || role.isBlank()) {
                    continue;
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", role);
                item.put("type", type);
                if (content != null && !content.isBlank()) {
                    item.put("content", content);
                }
                if (message.getToolCallId() != null && !message.getToolCallId().isBlank()) {
                    item.put("tool_call_id", message.getToolCallId());
                }
                if (message.getName() != null && !message.getName().isBlank()) {
                    item.put("name", message.getName());
                }
                if (raw != null && !raw.isBlank() && !"text".equalsIgnoreCase(type)) {
                    item.put("raw_content", raw);
                }
                items.add(item);
            }
            root.put("items", items);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "构建对话上下文失败");
        }
    }

    private int estimateExtraItemChars(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map<String, Object> item : items) {
            if (item != null) {
                total += String.valueOf(item.getOrDefault("content", "")).length();
            }
        }
        return total;
    }

    private Map<String, Object> buildPersistedAgentTaskProjectionItem(String projectionJson) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "system");
        item.put("type", "text");
        item.put("content", """
                ## Persisted Agent Task Projection

                This is compact backend-persisted task state from the latest AgentRunLedger for this session/project.
                Use it to continue todos, decisions, and page/task progress without replaying old chat/tool history.
                Treat heuristic progress as a planning hint; verify with narrow artifact reads before writes.

                ```json
                %s
                ```
                """.formatted(projectionJson));
        return item;
    }

    private Map<String, Object> buildPersistedArtifactProjectionItem(String projectionJson) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", "system");
        item.put("type", "text");
        item.put("content", """
                ## Persisted Artifact Projection

                This is compact backend-persisted artifact state from the latest AgentRunLedger for this session/project.
                Use activeDocument, latestDocumentRevisions, activeViews, and readReuseHints to avoid rediscovering document identity or repeating old reads.
                Before writing, verify revision with a narrow read if the document may have changed.

                ```json
                %s
                ```
                """.formatted(projectionJson));
        return item;
    }

    private Map<String, Object> sanitizeClientContext(Long userId,
                                                       Session session,
                                                       UserMessageClientContext clientContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "client_context");
        payload.put("scope", "current_turn_only");
        payload.put("serverNow", OffsetDateTime.now().toString());

        Map<String, Object> activeDocument = sanitizeActiveDocument(userId, session, clientContext.getActiveDocument());
        if (!activeDocument.isEmpty()) {
            payload.put("activeDocument", activeDocument);
        }

        List<Map<String, Object>> referencedDocuments = sanitizeReferencedDocuments(
                userId, session, clientContext.getReferencedDocumentIds());
        if (!referencedDocuments.isEmpty()) {
            payload.put("referencedDocuments", referencedDocuments);
        }

        Map<String, Object> environment = sanitizeEnvironmentContext(clientContext.getEnvironment());
        if (!environment.isEmpty()) {
            payload.put("environment", environment);
        }

        if (payload.size() <= 3) {
            return Map.of();
        }
        return payload;
    }

    private Map<String, Object> sanitizeActiveDocument(Long userId,
                                                       Session session,
                                                       UserMessageClientContext.ActiveDocument input) {
        if (input == null) {
            return Map.of();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        Long documentId = parsePositiveLong(input.getDocumentId());
        if (documentId != null) {
            DocumentDTO doc = null;
            try {
                doc = documentService.get(userId, documentId, false);
            } catch (Exception e) {
                log.warn("Client context active document ignored: userId={}, documentId={}, err={}",
                        userId, documentId, e.getMessage());
            }

            Long sessionProjectId = session == null ? null : session.getProjectId();
            if (doc != null && doc.getId() != null
                    && (sessionProjectId == null || sessionProjectId <= 0
                    || doc.getProjectId() == null || doc.getProjectId().equals(sessionProjectId))) {
                out.put("documentId", doc.getId());
                if (doc.getProjectId() != null) {
                    out.put("projectId", doc.getProjectId());
                }
                if (doc.getDocumentType() != null) {
                    out.put("documentType", doc.getDocumentType().toDatabaseValue());
                }
                putString(out, "title", doc.getTitle(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
            }
        }

        putString(out, "type", input.getType(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
        putString(out, "fileName", input.getFileName(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
        putString(out, "cacheKey", input.getCacheKey(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
        return out;
    }

    private List<Map<String, Object>> sanitizeReferencedDocuments(Long userId,
                                                                 Session session,
                                                                 List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_REFERENCED_DOCUMENTS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每条消息最多引用 5 篇文档");
        }
        Long projectId = session == null ? null : session.getProjectId();
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String rawId : ids) {
            Long id = parsePositiveLong(rawId);
            if (id == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "引用文档 ID 无效");
            }
            if (!seen.add(id)) {
                continue;
            }
            DocumentDTO document = documentService.get(userId, id, false);
            if (document == null || document.getId() == null || projectId == null
                    || !projectId.equals(document.getProjectId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "引用文档不属于当前工作区");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("documentId", document.getId());
            entry.put("projectId", projectId);
            if (document.getDocumentType() != null) {
                entry.put("documentType", document.getDocumentType().toDatabaseValue());
            }
            putString(entry, "title", document.getTitle(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
            result.add(entry);
        }
        return result;
    }

    private Map<String, Object> sanitizeEnvironmentContext(UserMessageClientContext.EnvironmentContext input) {
        if (input == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        putString(out, "clientNow", input.getClientNow(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
        putString(out, "timeZone", input.getTimeZone(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
        putString(out, "locale", input.getLocale(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
        putString(out, "region", input.getRegion(), MAX_CLIENT_CONTEXT_STRING_LENGTH);
        return out;
    }

    private String buildClientContextPrompt(Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        return """
                Transient client context for this turn only. This is UI/environment context, not a persisted user message.
                Use activeDocument to resolve "current document" and referencedDocuments as the explicit files selected for this turn.
                Treat frontend-supplied values as hints; before editing, read the latest document/revision through tools.

                ```json
                %s
                ```
                """.formatted(json);
    }

    private Optional<ProviderContextWindowItem> buildProviderContextWindowItem(Long userId, Session session) {
        if (!providerContextCompactionEnabled || userId == null || userId <= 0 || session == null
                || session.getId() == null || session.getId() <= 0) {
            return Optional.empty();
        }
        String provider = normalizeProviderContextCompactionProvider(providerContextCompactionProvider);
        try {
            return agentRunPersistenceRepository
                    .findActiveContextCompaction(userId, session.getId(), provider)
                    .flatMap(this::toProviderContextWindowItem);
        } catch (Exception e) {
            log.warn("Provider context compaction lookup failed, fallback to local history: userId={}, sessionId={}, provider={}, error={}",
                    userId, session.getId(), provider, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ProviderContextWindowItem> toProviderContextWindowItem(
            AgentRunPersistenceRepository.ActiveContextCompaction compaction) {
        if (compaction == null) {
            return Optional.empty();
        }
        int sourceLastSeq = Optional.ofNullable(compaction.sourceLastSeq()).orElse(0);
        if (sourceLastSeq <= 0) {
            log.debug("Provider context compaction ignored without source_last_seq: compactionId={}, responseId={}",
                    compaction.id(), compaction.responseId());
            return Optional.empty();
        }
        String raw = Objects.toString(compaction.compactedInputJson(), "").trim();
        if (raw.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            String provider = firstNonBlank(
                    jsonText(root.path("provider")),
                    normalizeProviderContextCompactionProvider(compaction.provider()));
            String format = firstNonBlank(
                    jsonText(root.path("format")),
                    Objects.toString(compaction.contextFormat(), "").trim());
            if (!"openai".equalsIgnoreCase(provider)
                    || !"responses_compacted_window_v1".equalsIgnoreCase(format)) {
                log.warn("Provider context compaction ignored due to unsupported shape: compactionId={}, provider={}, format={}",
                        compaction.id(), provider, format);
                return Optional.empty();
            }
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                log.warn("Provider context compaction ignored because items is empty: compactionId={}",
                        compaction.id());
                return Optional.empty();
            }
            List<Object> compactedItems = objectMapper.convertValue(
                    items,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "provider_context_window");
            item.put("provider", "openai");
            item.put("format", "responses_compacted_window_v1");
            item.put("items", compactedItems);
            return Optional.of(new ProviderContextWindowItem(item, compaction.id(), sourceLastSeq));
        } catch (Exception e) {
            log.warn("Provider context compaction parse failed, fallback to local history: compactionId={}, error={}",
                    compaction.id(), e.getMessage());
            return Optional.empty();
        }
    }

    private List<SessionMessage> selectProviderContextDeltaMessages(List<SessionMessage> history, int sourceLastSeq) {
        if (history == null || history.isEmpty() || sourceLastSeq <= 0) {
            return List.of();
        }
        List<SessionMessage> visibleDelta = new ArrayList<>();
        for (SessionMessage message : history) {
            if (message == null || message.getSeq() == null || message.getSeq() <= sourceLastSeq) {
                continue;
            }
            if (isFinalVisibleHistoryMessage(message)) {
                visibleDelta.add(message);
            }
        }
        if (visibleDelta.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(providerContextCompactionMaxDeltaMessages, 0);
        if (limit <= 0) {
            return List.of();
        }
        if (visibleDelta.size() > limit) {
            visibleDelta = new ArrayList<>(visibleDelta.subList(visibleDelta.size() - limit, visibleDelta.size()));
        }
        return dropIncompleteToolPairs(visibleDelta);
    }

    private static List<SessionMessage> dropIncompleteToolPairs(List<SessionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Map<String, ToolPairCandidate> toolPairs = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            SessionMessage message = messages.get(i);
            if (!isContextToolMessage(message)) {
                continue;
            }
            String callId = Objects.toString(message.getToolCallId(), "").trim();
            ToolPairCandidate pair = toolPairs.computeIfAbsent(callId, ignored -> new ToolPairCandidate());
            if (message.getRole() == SessionMessageRole.ASSISTANT) {
                pair.callIndex = i;
                pair.callMessage = message;
            } else if (message.getRole() == SessionMessageRole.TOOL) {
                pair.resultIndex = i;
                pair.resultMessage = message;
            }
        }
        Set<String> completeCallIds = new HashSet<>();
        for (Map.Entry<String, ToolPairCandidate> entry : toolPairs.entrySet()) {
            if (entry.getValue().complete()) {
                completeCallIds.add(entry.getKey());
            }
        }

        List<SessionMessage> selected = new ArrayList<>();
        for (SessionMessage message : messages) {
            if (isContextToolMessage(message)
                    && !completeCallIds.contains(Objects.toString(message.getToolCallId(), "").trim())) {
                continue;
            }
            selected.add(message);
        }
        return List.copyOf(selected);
    }

    private static List<ContextMessageGroup> retainLastGroups(List<ContextMessageGroup> groups, int limit) {
        if (groups == null || groups.isEmpty() || limit <= 0) {
            return List.of();
        }
        int start = Math.max(0, groups.size() - limit);
        return new ArrayList<>(groups.subList(start, groups.size()));
    }

    private static void applyContextHistoryBudget(List<ContextMessageGroup> selectedGroups, int maxChars) {
        if (selectedGroups == null || selectedGroups.isEmpty() || maxChars <= 0) {
            return;
        }
        while (estimateGroupChars(selectedGroups) > maxChars && !selectedGroups.isEmpty()) {
            int removeIndex = firstGroupIndexOf(selectedGroups, ContextMessageGroupKind.TOOL_PAIR);
            if (removeIndex < 0) {
                removeIndex = firstGroupIndexOf(selectedGroups, ContextMessageGroupKind.ASSISTANT_TEXT);
            }
            if (removeIndex < 0) {
                removeIndex = firstGroupIndexOf(selectedGroups, ContextMessageGroupKind.USER_TEXT);
            }
            if (removeIndex < 0) {
                return;
            }
            selectedGroups.remove(removeIndex);
        }
    }

    private static int firstGroupIndexOf(List<ContextMessageGroup> groups, ContextMessageGroupKind kind) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).kind() == kind) {
                return i;
            }
        }
        return -1;
    }

    private static int estimateGroupChars(List<ContextMessageGroup> groups) {
        int total = 0;
        if (groups == null) {
            return total;
        }
        for (ContextMessageGroup group : groups) {
            total += group == null ? 0 : group.estimatedChars();
        }
        return total;
    }

    private static int estimateMessagesChars(List<SessionMessage> messages) {
        int total = 0;
        if (messages == null) {
            return total;
        }
        for (SessionMessage message : messages) {
            total += estimateMessageChars(message);
        }
        return total;
    }

    private static int estimateMessageChars(SessionMessage message) {
        if (message == null) {
            return 0;
        }
        int total = 0;
        total += Objects.toString(message.getContent(), "").length();
        total += Objects.toString(message.getRawContent(), "").length();
        total += Objects.toString(message.getName(), "").length();
        total += Objects.toString(message.getToolCallId(), "").length();
        if (message.getMetadata() != null) {
            total += Objects.toString(message.getMetadata().data(), "").length();
        }
        return total;
    }

    private boolean isFinalVisibleHistoryMessage(SessionMessage message) {
        if (message == null) {
            return false;
        }
        if (message.getStatus() != null && message.getStatus() != SessionMessageStatus.FINAL) {
            return false;
        }
        return !chatAttachmentService.isAttachmentMetaMessage(message);
    }

    private static boolean isContextTextMessage(SessionMessage message) {
        if (message == null || message.getType() != SessionMessageType.TEXT) {
            return false;
        }
        if (message.getRole() != SessionMessageRole.USER && message.getRole() != SessionMessageRole.ASSISTANT) {
            return false;
        }
        return hasVisibleContent(message);
    }

    private static boolean isContextToolMessage(SessionMessage message) {
        if (message == null || message.getType() != SessionMessageType.TOOL_OUTPUT) {
            return false;
        }
        if (message.getRole() != SessionMessageRole.ASSISTANT && message.getRole() != SessionMessageRole.TOOL) {
            return false;
        }
        return !Objects.toString(message.getToolCallId(), "").trim().isBlank();
    }

    private static boolean hasVisibleContent(SessionMessage message) {
        return !Objects.toString(message.getContent(), "").isBlank()
                || !Objects.toString(message.getRawContent(), "").isBlank();
    }

    private static Long parsePositiveLong(String value) {
        String stringValue = sanitizeString(value, 64);
        if (stringValue == null || stringValue.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(stringValue);
            return parsed > 0 ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void putString(Map<String, Object> out, String key, String value, int maxLength) {
        String sanitized = sanitizeString(value, maxLength);
        if (sanitized != null && !sanitized.isBlank()) {
            out.put(key, sanitized);
        }
    }

    private static String sanitizeString(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        int limit = Math.max(1, maxLength);
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }

    private static String normalizeProviderContextCompactionProvider(String provider) {
        String value = Objects.toString(provider, "openai").trim().toLowerCase(Locale.ROOT);
        return "openai".equals(value) ? value : "openai";
    }

    private static String jsonText(JsonNode node) {
        return node != null && node.isTextual() ? node.asText().trim() : "";
    }

    private static String firstNonBlank(String first, String second) {
        String firstValue = Objects.toString(first, "").trim();
        return firstValue.isBlank() ? Objects.toString(second, "").trim() : firstValue;
    }
}
