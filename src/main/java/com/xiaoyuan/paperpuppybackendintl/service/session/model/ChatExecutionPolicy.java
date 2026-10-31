package com.xiaoyuan.paperpuppybackendintl.service.session.model;

import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import lombok.Builder;
import lombok.Value;

/**
 * 一次聊天请求在 backend 内部解析后的执行策略。
 *
 * <p>它只描述 backend 当前对能力和路由的判断结果，不直接暴露到外部协议。</p>
 *
 * <p>ASK / AGENT 均进入 Agent；差异由能力开关表达。</p>
 */
@Value
@Builder
public class ChatExecutionPolicy {

    ConversationMode requestedMode;
    ResolvedRoute resolvedRoute;
    boolean allowAgentSkills;
    boolean useOpenAiPriorityProcessing;

    public boolean isAgentRoute() {
        return resolvedRoute == ResolvedRoute.AGENT;
    }

    public enum ResolvedRoute {
        AGENT
    }
}
