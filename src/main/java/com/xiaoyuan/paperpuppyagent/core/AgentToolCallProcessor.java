package com.xiaoyuan.paperpuppyagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.xiaoyuan.paperpuppyagent.config.AgentModelCallProperties;
import com.xiaoyuan.paperpuppyagent.config.AgentToolResultProperties;
import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRuntime;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunLedgerStore;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentRunPersistenceReporter;
import com.xiaoyuan.paperpuppyagent.core.memory.AgentToolResultStore;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillExecutor;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates tool execution, result processing, stream events, and fact reporting. */
@Component
public class AgentToolCallProcessor {
    private final ToolInvocationExecutor invocationExecutor;
    private final ToolResultProcessor resultProcessor;
    private final ToolEventPublisher eventPublisher;
    private final ToolFactReporter factReporter;

    @Autowired
    public AgentToolCallProcessor(ToolInvocationExecutor invocationExecutor,
                                  ToolResultProcessor resultProcessor,
                                  ToolEventPublisher eventPublisher,
                                  ToolFactReporter factReporter) {
        this.invocationExecutor = invocationExecutor;
        this.resultProcessor = resultProcessor;
        this.eventPublisher = eventPublisher;
        this.factReporter = factReporter;
    }

    /** Compatibility constructor retained for focused unit tests and non-Spring callers. */
    public AgentToolCallProcessor(ObjectMapper om,
                                  AgentModelCallProperties modelCallProperties,
                                  SkillCatalog skillCatalog,
                                  SkillExecutor skillExecutor,
                                  AgentConversationMapper conversationMapper,
                                  AgentToolResultProperties toolResultProperties,
                                  AgentToolResultStore toolResultStore,
                                  AgentRunLedgerStore ledgerStore,
                                  AgentRunPersistenceReporter persistenceReporter) {
        this(
                new ToolInvocationExecutor(om, modelCallProperties, skillCatalog, skillExecutor),
                new ToolResultProcessor(om, conversationMapper, toolResultProperties, toolResultStore, ledgerStore),
                new ToolEventPublisher(),
                new ToolFactReporter(persistenceReporter));
    }

    @Autowired(required = false)
    public void setToolCompositionRuntime(ToolCompositionRuntime toolCompositionRuntime) {
        invocationExecutor.setToolCompositionRuntime(toolCompositionRuntime);
    }

    public ToolCallResult processCalls(List<ResponseFunctionToolCall> calls,
                                       ToolExecutionContext toolContext,
                                       StreamObserver<AgentChatResponse> out,
                                       AtomicBoolean cancelled,
                                       Context ctx) {
        List<ResponseInputItem> appendedInput = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();
        List<String> fileUrls = new ArrayList<>();
        int errorCount = 0;
        String lastErrorTool = null;
        String lastErrorMessage = null;
        String lastErrorCode = null;
        String lastErrorHint = null;
        int writeCallsSeen = 0;

        if (calls == null || calls.isEmpty()) {
            return new ToolCallResult(appendedInput, imageUrls, fileUrls, 0, 0,
                    null, null, null, null);
        }

        for (ResponseFunctionToolCall call : calls) {
            if (isCancelled(cancelled, ctx)) {
                return new ToolCallResult(appendedInput, imageUrls, fileUrls, calls.size(), errorCount,
                        lastErrorTool, lastErrorMessage, lastErrorCode, lastErrorHint);
            }
            String name = call == null ? "" : call.name();
            int writeOrdinal = invocationExecutor.writesData(name) ? ++writeCallsSeen : 0;
            ToolInvocationExecutor.InvocationResult invocation = invocationExecutor.execute(
                    call, toolContext, calls.size(), writeOrdinal);
            ToolResultProcessor.ProcessedToolResult processed = resultProcessor.process(
                    toolContext,
                    invocation.callId(),
                    invocation.name(),
                    invocation.arguments(),
                    invocation.result());

            eventPublisher.publishCall(out, invocation);
            eventPublisher.publishResult(out, invocation, processed);
            factReporter.report(toolContext, invocation, processed);

            appendedInput.add(processed.appendedInput());
            imageUrls.addAll(processed.imageUrls());
            fileUrls.addAll(processed.fileUrls());
            if (invocation.error()) {
                errorCount++;
                lastErrorTool = invocation.name();
                lastErrorMessage = invocation.errorMessage();
                lastErrorCode = invocation.errorCode();
                lastErrorHint = invocation.errorHint();
            }
        }

        return new ToolCallResult(appendedInput, imageUrls, fileUrls, calls.size(), errorCount,
                lastErrorTool, lastErrorMessage, lastErrorCode, lastErrorHint);
    }

    JsonNode compactForModel(String toolName, JsonNode result) {
        return resultProcessor.compactForModel(toolName, result);
    }

    JsonNode compactForModel(String toolName, JsonNode result,
                             com.xiaoyuan.paperpuppyagent.core.memory.StoredToolResult storedResult) {
        return resultProcessor.compactForModel(toolName, result, storedResult);
    }

    private static boolean isCancelled(AtomicBoolean cancelled, Context ctx) {
        return (cancelled != null && cancelled.get()) || (ctx != null && ctx.isCancelled());
    }

    public record ToolCallResult(
            List<ResponseInputItem> appendedInput,
            List<String> imageUrls,
            List<String> fileUrls,
            int totalCount,
            int errorCount,
            String lastErrorTool,
            String lastErrorMessage,
            String lastErrorCode,
            String lastErrorHint) {
        public int successCount() {
            return totalCount - errorCount;
        }
    }
}
