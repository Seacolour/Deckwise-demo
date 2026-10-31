package com.xiaoyuan.paperpuppyagent.core;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseInputItem;
import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import com.xiaoyuan.paperpuppyagent.core.llm.ModelEndpointRegistry;
import com.xiaoyuan.paperpuppyagent.core.llm.ModelRequestContext;
import com.xiaoyuan.paperpuppyagent.core.llm.OpenAiResponsesClient;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelTurnExecutorTest {

    @Test
    void loopTurnStreamsTextAndReportsTheSameModelInput() {
        OpenAiResponsesClient client = mock(OpenAiResponsesClient.class);
        AgentRunLedgerStore ledgerStore = mock(AgentRunLedgerStore.class);
        AgentRunPersistenceReporter persistenceReporter = mock(AgentRunPersistenceReporter.class);
        AgentContextManager contextManager = mock(AgentContextManager.class);
        AgentModelCallProperties properties = new AgentModelCallProperties();
        ModelTurnExecutor executor = new ModelTurnExecutor(
                client, properties, ledgerStore, persistenceReporter, contextManager);

        List<ResponseInputItem> input = List.of();
        List<ResponseInputItem> modelInput = new ArrayList<>();
        when(contextManager.withLedgerView("run-test", input)).thenReturn(modelInput);
        Response response = mock(Response.class);
        when(client.createResponseStreaming(
                any(ModelRequestContext.class), anyList(), anyList(), anyString(), anyBoolean(), anyLong(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> onTextDelta = invocation.getArgument(7);
                    onTextDelta.accept("hello");
                    return response;
                });

        CollectingObserver observer = new CollectingObserver();
        AgentChatRequest request = AgentChatRequest.newBuilder().setEnablePriorityProcessing(true).build();
        ModelRequestContext modelContext = new ModelEndpointRegistry(
                new OpenAiProperties("test-key", "", "gpt-test", "", ""), "")
                .resolve("gpt-test", "", "");
        ModelTurnExecutor.ModelTurn turn = executor.executeLoopTurn(
                "run-test",
                request,
                modelContext,
                "gpt-test",
                input,
                List.of(),
                "instructions",
                "agent-loop sessionKey=1:2 iter=1",
                observer,
                new AtomicBoolean(false),
                Context.ROOT);

        assertEquals(response, turn.response());
        assertEquals(modelInput, turn.modelInput());
        assertEquals("hello", observer.responses.getFirst().getText().getText());
        verify(ledgerStore).recordModelCall("run-test");
        verify(persistenceReporter).recordModelCall(
                eq("run-test"),
                eq(request),
                eq("openai"),
                eq("gpt-test"),
                eq("agent-loop"),
                eq(modelInput),
                eq(List.of()),
                eq("instructions"),
                eq(properties.getDefaultMaxOutputTokens()),
                eq(response),
                anyLong());
    }

    private static final class CollectingObserver implements StreamObserver<AgentChatResponse> {
        private final List<AgentChatResponse> responses = new ArrayList<>();

        @Override
        public void onNext(AgentChatResponse value) {
            responses.add(value);
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onCompleted() {
        }
    }
}
