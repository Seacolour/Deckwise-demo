package com.xiaoyuan.paperpuppybackendintl.service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.ModelCatalogProperties;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.enums.ConversationMode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelCatalogServiceTest {

    @Test
    void loadsModelCatalogFromClasspathResource() {
        ModelCatalogService service = new ModelCatalogService(
                sampleProperties(),
                new DefaultResourceLoader(),
                new ObjectMapper()
        );

        assertEquals("deepseek/deepseek-flash", service.defaultModel());
        assertTrue(service.findEnabledModel("gpt-5.6-terra").isPresent());
        assertTrue(service.findEnabledModel("gpt-5.6-sol").isPresent());
        assertEquals("deepseek", service.findEnabledModel("deepseek/deepseek-flash").orElseThrow().providerId());
        assertEquals("deepseek-flash", service.findEnabledModel("deepseek/deepseek-flash").orElseThrow().upstreamModelId());
        assertTrue(service.findEnabledModel("deepseek/deepseek-flash").orElseThrow().agentSupported());
        var routedOpenAi = service.findEnabledModel("openrouter/openai/gpt-6.1-terra").orElseThrow();
        assertEquals("openrouter", routedOpenAi.providerId());
        assertEquals("openai/gpt-6.1-terra", routedOpenAi.upstreamModelId());
        assertTrue(service.findEnabledModel("openrouter/openai/gpt-6-luna").isPresent());
        assertNull(service.findEnabledModel("gpt-5.6-terra").orElseThrow().requiredCapability());
        assertNull(service.findEnabledModel("gpt-5.6-sol").orElseThrow().requiredCapability());

        ModelCatalogService.TokenPricing tokenPricing = service.tokenPricing("gpt-5.6-sol").orElseThrow();
        assertEquals("USD", tokenPricing.currency());
        assertEquals(0, new BigDecimal("5.00").compareTo(tokenPricing.inputPerMillion()));
        assertEquals(0, new BigDecimal("0.50").compareTo(tokenPricing.cachedInputPerMillion()));
        assertEquals(0, new BigDecimal("30.00").compareTo(tokenPricing.outputPerMillion()));

        ModelCatalogService.ServiceTierPricing priorityPricing = service.serviceTierPricing("priority");
        assertEquals("priority", priorityPricing.serviceTier());
        assertEquals(0, new BigDecimal("2.0").compareTo(priorityPricing.tokenCostMultiplier()));
        assertEquals("priority", service.serviceTierPricing("fast").serviceTier());
    }

    @Test
    void listsAllEnabledModelsWithoutSubscriptionFilteringOrCreditPrices() throws Exception {
        ModelCatalogService service = new ModelCatalogService(
                sampleProperties(),
                new DefaultResourceLoader(),
                new ObjectMapper()
        );
        ModelResolutionService resolutionService = new ModelResolutionService(service, mock(ModelCredentialService.class));

        var models = resolutionService.getAvailableModels();

        assertEquals(6, models.size());
        assertTrue(models.stream().anyMatch(model -> "openai/gpt-5.6-terra".equals(model.id())));
        assertTrue(models.stream().anyMatch(model -> "deepseek/deepseek-flash".equals(model.id())));
        assertTrue(models.stream().anyMatch(model -> "openrouter/openai/gpt-6.1-terra".equals(model.id())));
        assertTrue(models.stream().anyMatch(model -> "openrouter/openai/gpt-6-luna".equals(model.id())));
        assertTrue(models.stream().allMatch(model -> model.requiredCapability() == null));
        String publicJson = new ObjectMapper().writeValueAsString(models);
        assertTrue(!publicJson.toLowerCase().contains("credit"));
    }

    @Test
    void resolvesBlankRequestToDefaultModel() {
        ModelResolutionService resolutionService = new ModelResolutionService(
                new ModelCatalogService(sampleProperties(), new DefaultResourceLoader(), new ObjectMapper()),
                mock(ModelCredentialService.class));

        assertEquals("deepseek/deepseek-flash", resolutionService.resolve(null));
        assertEquals("deepseek/deepseek-flash", resolutionService.resolve(" "));
    }

    @Test
    void rejectsUnsupportedModelInsteadOfFallingBackSilently() {
        ModelResolutionService resolutionService = new ModelResolutionService(
                new ModelCatalogService(sampleProperties(), new DefaultResourceLoader(), new ObjectMapper()),
                mock(ModelCredentialService.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> resolutionService.resolve("gpt-unknown")
        );

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("Unsupported model"));
    }

    @Test
    void userAwareResolutionDoesNotRequirePaidModelCapability() {
        ModelCatalogService catalogService = new ModelCatalogService(
                sampleProperties(),
                new DefaultResourceLoader(),
                new ObjectMapper()
        );
        ModelCredentialService credentials = mock(ModelCredentialService.class);
        when(credentials.hasCredential(7L, "openai")).thenReturn(true);
        ModelResolutionService resolutionService = new ModelResolutionService(catalogService, credentials);

        assertEquals("openai/gpt-5.6-terra", resolutionService.resolveForUser(7L, "gpt-5.6-terra"));
        assertEquals("openai/gpt-5.6-sol", resolutionService.resolveForUser(7L, "gpt-5.6-sol"));
        assertEquals(2, resolutionService.getAvailableModels(7L).size());
        assertEquals("openai/gpt-5.6-terra", resolutionService.resolveForUser(7L, null));
        assertThrows(BusinessException.class,
                () -> resolutionService.resolveForUser(7L, "deepseek/deepseek-flash", ConversationMode.AGENT));
    }

    @Test
    void deepSeekCredentialEnablesAgentModeWithoutInventedPricing() {
        ModelCatalogService catalog = new ModelCatalogService(
                sampleProperties(), new DefaultResourceLoader(), new ObjectMapper());
        ModelCredentialService credentials = mock(ModelCredentialService.class);
        when(credentials.hasCredential(7L, "deepseek")).thenReturn(true);
        ModelResolutionService resolution = new ModelResolutionService(catalog, credentials);

        assertEquals("deepseek/deepseek-flash", resolution.resolveForUser(7L, null));
        assertEquals(2, resolution.getAvailableModels(7L).size());
        assertTrue(resolution.getAvailableModels(7L).stream().allMatch(model -> model.agentSupported()));
        assertTrue(catalog.tokenPricing("deepseek/deepseek-flash").isEmpty());
        assertEquals("deepseek/deepseek-flash",
                resolution.resolveForUser(7L, "deepseek/deepseek-flash", ConversationMode.AGENT));
    }

    @Test
    void openRouterCredentialUnlocksItsModelsForAgentWithoutASeparateOpenAiKey() {
        ModelCatalogService catalog = new ModelCatalogService(
                sampleProperties(), new DefaultResourceLoader(), new ObjectMapper());
        ModelCredentialService credentials = mock(ModelCredentialService.class);
        when(credentials.hasCredential(7L, "openrouter")).thenReturn(true);
        ModelResolutionService resolution = new ModelResolutionService(catalog, credentials);

        assertEquals(2, resolution.getAvailableModels(7L).size());
        assertEquals("openrouter/openai/gpt-6.1-terra",
                resolution.resolveForUser(7L, "openrouter/openai/gpt-6.1-terra", ConversationMode.AGENT));
        assertTrue(catalog.tokenPricing("openrouter/openai/gpt-6.1-terra").isEmpty());
    }

    private static ModelCatalogProperties sampleProperties() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setEnabled(true);
        properties.setLocation("classpath:model-catalog.json");
        return properties;
    }
}
