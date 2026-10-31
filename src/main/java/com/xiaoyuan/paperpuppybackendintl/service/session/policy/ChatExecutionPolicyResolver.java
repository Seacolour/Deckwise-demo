package com.xiaoyuan.paperpuppybackendintl.service.session.policy;

import com.xiaoyuan.paperpuppybackendintl.config.ChatAgentProperties;
import com.xiaoyuan.paperpuppybackendintl.config.ChatExecutionPolicyProperties;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * 将外部聊天请求解析为 backend 内部执行策略。
 *
 * <p>当前 resolver 负责 AI-Intl 废弃迁移期的语义收口：
 * backend 不再把聊天请求路由到 AI-Intl，ASK / AGENT 都统一进入 Agent gRPC。
 * ASK 表示 Agent no-tools / pure chat，AGENT 表示允许 Agent skills/tool loop。</p>
 */
@Component
@RequiredArgsConstructor
public class ChatExecutionPolicyResolver {

    private final ChatAgentProperties agentProperties;
    private final ChatExecutionPolicyProperties executionPolicyProperties;

    public ChatExecutionPolicy resolve(UserMessageSendRequest request) {
        ConversationMode requestedMode = request != null && request.getMode() != null
                ? request.getMode()
                : ConversationMode.ASK;

        if (!agentProperties.isEnabled()) {
            throw new BusinessException(
                    ErrorCode.OPERATION_ERROR,
                    "Agent chat runtime is disabled");
        }

        ChatExecutionPolicy.ResolvedRoute resolvedRoute = ChatExecutionPolicy.ResolvedRoute.AGENT;
        boolean toolAgentMode = requestedMode == ConversationMode.AGENT;

        return ChatExecutionPolicy.builder()
                .requestedMode(requestedMode)
                .resolvedRoute(resolvedRoute)
                .allowAgentSkills(toolAgentMode && executionPolicyProperties.isAllowAgentSkills())
                .useOpenAiPriorityProcessing(resolvePriorityProcessing(request))
                .build();
    }

    private static boolean resolvePriorityProcessing(UserMessageSendRequest request) {
        if (request == null) {
            return false;
        }
        String serviceTier = Objects.toString(request.getServiceTier(), "").trim();
        if (serviceTier.isBlank()) {
            return request.isEnablePriorityProcessing();
        }
        return switch (serviceTier.toLowerCase(Locale.ROOT)) {
            case "priority", "fast" -> true;
            case "default", "standard", "auto" -> false;
            default -> throw new BusinessException(
                    ErrorCode.PARAMS_ERROR,
                    "Unsupported serviceTier: " + serviceTier);
        };
    }
}
