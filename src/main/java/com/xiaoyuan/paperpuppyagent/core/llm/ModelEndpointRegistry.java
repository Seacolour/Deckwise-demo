package com.xiaoyuan.paperpuppyagent.core.llm;

import com.xiaoyuan.paperpuppyagent.config.OpenAiProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Resolves trusted provider endpoints. Explicit BYOK routing never falls back to service credentials. */
@Component
public class ModelEndpointRegistry {
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
    private static final Set<String> DEEPSEEK_MODELS = Set.of("deepseek-flash", "deepseek-v4-pro");

    private final OpenAiProperties openAiProperties;
    private final String deepSeekApiKey;

    public ModelEndpointRegistry(OpenAiProperties openAiProperties,
                                 @Value("${deepseek.api-key:}") String deepSeekApiKey) {
        this.openAiProperties = openAiProperties;
        this.deepSeekApiKey = deepSeekApiKey;
    }

    public ModelRequestContext resolve(String requestedModel, String providerOverride, String apiKeyOverride) {
        String explicitProvider = normalized(providerOverride);
        String model = Objects.toString(requestedModel, "").trim();
        String loweredModel = model.toLowerCase(Locale.ROOT);
        String inferredProvider = loweredModel.startsWith("openrouter:") ? "openrouter"
                : loweredModel.startsWith("deepseek:") || loweredModel.startsWith("deepseek-")
                ? "deepseek" : "openai";
        String provider = explicitProvider.isBlank() ? inferredProvider : explicitProvider;
        if (!"openai".equals(provider) && !"deepseek".equals(provider) && !"openrouter".equals(provider)) {
            throw new IllegalArgumentException("Unsupported model provider: " + provider);
        }

        int separator = model.indexOf(':');
        if (separator > 0) {
            String prefix = normalized(model.substring(0, separator));
            if (!prefix.equals(provider)) {
                throw new IllegalArgumentException("Model provider does not match the selected provider");
            }
            model = model.substring(separator + 1).trim();
        }
        if (model.isBlank()) {
            if ("openai".equals(provider) && explicitProvider.isBlank()) {
                model = Objects.toString(openAiProperties.model(), "").trim();
            }
            if (model.isBlank()) {
                throw new IllegalArgumentException("Model is required for provider " + provider);
            }
        }
        if ("openrouter".equals(provider) && explicitProvider.isBlank()) {
            throw new IllegalArgumentException("OpenRouter requires an explicit provider and user credential");
        }
        if ("openrouter".equals(provider) && (model.indexOf('/') <= 0 || model.endsWith("/"))) {
            throw new IllegalArgumentException("OpenRouter model must use a provider-qualified model ID");
        }
        if ("deepseek".equals(provider) && !DEEPSEEK_MODELS.contains(model)) {
            throw new IllegalArgumentException("Unsupported DeepSeek model: " + model);
        }
        if ("openai".equals(provider) && DEEPSEEK_MODELS.contains(model)) {
            throw new IllegalArgumentException("Model provider does not match the selected provider");
        }

        String key = explicitProvider.isBlank()
                ? ("deepseek".equals(provider) ? deepSeekApiKey : openAiProperties.apiKey())
                : apiKeyOverride;
        key = Objects.toString(key, "").trim();
        if (key.isBlank()) {
            throw new IllegalStateException(explicitProvider.isBlank()
                    ? ("deepseek".equals(provider) ? "DEEPSEEK_API_KEY is blank" : "OPENAI_API_KEY is blank")
                    : "No API key supplied for the selected model provider");
        }

        ModelEndpoint endpoint = switch (provider) {
            case "deepseek" -> new ModelEndpoint("deepseek", DEEPSEEK_BASE_URL,
                    true, false, false, false, false);
            case "openrouter" -> new ModelEndpoint("openrouter", OPENROUTER_BASE_URL,
                    true, true, false, false, false);
            default -> new ModelEndpoint("openai", normalizeOpenAiBaseUrl(openAiProperties.baseUrl()),
                    true, true, true, true, true);
        };
        return new ModelRequestContext(endpoint, model, key,
                "openai".equals(provider) && explicitProvider.isBlank());
    }

    public void validateRequest(ModelRequestContext context, boolean enableTools,
                                int imageCount, int fileCount) {
        ModelEndpoint endpoint = context.endpoint();
        if (enableTools && !endpoint.supportsTools()) {
            throw new IllegalArgumentException("Selected model provider does not support Agent tools");
        }
        if (imageCount > 0 && !endpoint.supportsImages()) {
            throw new IllegalArgumentException("Selected model provider does not support image inputs");
        }
        if (fileCount > 0 && !endpoint.supportsFiles()) {
            throw new IllegalArgumentException("Selected model provider does not support direct file inputs");
        }
    }

    private static String normalized(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeOpenAiBaseUrl(String baseUrl) {
        String url = Objects.toString(baseUrl, "").trim();
        if (url.isBlank()) return "https://api.openai.com/v1";
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.endsWith("/v1") ? url : url + "/v1";
    }
}
