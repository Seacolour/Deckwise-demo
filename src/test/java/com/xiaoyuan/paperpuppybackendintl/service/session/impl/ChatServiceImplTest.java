package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.AgentGrpcClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.model.dto.document.DocumentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageClientContext;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import com.xiaoyuan.paperpuppybackendintl.model.enums.DocumentType;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.*;
import com.xiaoyuan.paperpuppybackendintl.service.billing.AiUsageBillingService;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelResolutionService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCatalogService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCredentialService;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import com.xiaoyuan.paperpuppybackendintl.service.session.AutoTitleService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import com.xiaoyuan.paperpuppybackendintl.service.session.policy.ChatExecutionPolicyResolver;
import com.xiaoyuan.paperpuppybackendintl.service.session.util.FileQuoteResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceImplTest {

    @Test
    void chatValidatesAttachmentsBeforePersistingUserTurn() {
        TestFixture fixture = newFixture(command -> command.run());

        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);
        when(fixture.sessionService().requireSession(7L, 101L)).thenReturn(session);
        when(fixture.executionPolicyResolver().resolve(any())).thenReturn(askOnlyPolicy());
        when(fixture.redisClient().setIfAbsent(anyString(), anyString(), eq(75L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(fixture.sessionMessageRepository().listRecentBySession(101L, 200)).thenReturn(List.of());

        ObjectStorageFile imageFile = new ObjectStorageFile();
        imageFile.setId(11L);
        imageFile.setObjectKey("dev/files/7/20260422/image.png");
        imageFile.setMimeType("image/png");
        imageFile.setStatus("available");
        imageFile.setDeleted(false);
        when(fixture.objectStorageFileRepository().findAvailableByOwnerAndSource(7L, "bytes", "bad-image"))
                .thenReturn(Optional.of(imageFile));
        when(fixture.r2Client().exists(imageFile.getObjectKey())).thenReturn(true);

        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setMode(ConversationMode.ASK);
        request.setMessage("please read this");
        request.setFileCacheKeys(List.of("bad-image"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service().chat(7L, 101L, request)
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("imageCacheKeys"));
        verify(fixture.sessionMessageRepository(), never()).insertAutoSeq(any(SessionMessage.class));
        verify(fixture.sessionService(), never()).touchAfterUserMessage(anyLong(), anyLong(), anyString());
        verify(fixture.projectService(), never()).touchProjectActivity(anyLong());
        verify(fixture.autoTitleService(), never()).tryUpdateTitleAfterFirstTurn(anyLong(), anyLong(), anyString(), any());
        verify(fixture.redisClient()).deleteIfValue(anyString(), anyString());
    }

    @Test
    void prepareAttachmentWritesAllowsNewFileWhenSessionAlreadyHasFiveFiles() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());
        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);

        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(21L);
        file.setObjectKey("dev/files/7/20260703/new-file.pdf");
        file.setMimeType("application/pdf");
        file.setStatus("available");
        file.setDeleted(false);
        file.setOriginalName("new-file.pdf");
        file.setSizeBytes(128L);
        when(fixture.objectStorageFileRepository().findAvailableByOwnerAndSource(7L, "bytes", "new-file"))
                .thenReturn(Optional.of(file));
        when(fixture.r2Client().exists(file.getObjectKey())).thenReturn(true);
        when(fixture.projectFileLinkRepository().existsActiveLink(501L, 21L)).thenReturn(true);

        ChatAttachmentService.PreparedAttachmentWrites result = fixture.chatAttachmentService().prepareAttachmentWrites(
                7L,
                session,
                List.of("new-file"),
                List.of(),
                List.of("old-file-1", "old-file-2", "old-file-3", "old-file-4", "old-file-5"),
                List.of());

        assertEquals(List.of("new-file"), result.newFileKeys());
        assertEquals(1, result.messages().size());
        assertTrue(result.messages().getFirst().getRawContent().contains("\"cacheKey\":\"new-file\""));
    }

    @Test
    void findProjectScopedFileByKeyPrefersProjectLinkBeforeOwnerLookup() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());

        ObjectStorageFile linkedFile = new ObjectStorageFile();
        linkedFile.setId(41L);
        linkedFile.setStatus("available");
        linkedFile.setDeleted(false);

        when(fixture.projectFileLinkRepository().findActiveFileIdByProjectAndCacheKey(501L, "same-cache-key"))
                .thenReturn(Optional.of(41L));
        when(fixture.objectStorageFileRepository().findById(41L)).thenReturn(Optional.of(linkedFile));

        ObjectStorageFile resolved = fixture.chatAttachmentService()
                .findProjectScopedFileByKey(7L, 501L, "same-cache-key");

        assertEquals(41L, resolved.getId());
        verify(fixture.objectStorageFileRepository(), never())
                .findAvailableByOwnerAndSource(anyLong(), anyString(), anyString());
    }

    @Test
    void buildCanonicalV1SkipsNonFinalMessages() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());

        SessionMessage finalUser = new SessionMessage();
        finalUser.setRole(SessionMessageRole.USER);
        finalUser.setType(SessionMessageType.TEXT);
        finalUser.setContent("final user");
        finalUser.setStatus(SessionMessageStatus.FINAL);

        SessionMessage abortedAssistant = new SessionMessage();
        abortedAssistant.setRole(SessionMessageRole.ASSISTANT);
        abortedAssistant.setType(SessionMessageType.TEXT);
        abortedAssistant.setContent("partial assistant");
        abortedAssistant.setStatus(SessionMessageStatus.ABORTED);

        SessionMessage errorTool = new SessionMessage();
        errorTool.setRole(SessionMessageRole.TOOL);
        errorTool.setType(SessionMessageType.TOOL_OUTPUT);
        errorTool.setContent("error tool output");
        errorTool.setRawContent("{\"status\":\"error\"}");
        errorTool.setStatus(SessionMessageStatus.ERROR);

        SessionMessage finalAssistant = new SessionMessage();
        finalAssistant.setRole(SessionMessageRole.ASSISTANT);
        finalAssistant.setType(SessionMessageType.TEXT);
        finalAssistant.setContent("final assistant");
        finalAssistant.setStatus(SessionMessageStatus.FINAL);

        String fullContextJson = fixture.chatContextAssembler().buildCanonicalV1(
                List.of(finalUser, abortedAssistant, errorTool, finalAssistant),
                List.of(Map.of("role", "system", "type", "text", "content", "extra item"))
        );

        assertTrue(fullContextJson.contains("extra item"));
        assertTrue(fullContextJson.contains("final user"));
        assertTrue(fullContextJson.contains("final assistant"));
        assertFalse(fullContextJson.contains("partial assistant"));
        assertFalse(fullContextJson.contains("error tool output"));
    }

    @Test
    void buildAgentContextJsonUsesActiveProviderWindowAndDeltaMessages() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());
        ReflectionTestUtils.setField(fixture.chatContextAssembler(), "providerContextCompactionEnabled", true);

        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);
        String compactedWindowJson = """
                {
                  "format": "responses_compacted_window_v1",
                  "schema_version": 1,
                  "provider": "openai",
                  "items": [
                    {
                      "type": "compaction",
                      "id": "cmp-1",
                      "encrypted_content": "encrypted-window"
                    }
                  ]
                }
                """;
        when(fixture.agentRunPersistenceRepository().findActiveContextCompaction(7L, 101L, "openai"))
                .thenReturn(Optional.of(new AgentRunPersistenceRepository.ActiveContextCompaction(
                        44L,
                        "openai",
                        "responses_compacted_window_v1",
                        compactedWindowJson,
                        10,
                        "gpt-5-codex",
                        "resp-1",
                        OffsetDateTime.now()
                )));

        List<SessionMessage> history = List.of(
                textMessage(1, SessionMessageRole.USER, "old user covered by provider window"),
                textMessage(10, SessionMessageRole.ASSISTANT, "covered assistant"),
                textMessage(11, SessionMessageRole.USER, "delta user"),
                toolCallMessage(12, "call-delta", "json_document_read"),
                toolResultMessage(13, "call-delta", "json_document_read"),
                textMessage(14, SessionMessageRole.ASSISTANT, "delta assistant")
        );

        String fullContextJson = invokeBuildAgentContextJson(
                fixture.chatContextAssembler(),
                7L,
                session,
                history,
                List.of(Map.of("role", "system", "type", "text", "content", "turn extra"))
        );

        var items = new ObjectMapper().readTree(fullContextJson).path("items");
        assertEquals("turn extra", items.get(0).path("content").asText());
        assertEquals("provider_context_window", items.get(1).path("type").asText());
        assertEquals("openai", items.get(1).path("provider").asText());
        assertEquals("responses_compacted_window_v1", items.get(1).path("format").asText());
        assertEquals("encrypted-window", items.get(1).path("items").get(0).path("encrypted_content").asText());
        assertTrue(fullContextJson.contains("delta user"));
        assertTrue(fullContextJson.contains("delta assistant"));
        assertTrue(fullContextJson.contains("call-delta"));
        assertFalse(fullContextJson.contains("old user covered by provider window"));
        assertFalse(fullContextJson.contains("covered assistant"));
        verify(fixture.agentRunPersistenceRepository()).findActiveContextCompaction(7L, 101L, "openai");
    }

    @Test
    void buildAgentContextJsonFallsBackWhenProviderWindowInvalid() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());
        ReflectionTestUtils.setField(fixture.chatContextAssembler(), "providerContextCompactionEnabled", true);

        Session session = new Session();
        session.setId(101L);
        when(fixture.agentRunPersistenceRepository().findActiveContextCompaction(7L, 101L, "openai"))
                .thenReturn(Optional.of(new AgentRunPersistenceRepository.ActiveContextCompaction(
                        45L,
                        "openai",
                        "responses_compacted_window_v1",
                        "{\"format\":\"responses_compacted_window_v1\",\"provider\":\"openai\",\"items\":[]}",
                        10,
                        "gpt-5-codex",
                        "resp-2",
                        OffsetDateTime.now()
                )));

        String fullContextJson = invokeBuildAgentContextJson(
                fixture.chatContextAssembler(),
                7L,
                session,
                List.of(
                        textMessage(1, SessionMessageRole.USER, "fallback old user"),
                        textMessage(11, SessionMessageRole.USER, "fallback delta user")
                ),
                List.of()
        );

        assertFalse(fullContextJson.contains("provider_context_window"));
        assertTrue(fullContextJson.contains("fallback old user"));
        assertTrue(fullContextJson.contains("fallback delta user"));
    }

    @Test
    void selectContextMessagesAppliesTextAndToolQuotasWithoutOrphanToolCalls() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());
        List<SessionMessage> history = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            SessionMessageRole role = i % 2 == 0 ? SessionMessageRole.ASSISTANT : SessionMessageRole.USER;
            history.add(textMessage(i, role, "text-" + i));
        }
        for (int i = 1; i <= 5; i++) {
            history.add(toolCallMessage(100 + i * 2, "call-" + i, "tool-" + i));
            history.add(toolResultMessage(101 + i * 2, "call-" + i, "tool-" + i));
        }
        history.add(toolCallMessage(999, "call-pending", "json_document_read"));

        List<SessionMessage> selected = fixture.chatContextAssembler().selectContextMessages(history);

        assertEquals(20, selected.size());
        assertFalse(containsContent(selected, "text-1"));
        assertFalse(containsContent(selected, "text-2"));
        assertTrue(containsContent(selected, "text-3"));
        assertTrue(containsContent(selected, "text-14"));
        assertFalse(containsToolMessage(selected, SessionMessageRole.ASSISTANT, "call-1"));
        assertFalse(containsToolMessage(selected, SessionMessageRole.TOOL, "call-1"));
        for (int i = 2; i <= 5; i++) {
            assertTrue(containsToolMessage(selected, SessionMessageRole.ASSISTANT, "call-" + i));
            assertTrue(containsToolMessage(selected, SessionMessageRole.TOOL, "call-" + i));
        }
        assertFalse(containsToolMessage(selected, SessionMessageRole.ASSISTANT, "call-pending"));
    }

    @Test
    void buildClientContextItemsInjectsTransientValidatedContext() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());

        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);

        DocumentDTO document = new DocumentDTO();
        document.setId(123L);
        document.setProjectId(501L);
        document.setDocumentType(DocumentType.MARKDOWN);
        document.setTitle("Project notes");
        when(fixture.documentService().get(7L, 123L, false)).thenReturn(document);
        DocumentDTO secondDocument = new DocumentDTO();
        secondDocument.setId(124L);
        secondDocument.setProjectId(501L);
        secondDocument.setDocumentType(DocumentType.MARKDOWN);
        secondDocument.setTitle("Source material");
        when(fixture.documentService().get(7L, 124L, false)).thenReturn(secondDocument);

        UserMessageClientContext.ActiveDocument activeDocument = new UserMessageClientContext.ActiveDocument();
        activeDocument.setDocumentId("123");
        activeDocument.setType("markdown");
        activeDocument.setFileName("notes.md");

        UserMessageClientContext.EnvironmentContext environment = new UserMessageClientContext.EnvironmentContext();
        environment.setTimeZone("Asia/Shanghai");
        environment.setLocale("zh-CN");
        environment.setClientNow("2026-04-27T13:40:00+08:00");

        UserMessageClientContext clientContext = new UserMessageClientContext();
        clientContext.setActiveDocument(activeDocument);
        clientContext.setReferencedDocumentIds(List.of("123", "124"));
        clientContext.setEnvironment(environment);

        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setClientContext(clientContext);

        List<Map<String, Object>> items = fixture.chatContextAssembler()
                .buildClientContextItems(7L, session, request);

        assertEquals(1, items.size());
        Map<String, Object> item = items.getFirst();
        assertEquals("system", item.get("role"));
        String content = String.valueOf(item.get("content"));
        assertTrue(content.contains("Transient client context for this turn only"));
        assertTrue(content.contains("\"kind\":\"client_context\""));
        assertTrue(content.contains("\"scope\":\"current_turn_only\""));
        assertTrue(content.contains("\"documentId\":123"));
        assertTrue(content.contains("\"documentType\":\"markdown\""));
        assertTrue(content.contains("\"referencedDocuments\""));
        assertTrue(content.contains("\"documentId\":124"));
        assertTrue(content.contains("\"title\":\"Source material\""));
        assertFalse(content.contains("\"currentPage\""));
        assertFalse(content.contains("\"selection\""));
        assertFalse(content.contains("selectedText"));
        assertFalse(content.contains("selectedNodeIds"));
        assertTrue(content.contains("\"timeZone\":\"Asia/Shanghai\""));
    }

    @Test
    void buildClientContextItemsRejectsDocumentsFromAnotherProject() {
        TestFixture fixture = newFixture(command -> command.run());
        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);
        DocumentDTO foreignDocument = new DocumentDTO();
        foreignDocument.setId(124L);
        foreignDocument.setProjectId(999L);
        when(fixture.documentService().get(7L, 124L, false)).thenReturn(foreignDocument);

        UserMessageClientContext context = new UserMessageClientContext();
        context.setReferencedDocumentIds(List.of("124"));
        UserMessageSendRequest request = new UserMessageSendRequest();
        request.setClientContext(context);

        assertThrows(BusinessException.class, () -> fixture.chatContextAssembler()
                .buildClientContextItems(7L, session, request));
    }

    @Test
    void buildPersistedAgentProjectionItemsInjectTaskAndArtifactProjectionForSameProject() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());
        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);
        when(fixture.agentRunPersistenceRepository().findLatestTaskProjectionJson(
                eq(7L),
                eq(101L),
                eq(501L),
                any(OffsetDateTime.class),
                eq(6000)
        )).thenReturn(Optional.of("{\"kind\":\"persisted_agent_task_projection\",\"goal\":\"continue deck\"}"));
        when(fixture.agentRunPersistenceRepository().findLatestArtifactProjectionJson(
                eq(7L),
                eq(101L),
                eq(501L),
                any(OffsetDateTime.class),
                eq(6000)
        )).thenReturn(Optional.of("{\"kind\":\"persisted_agent_artifact_projection\",\"activeDocument\":{\"documentId\":101}}"));

        List<Map<String, Object>> items = fixture.chatContextAssembler()
                .buildPersistedAgentProjectionItems(7L, session);

        assertEquals(2, items.size());
        assertEquals("system", items.get(0).get("role"));
        String taskContent = String.valueOf(items.get(0).get("content"));
        String artifactContent = String.valueOf(items.get(1).get("content"));
        assertTrue(taskContent.contains("Persisted Agent Task Projection"));
        assertTrue(taskContent.contains("persisted_agent_task_projection"));
        assertTrue(taskContent.contains("continue deck"));
        assertTrue(artifactContent.contains("Persisted Artifact Projection"));
        assertTrue(artifactContent.contains("persisted_agent_artifact_projection"));
        assertTrue(artifactContent.contains("\"documentId\":101"));
        verify(fixture.agentRunPersistenceRepository()).findLatestTaskProjectionJson(
                eq(7L),
                eq(101L),
                eq(501L),
                any(OffsetDateTime.class),
                eq(6000)
        );
        verify(fixture.agentRunPersistenceRepository()).findLatestArtifactProjectionJson(
                eq(7L),
                eq(101L),
                eq(501L),
                any(OffsetDateTime.class),
                eq(6000)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveAssistantMessagePersistsTextAndToolEventsInStreamOrder() throws Exception {
        TestFixture fixture = newFixture(command -> command.run());
        Session session = new Session();
        session.setId(101L);
        session.setProjectId(501L);
        when(fixture.sessionService().requireSession(7L, 101L)).thenReturn(session);

        AgentStreamCoordinator.AssistantOutputAccumulator accumulator =
                new AgentStreamCoordinator.AssistantOutputAccumulator();

        Map<String, Object> call = new java.util.LinkedHashMap<>();
        call.put("phase", "call");
        call.put("call_id", "call-1");
        call.put("name", "json_document_read");
        call.put("arguments_json", "{\"document_id\":455}");

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("phase", "result");
        result.put("call_id", "call-1");
        result.put("name", "json_document_read");
        result.put("result_json", "{\"success\":true}");

        accumulator.appendText("我会先读取第一页。");
        accumulator.appendToolEvent(call);
        accumulator.appendToolEvent(result);
        accumulator.appendText("已经完成替换。");

        when(fixture.sessionMessageRepository().insertBatchAutoSeq(eq(101L), any())).thenAnswer(invocation -> {
            List<SessionMessage> saved = invocation.getArgument(1);
            for (int i = 0; i < saved.size(); i++) {
                saved.get(i).setId(1_001L + i);
                saved.get(i).setSeq(20 + i);
            }
            return saved;
        });

        fixture.agentStreamCoordinator().saveAssistantMessage(
                7L,
                session,
                accumulator,
                Map.of("total_tokens", "42", "model", "gpt-test"),
                List.of(),
                SessionMessageStatus.FINAL);

        ArgumentCaptor<List<SessionMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(fixture.sessionMessageRepository()).insertBatchAutoSeq(eq(101L), captor.capture());
        List<SessionMessage> inserted = captor.getValue();

        assertEquals(4, inserted.size());
        assertEquals(SessionMessageType.TEXT, inserted.get(0).getType());
        assertEquals(SessionMessageRole.ASSISTANT, inserted.get(0).getRole());
        assertEquals("我会先读取第一页。", inserted.get(0).getContent());

        assertEquals(SessionMessageType.TOOL_OUTPUT, inserted.get(1).getType());
        assertEquals(SessionMessageRole.ASSISTANT, inserted.get(1).getRole());
        assertEquals("call-1", inserted.get(1).getToolCallId());
        assertEquals("{\"document_id\":455}", inserted.get(1).getRawContent());

        assertEquals(SessionMessageType.TOOL_OUTPUT, inserted.get(2).getType());
        assertEquals(SessionMessageRole.TOOL, inserted.get(2).getRole());
        assertEquals("call-1", inserted.get(2).getToolCallId());
        assertEquals("{\"success\":true}", inserted.get(2).getRawContent());

        assertEquals(SessionMessageType.TEXT, inserted.get(3).getType());
        assertEquals(SessionMessageRole.ASSISTANT, inserted.get(3).getRole());
        assertEquals("已经完成替换。", inserted.get(3).getContent());
        assertEquals(42, inserted.get(3).getTokenCount());
        assertTrue(inserted.get(3).getMetadata().data().contains("gpt-test"));
        verify(fixture.agentRunPersistenceRepository()).bindLatestSessionAgentRunMessageSpan(
                7L,
                101L,
                1_001L,
                1_004L,
                20,
                23
        );
    }

    private static ChatExecutionPolicy askOnlyPolicy() {
        return ChatExecutionPolicy.builder()
                .requestedMode(ConversationMode.ASK)
                .resolvedRoute(ChatExecutionPolicy.ResolvedRoute.AGENT)
                .allowAgentSkills(false)
                .useOpenAiPriorityProcessing(false)
                .build();
    }

    private static SessionMessage textMessage(int seq, SessionMessageRole role, String content) {
        SessionMessage message = new SessionMessage();
        message.setSeq(seq);
        message.setRole(role);
        message.setType(SessionMessageType.TEXT);
        message.setContent(content);
        message.setStatus(SessionMessageStatus.FINAL);
        message.setDeleted(false);
        return message;
    }

    private static SessionMessage toolCallMessage(int seq, String callId, String name) {
        SessionMessage message = new SessionMessage();
        message.setSeq(seq);
        message.setRole(SessionMessageRole.ASSISTANT);
        message.setType(SessionMessageType.TOOL_OUTPUT);
        message.setToolCallId(callId);
        message.setName(name);
        message.setContent("{\"phase\":\"call\",\"call_id\":\"" + callId + "\",\"name\":\"" + name + "\"}");
        message.setRawContent("{}");
        message.setStatus(SessionMessageStatus.FINAL);
        message.setDeleted(false);
        return message;
    }

    private static SessionMessage toolResultMessage(int seq, String callId, String name) {
        SessionMessage message = new SessionMessage();
        message.setSeq(seq);
        message.setRole(SessionMessageRole.TOOL);
        message.setType(SessionMessageType.TOOL_OUTPUT);
        message.setToolCallId(callId);
        message.setName(name);
        message.setContent("{\"phase\":\"result\",\"call_id\":\"" + callId + "\",\"name\":\"" + name + "\"}");
        message.setRawContent("{\"success\":true}");
        message.setStatus(SessionMessageStatus.FINAL);
        message.setDeleted(false);
        return message;
    }

    private static boolean containsContent(List<SessionMessage> messages, String content) {
        return messages.stream().anyMatch(message -> content.equals(message.getContent()));
    }

    private static boolean containsToolMessage(List<SessionMessage> messages,
                                               SessionMessageRole role,
                                               String callId) {
        return messages.stream().anyMatch(message -> message.getRole() == role
                && callId.equals(message.getToolCallId()));
    }

    private static String invokeBuildAgentContextJson(ChatContextAssembler contextAssembler,
                                                      Long userId,
                                                      Session session,
                                                      List<SessionMessage> history,
                                                      List<Map<String, Object>> extraItems) throws Exception {
        return contextAssembler.buildAgentContextJson(userId, session, history, extraItems).fullContextJson();
    }

    private TestFixture newFixture(TaskExecutor taskExecutor) {
        SessionService sessionService = mock(SessionService.class);
        AutoTitleService autoTitleService = mock(AutoTitleService.class);
        SessionMessageRepository sessionMessageRepository = mock(SessionMessageRepository.class);
        ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        FileTextFragmentRepository fileTextFragmentRepository = mock(FileTextFragmentRepository.class);
        FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AgentGrpcClient agentGrpcClient = mock(AgentGrpcClient.class);
        ChatExecutionPolicyResolver executionPolicyResolver = mock(ChatExecutionPolicyResolver.class);
        RedisClient redisClient = mock(RedisClient.class);
        R2Client r2Client = mock(R2Client.class);
        ProjectService projectService = mock(ProjectService.class);
        DocumentService documentService = mock(DocumentService.class);
        FileQuoteResolver fileQuoteResolver = mock(FileQuoteResolver.class);
        UserRuleRepository userRuleRepository = mock(UserRuleRepository.class);
        UserMemoryRepository userMemoryRepository = mock(UserMemoryRepository.class);
        UserSkillSettingRepository userSkillSettingRepository = mock(UserSkillSettingRepository.class);
        UserCustomSkillRepository userCustomSkillRepository = mock(UserCustomSkillRepository.class);
        ModelResolutionService modelResolutionService = mock(ModelResolutionService.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        ModelCredentialService modelCredentialService = mock(ModelCredentialService.class);
        when(modelResolutionService.resolveForUser(eq(7L), nullable(String.class), nullable(ConversationMode.class)))
                .thenReturn("openai/gpt-5.6-terra");
        when(modelCatalogService.findEnabledModel("openai/gpt-5.6-terra"))
                .thenReturn(Optional.of(new ModelCatalogService.ModelDefinition(
                        "openai/gpt-5.6-terra", "openai", "gpt-5.6-terra", "openai-responses",
                        true, true, "OpenAI Fast", null, true, null, null)));
        when(modelCredentialService.resolveApiKey(7L, "openai"))
                .thenReturn(Optional.of("test-key"));
        AiUsageBillingService aiUsageBillingService = mock(AiUsageBillingService.class);
        AgentRunPersistenceRepository agentRunPersistenceRepository = mock(AgentRunPersistenceRepository.class);

        ObjectMapper objectMapper = new ObjectMapper();
        ChatAttachmentService chatAttachmentService = new ChatAttachmentService(
                objectStorageFileRepository,
                projectFileLinkRepository,
                fileTextFragmentRepository,
                fileAccessGrantRepository,
                r2Client,
                objectMapper
        );
        ChatContextAssembler chatContextAssembler = new ChatContextAssembler(
                objectMapper,
                documentService,
                agentRunPersistenceRepository,
                chatAttachmentService
        );
        AgentRequestFactory agentRequestFactory = new AgentRequestFactory(
                userRepository,
                userRuleRepository,
                userMemoryRepository,
                userSkillSettingRepository,
                userCustomSkillRepository,
                modelResolutionService,
                modelCatalogService,
                modelCredentialService
        );
        ChatExecutionLifecycle chatExecutionLifecycle = new ChatExecutionLifecycle(
                redisClient,
                agentRunPersistenceRepository
        );
        AgentStreamCoordinator agentStreamCoordinator = new AgentStreamCoordinator(
                agentGrpcClient,
                chatExecutionLifecycle,
                sessionMessageRepository,
                sessionService,
                agentRunPersistenceRepository,
                aiUsageBillingService,
                taskExecutor,
                objectMapper
        );
        ChatServiceImpl service = new ChatServiceImpl(
                sessionService,
                autoTitleService,
                sessionMessageRepository,
                chatAttachmentService,
                chatContextAssembler,
                agentRequestFactory,
                chatExecutionLifecycle,
                agentStreamCoordinator,
                executionPolicyResolver,
                taskExecutor,
                projectService,
                fileQuoteResolver
        );
        return new TestFixture(
                service,
                chatAttachmentService,
                chatContextAssembler,
                agentRequestFactory,
                chatExecutionLifecycle,
                agentStreamCoordinator,
                sessionService,
                autoTitleService,
                sessionMessageRepository,
                objectStorageFileRepository,
                projectFileLinkRepository,
                executionPolicyResolver,
                redisClient,
                r2Client,
                projectService,
                documentService,
                agentGrpcClient,
                modelResolutionService,
                agentRunPersistenceRepository
        );
    }

    private record TestFixture(ChatServiceImpl service,
            ChatAttachmentService chatAttachmentService,
            ChatContextAssembler chatContextAssembler,
            AgentRequestFactory agentRequestFactory,
            ChatExecutionLifecycle chatExecutionLifecycle,
            AgentStreamCoordinator agentStreamCoordinator,
            SessionService sessionService,
            AutoTitleService autoTitleService,
            SessionMessageRepository sessionMessageRepository,
            ObjectStorageFileRepository objectStorageFileRepository,
            ProjectFileLinkRepository projectFileLinkRepository,
            ChatExecutionPolicyResolver executionPolicyResolver,
            RedisClient redisClient,
            R2Client r2Client,
            ProjectService projectService,
            DocumentService documentService,
            AgentGrpcClient agentGrpcClient,
            ModelResolutionService modelResolutionService,
            AgentRunPersistenceRepository agentRunPersistenceRepository) {
    }
}
