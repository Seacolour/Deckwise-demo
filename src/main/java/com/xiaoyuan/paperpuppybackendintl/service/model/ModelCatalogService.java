package com.xiaoyuan.paperpuppybackendintl.service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.ModelCatalogProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelCatalogService {

    private static final String FALLBACK_DEFAULT_MODEL = "deepseek/deepseek-flash";
    private static final BigDecimal DEFAULT_MULTIPLIER = BigDecimal.ONE;

    private final ModelCatalogProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private volatile CatalogSnapshot cachedSnapshot;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public String defaultModel() {
        String model = snapshot().catalog().getDefaultModel();
        return StringUtils.hasText(model) ? model.trim() : FALLBACK_DEFAULT_MODEL;
    }

    public List<ModelDefinition> listEnabledModels() {
        return snapshot().orderedModels().stream()
                .filter(ModelDefinition::enabled)
                .toList();
    }

    public Optional<ModelDefinition> findEnabledModel(String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return Optional.empty();
        }
        ModelDefinition model = snapshot().modelById().get(normalizeModelId(modelId));
        if (model == null && !modelId.contains("/")) {
            // Preserve unambiguous older clients. A duplicated upstream name
            // must be addressed by its provider-qualified catalog ID.
            List<ModelDefinition> matches = snapshot().orderedModels().stream()
                    .filter(candidate -> candidate.upstreamModelId().equalsIgnoreCase(modelId.trim()))
                    .limit(2).toList();
            model = matches.size() == 1 ? matches.getFirst() : null;
        }
        if (model == null || !model.enabled()) {
            return Optional.empty();
        }
        return Optional.of(model);
    }

    public Optional<TokenPricing> tokenPricing(String modelId) {
        return findEnabledModel(modelId)
                .or(() -> findEnabledModel(defaultModel()))
                .map(ModelDefinition::tokenPricing)
                .filter(TokenPricing::hasRequiredRates);
    }

    public ServiceTierPricing serviceTierPricing(String serviceTier) {
        String normalizedTier = normalizeServiceTier(serviceTier);
        if ("default".equals(normalizedTier)) {
            return new ServiceTierPricing("default", DEFAULT_MULTIPLIER);
        }
        ServiceTierSpec spec = null;
        Map<String, ServiceTierSpec> configured = snapshot().catalog().getServiceTiers();
        if (configured != null) {
            for (Map.Entry<String, ServiceTierSpec> entry : configured.entrySet()) {
                if (normalizedTier.equals(normalizeServiceTier(entry.getKey()))) {
                    spec = entry.getValue();
                    break;
                }
            }
        }
        if (spec == null) {
            return new ServiceTierPricing(normalizedTier, DEFAULT_MULTIPLIER);
        }
        return new ServiceTierPricing(
                normalizedTier,
                positiveMultiplier(spec.getTokenCostMultiplier())
        );
    }

    public void evictCache() {
        cachedSnapshot = null;
    }

    private CatalogSnapshot snapshot() {
        CatalogSnapshot local = cachedSnapshot;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cachedSnapshot == null) {
                cachedSnapshot = loadSnapshot();
            }
            return cachedSnapshot;
        }
    }

    private CatalogSnapshot loadSnapshot() {
        CatalogSpec catalog = readCatalog();
        Map<String, ModelDefinition> byId = new LinkedHashMap<>();
        List<ModelDefinition> ordered = new ArrayList<>();
        if (catalog.getModels() != null) {
            for (ModelSpec spec : catalog.getModels()) {
                if (spec == null || !StringUtils.hasText(spec.getId())) {
                    continue;
                }
                ModelDefinition model = toModelDefinition(spec);
                byId.put(normalizeModelId(model.id()), model);
                ordered.add(model);
            }
        }
        log.info("[ModelCatalog] loaded models: count={}, location={}", ordered.size(), properties.getLocation());
        return new CatalogSnapshot(catalog, List.copyOf(ordered), Map.copyOf(byId));
    }

    private CatalogSpec readCatalog() {
        Resource resource = resourceLoader.getResource(properties.getLocation());
        try (var input = resource.getInputStream()) {
            CatalogSpec catalog = objectMapper.readValue(input, CatalogSpec.class);
            return catalog == null ? new CatalogSpec() : catalog;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read model catalog: " + properties.getLocation(), e);
        }
    }

    private ModelDefinition toModelDefinition(ModelSpec spec) {
        TokenPricingSpec tokenPricing = spec.getTokenPricing() == null ? new TokenPricingSpec() : spec.getTokenPricing();
        return new ModelDefinition(
                spec.getId().trim(),
                StringUtils.hasText(spec.getProviderId()) ? spec.getProviderId().trim().toLowerCase(Locale.ROOT) : "openai",
                StringUtils.hasText(spec.getUpstreamModelId()) ? spec.getUpstreamModelId().trim() : spec.getId().trim(),
                StringUtils.hasText(spec.getApiFormat()) ? spec.getApiFormat().trim() : "openai-responses",
                !Boolean.FALSE.equals(spec.getAskSupported()),
                !Boolean.FALSE.equals(spec.getAgentSupported()),
                trimToNull(spec.getDisplayName()),
                trimToNull(spec.getDescription()),
                !Boolean.FALSE.equals(spec.getEnabled()),
                trimToNull(spec.getRequiredCapability()),
                new TokenPricing(
                        trimToNull(tokenPricing.getCurrency()),
                        tokenPricing.getInputPerMillion(),
                        tokenPricing.getCachedInputPerMillion(),
                        tokenPricing.getOutputPerMillion()
                )
        );
    }

    private static String normalizeModelId(String modelId) {
        return modelId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeServiceTier(String serviceTier) {
        String normalized = serviceTier == null ? "" : serviceTier.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "default", "standard", "auto" -> "default";
            case "fast" -> "priority";
            default -> normalized;
        };
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BigDecimal positiveMultiplier(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_MULTIPLIER;
        }
        return value;
    }

    public record ModelDefinition(String id,
                                  String providerId,
                                  String upstreamModelId,
                                  String apiFormat,
                                  boolean askSupported,
                                  boolean agentSupported,
                                  String displayName,
                                  String description,
                                  boolean enabled,
                                  String requiredCapability,
                                  TokenPricing tokenPricing) {
    }

    public record TokenPricing(String currency,
                               BigDecimal inputPerMillion,
                               BigDecimal cachedInputPerMillion,
                               BigDecimal outputPerMillion) {
        boolean hasRequiredRates() {
            return inputPerMillion != null && outputPerMillion != null;
        }
    }

    public record ServiceTierPricing(String serviceTier,
                                     BigDecimal tokenCostMultiplier) {
    }

    private record CatalogSnapshot(CatalogSpec catalog,
                                   List<ModelDefinition> orderedModels,
                                   Map<String, ModelDefinition> modelById) {
    }

    @Data
    private static class CatalogSpec {
        private String defaultModel = FALLBACK_DEFAULT_MODEL;
        private Map<String, ServiceTierSpec> serviceTiers = new LinkedHashMap<>();
        private List<ModelSpec> models = new ArrayList<>();
    }

    @Data
    private static class ModelSpec {
        private String id;
        private String providerId;
        private String upstreamModelId;
        private String apiFormat;
        private Boolean askSupported = true;
        private Boolean agentSupported = true;
        private String displayName;
        private String description;
        private Boolean enabled = true;
        private String requiredCapability;
        private TokenPricingSpec tokenPricing = new TokenPricingSpec();
    }

    @Data
    private static class TokenPricingSpec {
        private String currency;
        private BigDecimal inputPerMillion;
        private BigDecimal cachedInputPerMillion;
        private BigDecimal outputPerMillion;
    }

    @Data
    private static class ServiceTierSpec {
        private BigDecimal tokenCostMultiplier;
    }
}
