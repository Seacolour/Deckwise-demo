package com.xiaoyuan.paperpuppybackendintl.service.billing;

import com.xiaoyuan.paperpuppybackendintl.config.AiBillingProperties;
import com.xiaoyuan.paperpuppybackendintl.repository.AiUsageLedgerRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.AiUsageLedgerRepository.UsageLedgerEntry;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * AI usage 结算服务。
 *
 * <p>记录 backend-owned chat/agent usage，并保存结算时的 provider 价格快照。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageBillingService {

    private static final long ONE_MILLION = 1_000_000L;

    private final AiBillingProperties aiBillingProperties;
    private final AiUsageLedgerRepository aiUsageLedgerRepository;
    private final ModelCatalogService modelCatalogService;

    /**
     * 主聊天链路的 usage 记账。
     * <p>从 gRPC responseMeta 提取 model/tokens，按模型单价估算成本后写入账本。</p>
     */
    @Transactional
    public void recordConversationUsage(Long userId, Long sessionId,
                                         Long userMessageId,
                                         Map<String, String> responseMeta,
                                         List<Map<String, Object>> toolEventList) {
        if (userId == null || userMessageId == null || responseMeta == null || responseMeta.isEmpty()) {
            return;
        }

        String actualModel = responseMeta.get("model");
        String effectiveModel = firstNonBlank(actualModel, responseMeta.get("requested_model"));
        String serviceTier = firstNonBlank(responseMeta.get("service_tier"), responseMeta.get("requested_service_tier"));
        Optional<PricingSnapshot> pricingOpt = resolvePricing(effectiveModel, serviceTier);
        PricingSnapshot pricing = pricingOpt.orElse(null);

        int inputTokens = parseIntSafe(responseMeta.get("input_tokens"));
        int outputTokens = parseIntSafe(responseMeta.get("output_tokens"));
        int totalTokens = parseIntSafe(responseMeta.get("total_tokens"));
        int cachedTokens = parseIntSafe(responseMeta.get("cached_tokens"));
        if (totalTokens <= 0) {
            totalTokens = inputTokens + outputTokens;
        }
        if (cachedTokens > inputTokens) {
            cachedTokens = inputTokens;
        }

        int billableCachedTokens = pricing != null && pricing.cachedInputRateMicrosPerMillion() != null
                ? cachedTokens
                : 0;
        int billableInputTokens = Math.max(0, inputTokens - billableCachedTokens);
        int billableOutputTokens = outputTokens;

        Long estimatedInputCostMicros = pricing == null
                ? null
                : estimateCostMicros(billableInputTokens, pricing.inputRateMicrosPerMillion());
        Long estimatedCachedCostMicros = pricing == null || pricing.cachedInputRateMicrosPerMillion() == null
                ? null
                : estimateCostMicros(billableCachedTokens, pricing.cachedInputRateMicrosPerMillion());
        Long estimatedOutputCostMicros = pricing == null
                ? null
                : estimateCostMicros(billableOutputTokens, pricing.outputRateMicrosPerMillion());
        Long estimatedTotalCostMicros = sumMicros(
                estimatedInputCostMicros,
                estimatedCachedCostMicros,
                estimatedOutputCostMicros
        );

        UsageLedgerEntry entry = new UsageLedgerEntry();
        entry.setUsageKey("chat:" + userMessageId);
        entry.setUsageSource("chat_conversation");
        entry.setUserId(userId);
        entry.setSessionId(sessionId);
        entry.setReportStatus("reported_success");
        entry.setSettlementKey("chat_conversation");
        entry.setProvider(firstNonBlank(responseMeta.get("provider"), aiBillingProperties.getProvider()));
        entry.setActualModel(effectiveModel);
        entry.setRequestedModel(responseMeta.get("requested_model"));
        entry.setSuccess(true);
        entry.setInputTokens(inputTokens);
        entry.setOutputTokens(outputTokens);
        entry.setCachedTokens(cachedTokens);
        entry.setReasoningTokens(parseIntSafe(responseMeta.get("reasoning_tokens")));
        entry.setTotalTokens(totalTokens);
        entry.setBillableInputTokens(billableInputTokens);
        entry.setBillableCachedTokens(billableCachedTokens);
        entry.setBillableOutputTokens(billableOutputTokens);
        entry.setCurrency(pricing == null ? null : pricing.currency());
        entry.setInputRateMicrosPerMillion(pricing == null ? null : pricing.inputRateMicrosPerMillion());
        entry.setCachedInputRateMicrosPerMillion(pricing == null ? null : pricing.cachedInputRateMicrosPerMillion());
        entry.setOutputRateMicrosPerMillion(pricing == null ? null : pricing.outputRateMicrosPerMillion());
        entry.setEstimatedInputCostMicros(estimatedInputCostMicros);
        entry.setEstimatedCachedCostMicros(estimatedCachedCostMicros);
        entry.setEstimatedOutputCostMicros(estimatedOutputCostMicros);
        entry.setEstimatedTotalCostMicros(estimatedTotalCostMicros);
        entry.setSettledAt(OffsetDateTime.now(ZoneOffset.UTC));

        aiUsageLedgerRepository.upsert(entry);

        log.info("Chat usage settled into ledger: userId={}, sessionId={}, msgId={}, model={}",
                userId, sessionId, userMessageId, effectiveModel);
    }

    private Optional<PricingSnapshot> resolvePricing(String model, String serviceTier) {
        if (!aiBillingProperties.isEnabled() || !StringUtils.hasText(model)) {
            return Optional.empty();
        }
        ModelCatalogService.ServiceTierPricing serviceTierPricing =
                modelCatalogService.serviceTierPricing(serviceTier);
        BigDecimal tokenCostMultiplier = serviceTierPricing == null
                ? BigDecimal.ONE
                : positiveMultiplier(serviceTierPricing.tokenCostMultiplier());
        return modelCatalogService.tokenPricing(model.trim())
                .map(pricing -> new PricingSnapshot(
                        normalizeCurrency(firstNonBlank(pricing.currency(), aiBillingProperties.getCurrency())),
                        toMicrosPerMillion(pricing.inputPerMillion().multiply(tokenCostMultiplier)),
                        pricing.cachedInputPerMillion() == null
                                ? null
                                : toMicrosPerMillion(pricing.cachedInputPerMillion().multiply(tokenCostMultiplier)),
                        toMicrosPerMillion(pricing.outputPerMillion().multiply(tokenCostMultiplier))
                ));
    }

    private static long toMicrosPerMillion(BigDecimal amountPerMillion) {
        return amountPerMillion
                .movePointRight(6)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static BigDecimal positiveMultiplier(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

    private static Long estimateCostMicros(int tokens, Long rateMicrosPerMillion) {
        if (tokens <= 0 || rateMicrosPerMillion == null || rateMicrosPerMillion <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(tokens)
                .multiply(BigDecimal.valueOf(rateMicrosPerMillion))
                .divide(BigDecimal.valueOf(ONE_MILLION), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static Long sumMicros(Long... costs) {
        long total = 0L;
        boolean hasValue = false;
        if (costs != null) {
            for (Long cost : costs) {
                if (cost == null) {
                    continue;
                }
                total += cost;
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }

    private static int parseIntSafe(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(value.trim()), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            return "USD";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private record PricingSnapshot(String currency,
                                   Long inputRateMicrosPerMillion,
                                   Long cachedInputRateMicrosPerMillion,
                                   Long outputRateMicrosPerMillion) {
    }
}
