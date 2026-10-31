package com.xiaoyuan.paperpuppybackendintl.grpc.client.model;

import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import lombok.Builder;
import lombok.Value;
import lombok.ToString;

import java.util.List;

/**
 * 对话上游请求模型（避免业务层直接依赖 gRPC 生成类）。
 *
 * <p>虽然类名保留为 {@code AiChatRequest}，但它现在同时作为 AI 与 Agent
 * 两条 mapper 的共享上游载体。</p>
 */
@Value
@Builder
public class AiChatRequest {
    String userId;
    String sessionId;
    String fullContextJson;

    /**
     * 可选：若 fullContextJson 已包含本轮消息，可置空
     */
    String message;

    /**
     * 预留：多模态图片
     */
    @Builder.Default
    List<String> imageUrls = List.of();

    /**
     * 文件 URL（多模态 input_file）：ASK 模式下有 publicUrl 的文件，直接传给 OpenAI 原生解析
     */
    @Builder.Default
    List<String> fileUrls = List.of();

    @Builder.Default
    boolean enableImage = false;
    @Builder.Default
    boolean vip = false;

    /**
     * backend 内部执行策略。用于将清晰语义降维映射到兼容协议字段。
     */
    ChatExecutionPolicy executionPolicy;

    /**
     * 当前项目 ID（用于工具执行上下文，由 Backend 强制注入）
     */
    Long projectId;

    /**
     * 用户偏好语言（如 "zh-CN", "en-US"），用于选择对应语言的 AI 提示词
     */
    String locale;

    /**
     * 用户自定义规则（从 user_rules 表聚合），拼接到 AI 提示词末尾
     */
    String customRules;

    /**
     * 用户记忆（从 user_memories 表聚合），注入 AI 上下文作为背景知识
     */
    String userMemories;

    /**
     * 用户已关闭的技能名称列表（Agent 侧据此过滤 skill.json 中的 functions）
     */
    @Builder.Default
    List<String> disabledSkillNames = List.of();

    /**
     * 用户自定义 PromptSkill 拼接后的提示词（Agent 侧注入为独立系统消息）
     */
    String userSkillPrompt;

    /**
     * 后端解析后的模型名称。为空时由下游服务使用自身默认模型兜底。
     */
    String model;

    /** Trusted provider resolved by Backend; never supplied directly by the browser. */
    String providerId;

    /** Transient BYOK secret for the local Agent call. Never log or persist. */
    @ToString.Exclude
    String providerApiKey;
}
