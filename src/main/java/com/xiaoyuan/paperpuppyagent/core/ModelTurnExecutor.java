package com.xiaoyuan.paperpuppyagent.core;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.Tool;
import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import com.xiaoyuan.paperpuppyagent.core.llm.OpenAiResponsesClient;
import com.xiaoyuan.paperpuppyagent.core.llm.ModelRequestContext;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import com.xiaoyuan.paperpuppyagent.grpc.agent.TextChunk;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes and reports a single OpenAI Responses model turn. */
@Component
@RequiredArgsConstructor
public class ModelTurnExecutor {
    private final OpenAiResponsesClient openAiResponsesClient;
    private final AgentModelCallProperties modelCallProperties;
    private final AgentRunLedgerStore ledgerStore;
    private final AgentRunPersistenceReporter persistenceReporter;
    private final AgentContextManager contextManager;

    public ModelTurn executeLoopTurn(String agentRunId,
                                     AgentChatRequest request,
                                     ModelRequestContext modelContext,
                                     String model,
                                     List<ResponseInputItem> input,
                                     List<Tool> tools,
                                     String instructions,
                                     String callSite,
                                     StreamObserver<AgentChatResponse> out,
                                     AtomicBoolean cancelled,
                                     Context ctx) {
        return execute(
                agentRunId,
                request,
                modelContext,
                model,
                input,
                tools,
                instructions,
                request.getEnablePriorityProcessing(),
                modelCallProperties.getDefaultMaxOutputTokens(),
                callSite,
                "agent-loop",
                out,
                cancelled,
                ctx);
    }

    public ModelTurn executeNoToolsTurn(String agentRunId,
                                        AgentChatRequest request,
                                        ModelRequestContext modelContext,
                                        String model,
                                        List<ResponseInputItem> input,
                                        String instructions,
                                        boolean enablePriorityProcessing,
                                        String callSite,
                                        StreamObserver<AgentChatResponse> out,
                                        AtomicBoolean cancelled,
                                        Context ctx) {
        return execute(
                agentRunId,
                request,
                modelContext,
                model,
                input,
                List.of(),
                instructions,
                enablePriorityProcessing,
                modelCallProperties.getNoToolsMaxOutputTokens(),
                callSite,
                callSite,
                out,
                cancelled,
                ctx);
    }

    private ModelTurn execute(String agentRunId,
                              AgentChatRequest request,
                              ModelRequestContext modelContext,
                              String model,
                              List<ResponseInputItem> input,
                              List<Tool> tools,
                              String instructions,
                              boolean enablePriorityProcessing,
                              long maxOutputTokens,
                              String callSite,
                              String reportCallSite,
                              StreamObserver<AgentChatResponse> out,
                              AtomicBoolean cancelled,
                              Context ctx) {
        ledgerStore.recordModelCall(agentRunId);
        List<ResponseInputItem> modelInput = contextManager.withLedgerView(agentRunId, input);
        long modelCallStartNs = System.nanoTime();
        Response response = openAiResponsesClient.createResponseStreaming(
                modelContext,
                modelInput,
                tools,
                instructions,
                enablePriorityProcessing,
                maxOutputTokens,
                callSite,
                delta -> {
                    if (!isCancelled(cancelled, ctx)) {
                        out.onNext(AgentChatResponse.newBuilder()
                                .setText(TextChunk.newBuilder().setText(delta).build())
                                .build());
                    }
                },
                () -> isCancelled(cancelled, ctx));
        persistenceReporter.recordModelCall(
                agentRunId,
                request,
                modelContext.providerId(),
                model,
                reportCallSite,
                modelInput,
                tools,
                instructions,
                maxOutputTokens,
                response,
                (System.nanoTime() - modelCallStartNs) / 1_000_000);
        return new ModelTurn(response, modelInput);
    }

    private static boolean isCancelled(AtomicBoolean cancelled, Context ctx) {
        return (cancelled != null && cancelled.get()) || (ctx != null && ctx.isCancelled());
    }

    public record ModelTurn(Response response, List<ResponseInputItem> modelInput) {
    }
}
