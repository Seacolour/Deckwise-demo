package com.xiaoyuan.paperpuppyagent.core;

import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.Tool;
import com.xiaoyuan.paperpuppyagent.config.AgentWorkMemoryProperties;
import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import com.xiaoyuan.paperpuppyagent.core.composition.ToolCompositionRegistry;
import com.xiaoyuan.paperpuppyagent.core.llm.ModelEndpointRegistry;
import com.xiaoyuan.paperpuppyagent.core.llm.ModelRequestContext;
import com.xiaoyuan.paperpuppyagent.core.prompt.AgentInstructionsProvider;
import com.xiaoyuan.paperpuppyagent.core.skill.SkillCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillBindingCatalog;
import com.xiaoyuan.paperpuppyagent.core.skill.binding.SkillFunctionBinding;
import com.xiaoyuan.paperpuppyagent.core.tool.ToolExecutionContext;
import com.xiaoyuan.paperpuppyagent.grpc.agent.AgentChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Builds the immutable request-derived inputs used by one agent run. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentInputAssembler {
    private final OpenAiProperties openAiProperties;
    private final SkillCatalog skillCatalog;
    private final SkillBindingCatalog bindingCatalog;
    private final AgentConversationMapper conversationMapper;
    private final AgentInstructionsProvider instructionsProvider;
    private final AgentWorkMemoryProperties workMemoryProperties;
    private final ModelEndpointRegistry modelEndpointRegistry;
    private ToolCompositionRegistry toolCompositionRegistry;

    @Value("${agent.loop.allow-user-skill-prompt:false}")
    private boolean allowUserSkillPrompt;

    @Autowired(required = false)
    public void setToolCompositionRegistry(ToolCompositionRegistry toolCompositionRegistry) {
        this.toolCompositionRegistry = toolCompositionRegistry;
    }

    public PreparedInput assemble(AgentChatRequest request, String agentRunId) {
        String requestedModel = request.getProviderId().isBlank()
                ? pickModel(request.getModel()) : request.getModel();
        ModelRequestContext modelContext = modelEndpointRegistry.resolve(
                requestedModel, request.getProviderId(), request.getProviderApiKey());
        modelEndpointRegistry.validateRequest(modelContext, request.getEnableTools(),
                request.getImageUrlsCount(), request.getFileUrlsCount());
        String sessionKey = sessionKey(request);
        List<ResponseInputItem> input = conversationMapper.buildInitialInputFromCanonical(
                request, modelContext.providerId());
        injectUserSkillPrompt(request, input);

        String runnerMode = request.getEnableTools() ? "agent_loop" : "ask_only";
        log.info(
                "[Agent][Loop] 开始处理请求: agentRunId={}, userId={}, sessionId={}, projectId={}, enableTools={}, enableImage={}, isVip={}, enablePriorityProcessing={}, workMemoryEnabled={}, runnerMode={}",
                agentRunId,
                request.getUserId(), request.getSessionId(), request.getProjectId(), request.getEnableTools(),
                request.getEnableImage(), request.getIsVip(), request.getEnablePriorityProcessing(),
                workMemoryProperties.isEnabled(), runnerMode);

        ToolExecutionContext toolContext = new ToolExecutionContext(
                request.getUserId(), request.getProjectId(), request.getSessionId(), agentRunId);
        input.add(conversationMapper.userMessageItemWithAttachments(
                request.getMessage(), request.getImageUrlsList(), request.getFileUrlsList()));
        int loopHistoryStartIndex = input.size();

        Set<String> disabledSkills = new HashSet<>(request.getDisabledSkillNamesList());
        List<Tool> tools = request.getEnableTools()
                ? skillCatalog.toOpenAiSdkTools(disabledSkills, function -> isFunctionVisibleToModel(function.name()))
                : List.of();
        if ("openrouter".equals(modelContext.providerId())) {
            // OpenRouter function tools use the standard Responses shape. Its server-side
            // web-search tools use a separate extension that is not configured here.
            tools = tools.stream().filter(Tool::isFunction).toList();
        }
        String model = modelContext.upstreamModel();
        String instructions = buildInstructions(request);
        log.info("[Agent][Loop] 提示词加载: locale={}, toolsEnabled={}, toolCount={}, model={}, priority={}",
                request.getLocale(), request.getEnableTools(), tools.size(), model,
                request.getEnablePriorityProcessing());
        return new PreparedInput(
                sessionKey, input, loopHistoryStartIndex, tools, model, instructions, toolContext, modelContext);
    }

    private void injectUserSkillPrompt(AgentChatRequest request, List<ResponseInputItem> input) {
        String userSkillPrompt = request.getUserSkillPrompt();
        if (allowUserSkillPrompt && userSkillPrompt != null && !userSkillPrompt.isBlank()) {
            input.add(0, conversationMapper.systemMessageItem(userSkillPrompt));
            log.info("[Agent][Loop] 用户自定义 PromptSkill 已注入: promptLen={}", userSkillPrompt.length());
        } else if (!allowUserSkillPrompt && userSkillPrompt != null && !userSkillPrompt.isBlank()) {
            log.info("[Agent][Loop] 用户自定义 PromptSkill 已忽略: promptLen={}", userSkillPrompt.length());
        }
    }

    private String buildInstructions(AgentChatRequest request) {
        String locale = request.getLocale();
        String instructions = request.getEnableTools()
                ? Objects.toString(instructionsProvider.get(locale), "").trim()
                : askOnlyInstructions(locale);
        String compositionInstructions = !request.getEnableTools() || toolCompositionRegistry == null
                ? ""
                : Objects.toString(toolCompositionRegistry.instructionsForModel(), "").trim();
        if (!compositionInstructions.isBlank()) {
            instructions = instructions + "\n\n" + compositionInstructions;
        }
        String userMemories = request.getUserMemories();
        if (userMemories != null && !userMemories.isBlank()) {
            instructions = instructions + "\n\n## User Memories\n" + userMemories.trim();
            log.info("[Agent][Loop] 用户记忆已加载: locale={}, memoriesLen={}",
                    locale, userMemories.length());
        }
        String customRules = request.getCustomRules();
        if (customRules != null && !customRules.isBlank()) {
            instructions = instructions + "\n\n## User Custom Rules\n" + customRules.trim();
            log.info("[Agent][Loop] 用户自定义规则已加载: locale={}, customRulesLen={}",
                    locale, customRules.length());
        }
        return instructions;
    }

    private static String askOnlyInstructions(String locale) {
        if (Objects.toString(locale, "").toLowerCase(Locale.ROOT).startsWith("zh")) {
            return """
                    你是 AgentWorkbench 的问答助手。本轮没有可用工具，请直接用自然语言回答。
                    不要声称已读取工作区文件，也不要输出模拟工具调用或内部协议标记。
                    如果用户询问未提供内容的工作区文档，请说明当前问答模式无法读取，并建议切换到 Agent 任务。
                    """.trim();
        }
        return """
                You are AgentWorkbench's chat assistant. No tools are available in this turn; answer directly in natural language.
                Do not claim to have read workspace files or output simulated tool calls or internal protocol markers.
                If asked about a workspace document whose contents were not provided, explain that chat mode cannot read it and suggest Agent mode.
                """.trim();
    }

    private String pickModel(String requestedModel) {
        String model = Objects.toString(requestedModel, "").trim();
        if (model.isBlank()) {
            model = Objects.toString(openAiProperties.model(), "").trim();
        }
        return model.isBlank() ? "gpt-4.1-mini" : model;
    }

    private boolean isFunctionVisibleToModel(String functionName) {
        if (("agent_tool_result_open".equals(functionName) || "agent_ledger_update".equals(functionName))
                && !workMemoryProperties.isEnabled()) {
            return false;
        }
        SkillFunctionBinding binding = bindingCatalog.getBinding(functionName);
        return binding != null && binding.isVisibleToModel();
    }

    private static String sessionKey(AgentChatRequest request) {
        return request == null ? "0:0" : request.getUserId() + ":" + request.getSessionId();
    }

    public record PreparedInput(
            String sessionKey,
            List<ResponseInputItem> input,
            int loopHistoryStartIndex,
            List<Tool> tools,
            String model,
            String instructions,
            ToolExecutionContext toolContext,
            ModelRequestContext modelContext) {
    }
}
