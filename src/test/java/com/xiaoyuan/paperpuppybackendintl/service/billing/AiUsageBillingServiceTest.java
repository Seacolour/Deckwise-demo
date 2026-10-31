package com.xiaoyuan.paperpuppybackendintl.service.billing;

import com.xiaoyuan.paperpuppybackendintl.config.AiBillingProperties;
import com.xiaoyuan.paperpuppybackendintl.repository.AiUsageLedgerRepository;
import com.xiaoyuan.paperpuppybackendintl.service.model.ModelCatalogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiUsageBillingServiceTest {

    @Test
    void recordConversationUsageWritesLedgerFromCatalogPricing() {
        AiUsageLedgerRepository repository = mock(AiUsageLedgerRepository.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        when(modelCatalogService.tokenPricing("gpt-test")).thenReturn(Optional.of(sampleTokenPricing()));
        when(modelCatalogService.serviceTierPricing(nullable(String.class))).thenReturn(standardTierPricing());
        AiUsageBillingService service = new AiUsageBillingService(sampleProps(), repository, modelCatalogService);

        service.recordConversationUsage(
                7L,
                11L,
                101L,
                Map.of(
                        "requested_model", "gpt-test",
                        "model", "gpt-test",
                        "input_tokens", "1000",
                        "cached_tokens", "200",
                        "output_tokens", "500",
                        "total_tokens", "1500"
                ),
                List.of()
        );

        ArgumentCaptor<AiUsageLedgerRepository.UsageLedgerEntry> captor =
                ArgumentCaptor.forClass(AiUsageLedgerRepository.UsageLedgerEntry.class);
        verify(repository).upsert(captor.capture());

        AiUsageLedgerRepository.UsageLedgerEntry entry = captor.getValue();
        assertEquals("chat:101", entry.getUsageKey());
        assertEquals("chat_conversation", entry.getUsageSource());
        assertEquals("reported_success", entry.getReportStatus());
        assertEquals("chat_conversation", entry.getSettlementKey());
        assertEquals(800, entry.getBillableInputTokens());
        assertEquals(200, entry.getBillableCachedTokens());
        assertEquals(500, entry.getBillableOutputTokens());
        assertEquals(1900L, entry.getEstimatedTotalCostMicros());
    }

    @Test
    void recordConversationUsageAppliesPriorityTokenPricingMultiplier() {
        AiUsageLedgerRepository repository = mock(AiUsageLedgerRepository.class);
        ModelCatalogService modelCatalogService = mock(ModelCatalogService.class);
        when(modelCatalogService.tokenPricing("gpt-test")).thenReturn(Optional.of(sampleTokenPricing()));
        when(modelCatalogService.serviceTierPricing(eq("priority"))).thenReturn(priorityTierPricing());
        AiUsageBillingService service = new AiUsageBillingService(sampleProps(), repository, modelCatalogService);

        service.recordConversationUsage(
                7L,
                11L,
                101L,
                Map.of(
                        "requested_model", "gpt-test",
                        "model", "gpt-test",
                        "service_tier", "priority",
                        "input_tokens", "1000",
                        "cached_tokens", "200",
                        "output_tokens", "500",
                        "total_tokens", "1500"
                ),
                List.of()
        );

        ArgumentCaptor<AiUsageLedgerRepository.UsageLedgerEntry> captor =
                ArgumentCaptor.forClass(AiUsageLedgerRepository.UsageLedgerEntry.class);
        verify(repository).upsert(captor.capture());

        AiUsageLedgerRepository.UsageLedgerEntry entry = captor.getValue();
        assertEquals(2_000_000L, entry.getInputRateMicrosPerMillion());
        assertEquals(1_000_000L, entry.getCachedInputRateMicrosPerMillion());
        assertEquals(4_000_000L, entry.getOutputRateMicrosPerMillion());
        assertEquals(3800L, entry.getEstimatedTotalCostMicros());
    }

    @Test
    void recordsDeepSeekProviderAndTokensWithoutInventingAPrice() {
        AiUsageLedgerRepository repository = mock(AiUsageLedgerRepository.class);
        ModelCatalogService catalog = mock(ModelCatalogService.class);
        when(catalog.tokenPricing("deepseek-flash")).thenReturn(Optional.empty());
        AiUsageBillingService service = new AiUsageBillingService(sampleProps(), repository, catalog);

        service.recordConversationUsage(7L, 11L, 101L, Map.of(
                "provider", "deepseek",
                "requested_model", "deepseek/deepseek-flash",
                "model", "deepseek-flash",
                "input_tokens", "100",
                "output_tokens", "20"
        ), List.of());

        ArgumentCaptor<AiUsageLedgerRepository.UsageLedgerEntry> captor =
                ArgumentCaptor.forClass(AiUsageLedgerRepository.UsageLedgerEntry.class);
        verify(repository).upsert(captor.capture());
        assertEquals("deepseek", captor.getValue().getProvider());
        assertEquals(100, captor.getValue().getInputTokens());
        assertEquals(20, captor.getValue().getOutputTokens());
        assertNull(captor.getValue().getEstimatedTotalCostMicros());
    }

    private AiBillingProperties sampleProps() {
        AiBillingProperties props = new AiBillingProperties();
        props.setEnabled(true);
        props.setCurrency("USD");
        props.setProvider("openai");
        return props;
    }

    private ModelCatalogService.TokenPricing sampleTokenPricing() {
        return new ModelCatalogService.TokenPricing(
                "USD",
                new BigDecimal("1.0"),
                new BigDecimal("0.5"),
                new BigDecimal("2.0")
        );
    }

    private ModelCatalogService.ServiceTierPricing standardTierPricing() {
        return new ModelCatalogService.ServiceTierPricing("default", BigDecimal.ONE);
    }

    private ModelCatalogService.ServiceTierPricing priorityTierPricing() {
        return new ModelCatalogService.ServiceTierPricing(
                "priority",
                new BigDecimal("2.0")
        );
    }
}
