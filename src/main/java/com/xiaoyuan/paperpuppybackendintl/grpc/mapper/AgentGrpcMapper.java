package com.xiaoyuan.paperpuppybackendintl.grpc.mapper;

import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.AgentChatRequest;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;

import java.util.Objects;

/**
 * Agent gRPC 生成类 <-> 业务模型 的映射。
 *
 * <p>{@code enable_tools} 表示 Agent 技能总开关：ASK 语义会进入 Agent 的无工具路径，
 * AGENT 语义会进入 Agent 的工具路径。</p>
 */
public final class AgentGrpcMapper {

    private AgentGrpcMapper() {
    }

    public static AgentChatRequest toGrpc(AiChatRequest req) {
        if (req == null) {
            return AgentChatRequest.getDefaultInstance();
        }
        ChatExecutionPolicy policy = req.getExecutionPolicy();
        long userId = parseLongRequired("userId", req.getUserId());
        long sessionId = parseLongRequired("sessionId", req.getSessionId());
        AgentChatRequest.Builder b = AgentChatRequest.newBuilder()
                .setUserId(userId)
                .setSessionId(sessionId)
                .setFullContextJson(Objects.toString(req.getFullContextJson(), ""))
                .setMessage(Objects.toString(req.getMessage(), ""))
                .setEnableTools(policy != null && policy.isAllowAgentSkills())
                .setEnableImage(req.isEnableImage())
                .setIsVip(req.isVip())
                .setEnablePriorityProcessing(policy != null && policy.isUseOpenAiPriorityProcessing());
        if (req.getImageUrls() != null) {
            b.addAllImageUrls(req.getImageUrls());
        }
        if (req.getFileUrls() != null) {
            b.addAllFileUrls(req.getFileUrls());
        }
        // 设置项目 ID（用于工具执行上下文）
        if (req.getProjectId() != null && req.getProjectId() > 0) {
            b.setProjectId(req.getProjectId());
        }
        // 设置用户偏好语言（用于多语言提示词）
        if (req.getLocale() != null && !req.getLocale().isBlank()) {
            b.setLocale(req.getLocale());
        }
        // 设置用户自定义规则（拼接到 AI 提示词末尾）
        if (req.getCustomRules() != null && !req.getCustomRules().isBlank()) {
            b.setCustomRules(req.getCustomRules());
        }
        // 设置用户记忆（注入 AI 上下文作为背景知识）
        if (req.getUserMemories() != null && !req.getUserMemories().isBlank()) {
            b.setUserMemories(req.getUserMemories());
        }
        // 设置用户已关闭的技能列表（Agent 侧据此过滤可用工具）
        if (req.getDisabledSkillNames() != null && !req.getDisabledSkillNames().isEmpty()) {
            b.addAllDisabledSkillNames(req.getDisabledSkillNames());
        }
        // 设置用户自定义 PromptSkill 提示词（Agent 侧注入为独立系统消息）
        if (req.getUserSkillPrompt() != null && !req.getUserSkillPrompt().isBlank()) {
            b.setUserSkillPrompt(req.getUserSkillPrompt());
        }
        // 设置用户指定的模型
        if (req.getModel() != null && !req.getModel().isBlank()) {
            b.setModel(req.getModel());
        }
        if (req.getProviderId() != null && !req.getProviderId().isBlank()) {
            b.setProviderId(req.getProviderId());
        }
        if (req.getProviderApiKey() != null && !req.getProviderApiKey().isBlank()) {
            b.setProviderApiKey(req.getProviderApiKey());
        }
        return b.build();
    }

    private static long parseLongRequired(String field, String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("invalid " + field + ": blank");
        }
        String t = s.trim();
        try {
            long v = Long.parseLong(t);
            if (v <= 0) {
                throw new IllegalArgumentException("invalid " + field + ": " + t);
            }
            return v;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException("invalid " + field + ": " + t);
        }
    }
}
