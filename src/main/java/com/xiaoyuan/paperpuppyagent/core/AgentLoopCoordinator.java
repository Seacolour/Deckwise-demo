package com.xiaoyuan.paperpuppyagent.core;

import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRegistry;
import com.xiaoyuan.paperpuppyagent.core.deadline.DynamicDeadlineManager;
import com.xiaoyuan.paperpuppyagent.core.deadline.DynamicDeadlineProperties;
import com.xiaoyuan.paperpuppyagent.core.llm.ModelRequestContext;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatResponse;
import com.xiaoyuan.paperpuppyagent.grpc.agent.Done;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ReAct / Tool-calling Loop（MVP）：
 *
 * <p>
 * 使用 OpenAI Responses API 的 function calling 格式：
 * </p>
 * <ul>
 * <li>模型输出：output item type=function_call（含 name/arguments/call_id）</li>
 * <li>应用回传：input item type=function_call_output（含 call_id/output）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoopCoordinator {
    private final AgentInputAssembler inputAssembler;
    private final AgentRunCoordinator runCoordinator;
    private final ModelTurnExecutor modelTurnExecutor;
    private final AgentConversationMapper conversationMapper;
    private final AgentToolCallProcessor toolCallProcessor;
    private final DynamicDeadlineProperties deadlineProperties;
    private final AgentContextManager contextManager;

    @Autowired(required = false)
    public void setToolCompositionRegistry(ToolCompositionRegistry toolCompositionRegistry) {
        inputAssembler.setToolCompositionRegistry(toolCompositionRegistry);
    }

    @Value("${agent.loop.max-iterations:10}")
    private int maxIterations;

    /**
     * 连续工具失败上限：达到后不再盲目循环，给用户一个可操作的错误提示并结束。
     */
    @Value("${agent.loop.max-consecutive-tool-errors:2}")
    private int maxConsecutiveToolErrors;

    public void run(AgentChatRequest request, StreamObserver<AgentChatResponse> out, AtomicBoolean cancelled,
            Context ctx) {
        final long startNs = System.nanoTime();
        AgentRunCoordinator.RunScope runScope = runCoordinator.open(request);
        final String agentRunId = runScope.agentRunId();
        try {
            AgentInputAssembler.PreparedInput prepared = inputAssembler.assemble(request, agentRunId);
            String sessionKey = prepared.sessionKey();
            List<ResponseInputItem> input = prepared.input();
            int loopHistoryStartIndex = prepared.loopHistoryStartIndex();
            List<com.openai.models.responses.Tool> tools = prepared.tools();
            String model = prepared.model();
            ModelRequestContext modelContext = prepared.modelContext();
            String instructions = prepared.instructions();
            ToolExecutionContext toolContext = prepared.toolContext();
            runCoordinator.start(runScope, request, model);

            // 动态超时管理器：基于工具调用心跳延长超时预算
            DynamicDeadlineManager deadlineManager = new DynamicDeadlineManager(
                    deadlineProperties.getBaseThinkingBudgetMs(),
                    deadlineProperties.getPerToolBudgetMs(),
                    deadlineProperties.getMaxTotalCapMs(), sessionKey);

            int consecutiveToolErrors = 0;
            String lastErrorTool = null;
            String lastErrorMessage = null;
            String lastErrorCode = null;
            String lastErrorHint = null;
            for (int iter = 0; iter < Math.max(maxIterations, 1); iter++) {
                if (isCancelled(cancelled, ctx)) {
                    log.warn("终止 loop: sessionKey={}, iter={}", sessionKey, iter);
                    runScope.mark("cancelled");
                    return;
                }
                input = contextManager.compactLoopHistoryIfEnabled(sessionKey, input, loopHistoryStartIndex);

                // 动态超时检测：预算耗尽或达到硬上限时，向用户解释并优雅退出
                if (deadlineManager.isExpired()) {
                    log.warn("[Agent][Loop] 动态超时，触发收口: sessionKey={}, iter={}, elapsedMs={}, remainingMs={}",
                            sessionKey, iter, deadlineManager.getElapsedMs(), deadlineManager.getRemainingBudgetMs());
                    input.add(conversationMapper.systemMessageItem(
                            "系统提示：你已接近时间限制。请停止调用任何工具，直接回复用户：总结目前已完成的工作，"
                                    + "并告知用户如有更多需求可以继续发送消息。"));
                    input = contextManager.compactLoopHistoryIfEnabled(sessionKey, input, loopHistoryStartIndex);
                    streamOneTurnNoTools(modelContext, model, input, instructions, request.getEnablePriorityProcessing(),
                            out, cancelled, ctx, "dynamic-deadline sessionKey=" + sessionKey + " iter=" + (iter + 1),
                            agentRunId, request);
                    runScope.mark("dynamic_deadline_exceeded");
                    emitDone(out, "dynamic_deadline_exceeded");
                    return;
                }

                log.info("[Agent][Loop] 第{}轮开始: sessionKey={}, inputItems={}", iter + 1, sessionKey, input.size());
                String modelCallSite = "agent-loop sessionKey=" + sessionKey + " iter=" + (iter + 1);
                Response resp = modelTurnExecutor.executeLoopTurn(
                        agentRunId,
                        request,
                        modelContext,
                        model,
                        input,
                        tools,
                        instructions,
                        modelCallSite,
                        out,
                        cancelled,
                        ctx).response();
                if (isCancelled(cancelled, ctx)) {
                    runScope.mark("cancelled");
                    return;
                }
                if (resp == null) {
                    // streaming 被取消或异常中断（取消情况下直接返回即可）
                    runScope.mark("cancelled");
                    return;
                }
                log.info("[Agent][Loop] 模型响应完成: provider={}, sessionKey={}, responseId={}, serviceTier={}",
                        modelContext.providerId(),
                        sessionKey,
                        Objects.toString(resp.id(), ""),
                        resp.serviceTier().map(serviceTier -> serviceTier.asString()).orElse(""));
                List<ResponseOutputItem> outputItems = resp.output();
                if (outputItems == null || outputItems.isEmpty()) {
                    log.warn("[Agent][Loop] 模型未返回 output，结束: provider={}, sessionKey={}, iter={}",
                            modelContext.providerId(), sessionKey, iter + 1);
                    runScope.mark("no_output");
                    emitDone(out, "no_output");
                    return;
                }

                log.info("[Agent][Loop] 模型 output items: provider={}, sessionKey={}, count={}",
                        modelContext.providerId(), sessionKey, outputItems.size());
                // input_list += response.output（typed 版）
                for (ResponseOutputItem item : outputItems) {
                    if (item == null) {
                        continue;
                    }
                    if (item.isWebSearchCall()) {
                        log.info("[Agent][Loop] web_search_call 已回灌: sessionKey={}, searchId={}",
                                sessionKey, item.asWebSearchCall().id());
                    }
                }
                List<ResponseInputItem> nextTurnItems = AgentContextManager.toNextTurnInputItems(
                        outputItems,
                        contextManager.retainAssistantMessages());
                input.addAll(nextTurnItems);
                AgentContextManager.OfficialContextCompactionResult officialCompaction = null;
                if (contextManager.officialContextManagementEnabled()
                        && AgentContextManager.containsCompaction(nextTurnItems)) {
                    officialCompaction = AgentContextManager.compactAroundLatestOfficialCompaction(input);
                    if (officialCompaction.removedItems() > 0) {
                        input = officialCompaction.inputItems();
                        loopHistoryStartIndex = 0;
                        log.info("[Agent][Loop] official context compaction applied: sessionKey={}, inputItems {}->{}, removedItems={}, approxSavedChars={}, responseId={}",
                                sessionKey,
                                officialCompaction.originalItems(),
                                officialCompaction.compactedItems(),
                                officialCompaction.removedItems(),
                                officialCompaction.estimatedSavedChars(),
                                Objects.toString(resp.id(), ""));
                    }
                }

                // 注意：文本已经通过 output_text.delta 流式转发过，这里不再 forward 完整 message，避免重复输出

                // 2) handle function calls (if any)
                List<ResponseFunctionToolCall> calls = AgentContextManager.findFunctionCalls(outputItems);
                if (calls.isEmpty()) {
                    contextManager.persistOfficialContextCompactionIfNeeded(
                            agentRunId,
                            request,
                            model,
                            instructions,
                            resp,
                            officialCompaction,
                            input,
                            modelCallSite);
                    log.info("[Agent][Loop] 未检测到工具调用(function_call)，本轮结束并完成对话: sessionKey={}, iter={}", sessionKey,
                            iter + 1);
                    runScope.mark("completed");
                    emitDone(out, "completed");
                    return;
                }

                log.info("[Agent][Loop] 检测到工具调用: sessionKey={}, iter={}, callCount={}", sessionKey, iter + 1,
                        calls.size());
                AgentToolCallProcessor.ToolCallResult toolResult = toolCallProcessor.processCalls(
                        calls, toolContext, out, cancelled, ctx);
                input.addAll(toolResult.appendedInput());
                contextManager.persistOfficialContextCompactionIfNeeded(
                        agentRunId,
                        request,
                        model,
                        instructions,
                        resp,
                        officialCompaction,
                        input,
                        modelCallSite);
                runCoordinator.checkpoint(toolContext);

                // 失败计数：达到阈值时停止继续调用工具，转为让模型用自然语言向用户解释/澄清（不再"突兀结束"）
                // 按成功工具数延长超时预算（即使有部分失败，成功的工具仍应延长 deadline）
                for (int i = 0; i < toolResult.successCount(); i++) {
                    deadlineManager.onToolExecuted();
                }

                if (toolResult.errorCount() > 0) {
                    // 仅当批次内全部失败时才累加 consecutive 计数；部分成功说明工具系统整体可用
                    if (toolResult.successCount() == 0) {
                        consecutiveToolErrors += toolResult.errorCount();
                    } else {
                        consecutiveToolErrors = 0;
                    }
                    lastErrorTool = toolResult.lastErrorTool();
                    lastErrorMessage = toolResult.lastErrorMessage();
                    lastErrorCode = toolResult.lastErrorCode();
                    lastErrorHint = toolResult.lastErrorHint();

                    // 轻量纠错提示（system）：告诉模型不要用相同参数重试，必要时向用户澄清
                    String sysHint = "系统提示：上一次工具调用失败(tool=" + Objects.toString(lastErrorTool, "unknown")
                            + ", code=" + Objects.toString(lastErrorCode, "unknown")
                            + ", err=" + Objects.toString(lastErrorMessage, "unknown") + ")。"
                            + (lastErrorHint == null || lastErrorHint.isBlank()
                            ? "不要用相同参数重复调用工具；请改用其它工具/调整参数，或向用户确认关键信息（例如 document_id/file_id 是否正确）。"
                            : "建议：" + lastErrorHint);
                    input.add(conversationMapper.systemMessageItem(sysHint));

                    if (consecutiveToolErrors >= Math.max(maxConsecutiveToolErrors, 1)) {
                        // 触发"收口"：禁用 tools 再请求一次模型，让其用自然语言向用户解释问题并提出下一步
                        String closePrompt = "系统提示：你已连续多次工具调用失败。"
                                + "请停止调用任何工具，改为直接回复用户：说明失败原因（例如参数无效/缺少必要ID），"
                                + "并提出可执行的下一步（例如让用户确认/提供正确的 document_id 或先列出文件供选择）。";
                        input.add(conversationMapper.systemMessageItem(closePrompt));
                        input = contextManager.compactLoopHistoryIfEnabled(sessionKey, input, loopHistoryStartIndex);
                        streamOneTurnNoTools(modelContext, model, input, instructions, request.getEnablePriorityProcessing(),
                                out, cancelled, ctx, "tool-errors-exceeded sessionKey=" + sessionKey + " iter=" + (iter + 1),
                                agentRunId, request);
                        runScope.mark("tool_errors_exceeded");
                        emitDone(out, "tool_errors_exceeded");
                        return;
                    }
                } else {
                    consecutiveToolErrors = 0;
                }

                if (!toolResult.imageUrls().isEmpty()) {
                    input.add(conversationMapper.userMessageItemWithImages(
                            "Here is the image you requested. Please analyze it and continue.",
                            toolResult.imageUrls()));
                }

                if (!toolResult.fileUrls().isEmpty()) {
                    input.add(conversationMapper.userMessageItemWithFiles(
                            "Here is the file you requested. Please read it and continue.",
                            toolResult.fileUrls()));
                }
            }

            log.warn("[Agent][Loop] 达到最大迭代次数，强制结束: sessionKey={}, maxIterations={}", sessionKey,
                    Math.max(maxIterations, 1));
            // 兜底收口：禁用 tools 再请求一次模型，让其自然地向用户解释并引导下一步
            input.add(conversationMapper.systemMessageItem(
                    "系统提示：你已达到本次最大迭代次数（工具调用可能反复失败）。"
                            + "请停止调用任何工具，直接回复用户：总结当前卡点，并询问用户提供/确认必要信息后再继续。"));
            input = contextManager.compactLoopHistoryIfEnabled(sessionKey, input, loopHistoryStartIndex);
            streamOneTurnNoTools(modelContext, model, input, instructions, request.getEnablePriorityProcessing(),
                    out, cancelled, ctx, "max-iterations sessionKey=" + sessionKey + " iter=" + Math.max(maxIterations, 1),
                    agentRunId, request);
            runScope.mark("max_iterations_reached");
            emitDone(out, "max_iterations_reached");
            log.info("[Agent][Loop] 请求结束(max_iterations_reached): sessionKey={}, costMs={}", sessionKey,
                    (System.nanoTime() - startNs) / 1_000_000);

        } catch (Exception e) {
            runScope.fail(e);
            log.error("[Agent][Loop] 处理异常: userId={}, sessionId={}, error={}",
                    request == null ? 0L : request.getUserId(),
                    request == null ? 0L : request.getSessionId(), e.getMessage(), e);
            out.onError(toGrpcRuntimeException(e));
        } finally {
            runCoordinator.finish(runScope, request);
        }
    }
    private static boolean isCancelled(AtomicBoolean cancelled, Context ctx) {
        return (cancelled != null && cancelled.get()) || (ctx != null && ctx.isCancelled());
    }

    private void emitDone(StreamObserver<AgentChatResponse> out, String reason) {
        out.onNext(AgentChatResponse.newBuilder().setDone(Done.newBuilder().setReason(reason).build()).build());
        out.onCompleted();
    }

    /**
     * 额外跑一轮“禁用工具”的模型调用，用于自然语言收口（解释/澄清/下一步），避免直接 done 造成突兀。
     */
    private void streamOneTurnNoTools(ModelRequestContext modelContext,
            String model,
            List<ResponseInputItem> input,
            String instructions,
            boolean enablePriorityProcessing,
            StreamObserver<AgentChatResponse> out,
            AtomicBoolean cancelled,
            Context ctx,
            String callSite,
            String agentRunId,
            AgentChatRequest request) {
        if (isCancelled(cancelled, ctx)) {
            return;
        }
        try {
            ModelTurnExecutor.ModelTurn turn = modelTurnExecutor.executeNoToolsTurn(
                    agentRunId,
                    request,
                    modelContext,
                    model,
                    input,
                    instructions,
                    enablePriorityProcessing,
                    callSite,
                    out,
                    cancelled,
                    ctx);
            Response resp = turn.response();
            if (resp == null) {
                return;
            }
            List<ResponseInputItem> modelInput = turn.modelInput();
            List<ResponseInputItem> nextTurnItems = AgentContextManager.toNextTurnInputItems(
                    resp.output(),
                    contextManager.retainAssistantMessages());
            if (contextManager.officialContextManagementEnabled()
                    && AgentContextManager.containsCompaction(nextTurnItems)) {
                ArrayList<ResponseInputItem> compactable = new ArrayList<>(modelInput.size() + nextTurnItems.size());
                compactable.addAll(modelInput);
                compactable.addAll(nextTurnItems);
                AgentContextManager.OfficialContextCompactionResult compaction =
                        AgentContextManager.compactAroundLatestOfficialCompaction(compactable);
                contextManager.persistOfficialContextCompactionIfNeeded(
                        agentRunId,
                        request,
                        model,
                        instructions,
                        resp,
                        compaction,
                        compaction == null ? compactable : compaction.inputItems(),
                        callSite);
            }
            // 不再进入 loop：这里仅用于输出自然语言；无需处理 outputItems / function_call
        } catch (Exception e) {
            log.warn("[Agent][Loop] finalize(no-tools) failed: {}", e.getMessage());
        }
    }

    private static StatusRuntimeException toGrpcRuntimeException(Throwable t) {
        if (t instanceof StatusRuntimeException sre)
            return sre;
        String msg = Objects.toString(t == null ? null : t.getMessage(), "internal error");
        return Status.INTERNAL.withDescription(msg).withCause(t).asRuntimeException();
    }

}
