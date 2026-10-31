package com.xiaoyuan.paperpuppybackendintl.service.model;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelResolutionService {

    private final ModelCatalogService modelCatalogService;
    private final ModelCredentialService modelCredentialService;

    public String resolve(String requestedModel) {
        if (!modelCatalogService.isEnabled()) {
            return modelCatalogService.defaultModel();
        }
        if (!StringUtils.hasText(requestedModel)) {
            return modelCatalogService.defaultModel();
        }
        return modelCatalogService.findEnabledModel(requestedModel)
                .map(ModelCatalogService.ModelDefinition::id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PARAMS_ERROR,
                        "Unsupported model: " + requestedModel.trim()
                ));
    }

    public String resolveForUser(Long userId, String requestedModel) {
        return resolveForUser(userId, requestedModel, ConversationMode.ASK);
    }

    public String resolveForUser(Long userId, String requestedModel, ConversationMode mode) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        if (StringUtils.hasText(requestedModel)) {
            ModelCatalogService.ModelDefinition model = modelCatalogService.findEnabledModel(requestedModel)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR,
                            "Unsupported model: " + requestedModel.trim()));
            requireModeSupported(model, mode);
            requireCredential(userId, model.providerId());
            return model.id();
        }
        ModelCatalogService.ModelDefinition preferred = modelCatalogService.findEnabledModel(modelCatalogService.defaultModel())
                .orElse(null);
        if (preferred != null && supportsMode(preferred, mode)
                && modelCredentialService.hasCredential(userId, preferred.providerId())) {
            return preferred.id();
        }
        return modelCatalogService.listEnabledModels().stream()
                .filter(model -> supportsMode(model, mode))
                .filter(model -> modelCredentialService.hasCredential(userId, model.providerId()))
                .map(ModelCatalogService.ModelDefinition::id)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "请先在账户设置中配置模型提供方 API Key"));
    }

    private void requireCredential(Long userId, String providerId) {
        if (!modelCredentialService.hasCredential(userId, providerId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "请先在账户设置中配置 " + providerId + " API Key");
        }
    }

    private void requireModeSupported(ModelCatalogService.ModelDefinition model, ConversationMode mode) {
        if (!supportsMode(model, mode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "该模型暂不支持 " + mode + " 模式");
        }
    }

    private static boolean supportsMode(ModelCatalogService.ModelDefinition model, ConversationMode mode) {
        return mode == ConversationMode.AGENT ? model.agentSupported() : model.askSupported();
    }

    public List<AvailableModelDTO> getAvailableModels() {
        return modelCatalogService.listEnabledModels().stream()
                .map(model -> new AvailableModelDTO(
                        model.id(),
                        model.providerId(),
                        model.displayName(),
                        model.description(),
                        null,
                        model.askSupported(),
                        model.agentSupported()))
                .toList();
    }

    public List<AvailableModelDTO> getAvailableModels(Long userId) {
        return getAvailableModels().stream()
                .filter(model -> modelCredentialService.hasCredential(userId, model.providerId()))
                .toList();
    }

    public record AvailableModelDTO(String id,
                                    String providerId,
                                    String displayName,
                                    String description,
                                    String requiredCapability,
                                    boolean askSupported,
                                    boolean agentSupported) {
    }
}
