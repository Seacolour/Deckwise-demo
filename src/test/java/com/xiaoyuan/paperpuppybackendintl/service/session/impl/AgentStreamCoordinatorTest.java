package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.AgentGrpcClient;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.ChatGrpcCall;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.ChatStreamObserver;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.repository.AgentRunPersistenceRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import com.xiaoyuan.paperpuppybackendintl.service.billing.AiUsageBillingService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentStreamCoordinatorTest {

    @Test
    void completedStreamPersistsOutputAndUsageAndFinishesLifecycle() {
        Fixture fixture = newFixture();
        SseEmitter emitter = fixture.coordinator().start(fixture.input());
        fixture.tasks().get(2).run();

        ArgumentCaptor<ChatStreamObserver> observerCaptor = ArgumentCaptor.forClass(ChatStreamObserver.class);
        verify(fixture.agentGrpcClient()).chatStream(eq(fixture.request()), observerCaptor.capture());
        ChatStreamObserver observer = observerCaptor.getValue();
        observer.onText("done");
        observer.onResponse(Map.of(
                "input_tokens", "10",
                "output_tokens", "4",
                "total_tokens", "14",
                "model", "gpt-test"), List.of());
        observer.onCompleted();

        verify(fixture.sessionMessageRepository()).insertBatchAutoSeq(eq(101L), any());
        verify(fixture.lifecycle()).finishResources(
                eq(fixture.handle()), any(AtomicBoolean.class), eq("completed"));
        verify(fixture.aiUsageBillingService()).recordConversationUsage(
                eq(7L), eq(101L), eq(501L), any(), any());
        assertEquals(0L, emitter.getTimeout());
    }

    @Test
    void sseCompletionCancelsGrpcAndFinalizesWithoutWritingEmptyAssistantMessage() {
        Fixture fixture = newFixture();
        SseEmitter emitter = fixture.coordinator().start(fixture.input());
        fixture.tasks().get(2).run();

        Object completionCallback = ReflectionTestUtils.getField(emitter, "completionCallback");
        ((Runnable) completionCallback).run();

        verify(fixture.call()).cancel("sse disconnected");
        verify(fixture.sessionMessageRepository(), never()).insertBatchAutoSeq(any(), any());
        verify(fixture.lifecycle()).finishResources(
                eq(fixture.handle()), any(AtomicBoolean.class), eq("sse_disconnected"));
    }

    private Fixture newFixture() {
        AgentGrpcClient agentGrpcClient = mock(AgentGrpcClient.class);
        ChatExecutionLifecycle lifecycle = mock(ChatExecutionLifecycle.class);
        SessionMessageRepository sessionMessageRepository = mock(SessionMessageRepository.class);
        SessionService sessionService = mock(SessionService.class);
        AgentRunPersistenceRepository persistenceRepository = mock(AgentRunPersistenceRepository.class);
        AiUsageBillingService aiUsageBillingService = mock(AiUsageBillingService.class);
        ChatGrpcCall call = mock(ChatGrpcCall.class);
        List<Runnable> tasks = new ArrayList<>();
        TaskExecutor taskExecutor = tasks::add;
        AgentStreamCoordinator coordinator = new AgentStreamCoordinator(
                agentGrpcClient,
                lifecycle,
                sessionMessageRepository,
                sessionService,
                persistenceRepository,
                aiUsageBillingService,
                taskExecutor,
                new ObjectMapper());

        Session session = new Session();
        session.setId(101L);
        session.setProjectId(301L);
        when(sessionService.requireSession(7L, 101L)).thenReturn(session);
        when(sessionMessageRepository.insertBatchAutoSeq(eq(101L), any())).thenAnswer(invocation -> {
            List<SessionMessage> messages = invocation.getArgument(1);
            for (int index = 0; index < messages.size(); index++) {
                messages.get(index).setId(1_001L + index);
                messages.get(index).setSeq(20 + index);
            }
            return messages;
        });
        AiChatRequest request = AiChatRequest.builder()
                .userId("7")
                .sessionId("101")
                .fullContextJson("{\"version\":\"v1\"}")
                .message("hello")
                .build();
        when(agentGrpcClient.chatStream(eq(request), any())).thenReturn(call);
        ChatExecutionLifecycle.Handle handle = new ChatExecutionLifecycle.Handle(
                7L, 101L, "lock", "token", "7:101", "abort");
        AgentStreamCoordinator.StartInput input = new AgentStreamCoordinator.StartInput(
                7L, session, handle, request, "default", "gpt-test", 501L);
        return new Fixture(
                coordinator, tasks, agentGrpcClient, lifecycle, sessionMessageRepository,
                aiUsageBillingService, call, request, handle, input);
    }

    private record Fixture(AgentStreamCoordinator coordinator,
                           List<Runnable> tasks,
                           AgentGrpcClient agentGrpcClient,
                           ChatExecutionLifecycle lifecycle,
                           SessionMessageRepository sessionMessageRepository,
                           AiUsageBillingService aiUsageBillingService,
                           ChatGrpcCall call,
                           AiChatRequest request,
                           ChatExecutionLifecycle.Handle handle,
                           AgentStreamCoordinator.StartInput input) {
    }
}
