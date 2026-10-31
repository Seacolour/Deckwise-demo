package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserMemory;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserRule;
import com.xiaoyuan.paperpuppybackendintl.repository.UserCustomSkillRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserMemoryRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserRuleRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.UserSkillSettingRepository;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelResolutionService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCatalogService;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCredentialService;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the trusted request sent from Backend chat orchestration to the Agent runtime.
 *
 * <p>User/session/project identity comes from validated Backend state. User preferences and skill
 * settings are loaded best-effort and cannot override those trusted identity fields.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentRequestFactory {

    private final UserRepository userRepository;
    private final UserRuleRepository userRuleRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final UserSkillSettingRepository userSkillSettingRepository;
    private final UserCustomSkillRepository userCustomSkillRepository;
    private final ModelResolutionService modelResolutionService;
    private final ModelCatalogService modelCatalogService;
    private final ModelCredentialService modelCredentialService;

    /** Resolved before any chat history is written. Do not log this object. */
    public static final class ModelRoute {
        private final String catalogId;
        private final String providerId;
        private final String upstreamModelId;
        private final String apiKey;

        ModelRoute(String catalogId, String providerId, String upstreamModelId, String apiKey) {
            this.catalogId = catalogId;
            this.providerId = providerId;
            this.upstreamModelId = upstreamModelId;
            this.apiKey = apiKey;
        }

        public String catalogId() { return catalogId; }
        public String providerId() { return providerId; }
        public String upstreamModelId() { return upstreamModelId; }
        String apiKey() { return apiKey; }

        @Override
        public String toString() {
            return "ModelRoute[catalogId=" + catalogId + ", providerId=" + providerId + "]";
        }
    }

    public record RequestInput(Long userId,
                               Session session,
                               String fullContextJson,
                               String message,
                               ChatExecutionPolicy executionPolicy,
                               List<String> imageUrls,
                               List<String> fileUrls,
                               ModelRoute modelRoute) {
    }

    public ModelRoute resolveModelRoute(UserMessageSendRequest request, Long userId, Long sessionId) {
        String catalogId = modelResolutionService.resolveForUser(
                userId,
                request != null ? request.getModel() : null,
                request != null ? request.getMode() : null);
        ModelCatalogService.ModelDefinition model = modelCatalogService.findEnabledModel(catalogId)
                .orElseThrow(() -> new BusinessException(
                        com.xiaoyuan.paperpuppybackendintl.common.ErrorCode.PARAMS_ERROR,
                        "Unsupported model: " + catalogId));
        String apiKey = modelCredentialService.resolveApiKey(userId, model.providerId())
                .orElseThrow(() -> new BusinessException(
                        com.xiaoyuan.paperpuppybackendintl.common.ErrorCode.OPERATION_ERROR,
                        "请先在账户设置中配置 " + model.providerId() + " API Key"));
        return new ModelRoute(model.id(), model.providerId(), model.upstreamModelId(), apiKey);
    }

    public AiChatRequest create(RequestInput input) {
        Long userId = input.userId();
        Session session = input.session();
        Long sessionId = session == null ? null : session.getId();
        List<String> imageUrls = input.imageUrls() == null ? List.of() : input.imageUrls();
        List<String> fileUrls = input.fileUrls() == null ? List.of() : input.fileUrls();

        String locale = resolveLocale(userId);
        String customRules = resolveCustomRules(userId);
        String userMemories = resolveUserMemories(userId);
        List<String> disabledSkillNames = resolveDisabledSkillNames(userId);
        String userSkillPrompt = resolveUserSkillPrompt(userId);

        return AiChatRequest.builder()
                .userId(String.valueOf(userId))
                .sessionId(String.valueOf(sessionId))
                .fullContextJson(input.fullContextJson())
                .message(input.message())
                .executionPolicy(input.executionPolicy())
                .enableImage(!imageUrls.isEmpty())
                .imageUrls(imageUrls)
                .fileUrls(fileUrls)
                .vip(false)
                .projectId(session == null ? null : session.getProjectId())
                .locale(locale)
                .customRules(customRules)
                .userMemories(userMemories)
                .disabledSkillNames(disabledSkillNames)
                .userSkillPrompt(userSkillPrompt)
                .model(input.modelRoute().upstreamModelId())
                .providerId(input.modelRoute().providerId())
                .providerApiKey(input.modelRoute().apiKey())
                .build();
    }

    private String resolveLocale(Long userId) {
        try {
            return userRepository.findById(userId)
                    .map(user -> user.getPreferredLocale())
                    .orElse(null);
        } catch (Exception exception) {
            log.warn("User preferences fetch failed, fallback to default: userId={}, err={}",
                    userId, exception.getMessage());
            return null;
        }
    }

    private String resolveCustomRules(Long userId) {
        try {
            List<UserRule> enabledRules = userRuleRepository.listEnabledByUserId(userId);
            if (enabledRules.isEmpty()) {
                return null;
            }
            return enabledRules.stream()
                    .map(UserRule::getContent)
                    .collect(Collectors.joining("\n- ", "- ", ""));
        } catch (Exception exception) {
            log.warn("User rules fetch failed, fallback to none: userId={}, err={}", userId, exception.getMessage());
            return null;
        }
    }

    private String resolveUserMemories(Long userId) {
        try {
            List<UserMemory> memories = userMemoryRepository.listByUserId(userId);
            if (memories.isEmpty()) {
                return null;
            }
            return memories.stream()
                    .map(memory -> memory.getTitle() + ": " + memory.getContent())
                    .collect(Collectors.joining("\n- ", "- ", ""));
        } catch (Exception exception) {
            log.warn("User memories fetch failed, fallback to none: userId={}, err={}", userId, exception.getMessage());
            return null;
        }
    }

    private List<String> resolveDisabledSkillNames(Long userId) {
        try {
            return userSkillSettingRepository.listDisabledSkillNames(userId);
        } catch (Exception exception) {
            log.warn("User skill settings fetch failed, fallback to none: userId={}, err={}",
                    userId, exception.getMessage());
            return List.of();
        }
    }

    private String resolveUserSkillPrompt(Long userId) {
        try {
            var customSkills = userCustomSkillRepository.listEnabledByUserId(userId);
            if (customSkills.isEmpty()) {
                return null;
            }
            return customSkills.stream()
                    .map(skill -> "### " + skill.getSkillName() + "\n" + skill.getContent())
                    .collect(Collectors.joining("\n\n",
                            "## User Custom Skills\nThe following are user-installed custom skill instructions:\n\n", ""));
        } catch (Exception exception) {
            log.warn("User custom skills fetch failed, fallback to none: userId={}, err={}",
                    userId, exception.getMessage());
            return null;
        }
    }
}
