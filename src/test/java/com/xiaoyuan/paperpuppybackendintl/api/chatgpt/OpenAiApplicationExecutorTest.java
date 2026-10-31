package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import com.openai.models.responses.WebSearchTool;
import com.openai.models.responses.ResponseInputImage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiApplicationExecutorTest {

    @Test
    void buildToolsAddsWebSearchWhenEnabled() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.4-mini")
                .input("Build a deck outline with current market context.")
                .webSearchEnabled(true)
                .webSearchContextSize("high")
                .build();

        var tools = OpenAiApplicationExecutor.buildTools(param);

        assertEquals(1, tools.size());
        assertTrue(tools.get(0).isWebSearch());
        assertEquals(WebSearchTool.SearchContextSize.HIGH,
                tools.get(0).asWebSearch().searchContextSize().orElseThrow());
    }

    @Test
    void buildToolsDefaultsWebSearchContextSizeToMedium() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.4-mini")
                .input("Build a deck outline.")
                .webSearchEnabled(true)
                .webSearchContextSize("unexpected")
                .build();

        var tools = OpenAiApplicationExecutor.buildTools(param);

        assertEquals(WebSearchTool.SearchContextSize.MEDIUM,
                tools.get(0).asWebSearch().searchContextSize().orElseThrow());
    }

    @Test
    void buildToolsOmitsWebSearchByDefault() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.4-mini")
                .input("Build a title.")
                .build();

        assertTrue(OpenAiApplicationExecutor.buildTools(param).isEmpty());
    }

    @Test
    void reasoningEffortSupportIsLimitedToReasoningModels() {
        assertTrue(OpenAiApplicationExecutor.supportsReasoningEffort("gpt-5.4-mini"));
        assertTrue(OpenAiApplicationExecutor.supportsReasoningEffort("o4-mini"));
        assertTrue(OpenAiApplicationExecutor.supportsReasoningEffort("o3"));

        assertEquals(false, OpenAiApplicationExecutor.supportsReasoningEffort("gpt-4.1-mini"));
        assertEquals(false, OpenAiApplicationExecutor.supportsReasoningEffort("gpt-4o"));
    }

    @Test
    void buildCreateParamsConfiguresExplicitPromptCachingForGpt56() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.6-luna")
                .instructions("Plan slide layouts.")
                .cacheableInput("Stable catalog content")
                .input("Dynamic outline content")
                .promptCacheKey("slides-blueprint-v1")
                .explicitPromptCaching(true)
                .build();

        var request = OpenAiApplicationExecutor.buildCreateParams(
                param,
                param.model(),
                param.input(),
                param.instructions(),
                null
        );

        assertEquals("slides-blueprint-v1", request.promptCacheKey().orElseThrow());
        Map<?, ?> options = request._additionalBodyProperties()
                .get("prompt_cache_options")
                .convert(Map.class);
        assertEquals("explicit", options.get("mode"));
        assertEquals("30m", options.get("ttl"));

        var content = request.input().orElseThrow().asResponse().getFirst().asMessage().content();
        assertEquals(2, content.size());
        assertEquals("Stable catalog content", content.get(0).asInputText().text());
        assertEquals("Dynamic outline content", content.get(1).asInputText().text());
        Map<?, ?> breakpoint = content.get(0).asInputText()._additionalProperties()
                .get("prompt_cache_breakpoint")
                .convert(Map.class);
        assertEquals("explicit", breakpoint.get("mode"));
    }

    @Test
    void explicitPromptCachingFallsBackToAutomaticPrefixForOlderModels() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.4-mini")
                .cacheableInput("Stable catalog content")
                .input("Dynamic outline content")
                .promptCacheKey("slides-blueprint-v1")
                .explicitPromptCaching(true)
                .build();

        var request = OpenAiApplicationExecutor.buildCreateParams(
                param,
                param.model(),
                param.input(),
                "",
                null
        );

        assertEquals("slides-blueprint-v1", request.promptCacheKey().orElseThrow());
        assertFalse(request._additionalBodyProperties().containsKey("prompt_cache_options"));
        var firstText = request.input().orElseThrow().asResponse().getFirst().asMessage().content().getFirst().asInputText();
        assertFalse(firstText._additionalProperties().containsKey("prompt_cache_breakpoint"));
    }

    @Test
    void buildInputItemsAddsDedupedInputFiles() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.4-mini")
                .input("Build a deck outline.")
                .fileUrls(java.util.List.of(
                        " https://assets.example.com/brief.pdf ",
                        "",
                        "https://assets.example.com/brief.pdf",
                        "https://assets.example.com/data.xlsx"
                ))
                .build();

        var items = OpenAiApplicationExecutor.buildInputItems(param, param.input());

        assertEquals(1, items.size());
        var content = items.get(0).asMessage().content();
        assertEquals(3, content.size());
        assertTrue(content.get(0).isInputText());
        assertTrue(content.get(1).isInputFile());
        assertEquals("https://assets.example.com/brief.pdf",
                content.get(1).asInputFile().fileUrl().orElseThrow());
        assertEquals("https://assets.example.com/data.xlsx",
                content.get(2).asInputFile().fileUrl().orElseThrow());
    }

    @Test
    void buildInputItemsAddsDedupedInputImages() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.4-mini")
                .input("Build a deck outline from this screenshot.")
                .imageUrls(java.util.List.of(
                        " https://assets.example.com/screen.png ",
                        "",
                        "https://assets.example.com/screen.png",
                        "https://assets.example.com/chart.webp"
                ))
                .build();

        var items = OpenAiApplicationExecutor.buildInputItems(param, param.input());

        assertEquals(1, items.size());
        var content = items.get(0).asMessage().content();
        assertEquals(3, content.size());
        assertTrue(content.get(0).isInputText());
        assertTrue(content.get(1).isInputImage());
        assertEquals("https://assets.example.com/screen.png",
                content.get(1).asInputImage().imageUrl().orElseThrow());
        assertEquals(ResponseInputImage.Detail.AUTO,
                content.get(1).asInputImage().detail());
        assertEquals("https://assets.example.com/chart.webp",
                content.get(2).asInputImage().imageUrl().orElseThrow());
    }

    @Test
    void buildInputItemsPairsLabeledInputImagesWithTheirMetadata() {
        ApplicationParam param = ApplicationParam.builder()
                .model("gpt-5.4-mini")
                .input("Build a deck outline from these references.")
                .imageUrls(java.util.List.of("https://assets.example.com/fallback.png"))
                .inputImages(java.util.List.of(
                        new ApplicationInputImage(
                                " https://assets.example.com/chart.png ",
                                "Reference image reference-image-1 metadata:\nfileName: chart.png"
                        ),
                        new ApplicationInputImage(
                                "https://assets.example.com/chart.png",
                                "duplicate"
                        ),
                        new ApplicationInputImage(
                                "https://assets.example.com/photo.jpg",
                                "Reference image reference-image-2 metadata:\nfileName: photo.jpg"
                        )
                ))
                .build();

        var items = OpenAiApplicationExecutor.buildInputItems(param, param.input());

        assertEquals(1, items.size());
        var content = items.get(0).asMessage().content();
        assertEquals(5, content.size());
        assertTrue(content.get(0).isInputText());
        assertTrue(content.get(1).isInputText());
        assertTrue(content.get(2).isInputImage());
        assertTrue(content.get(3).isInputText());
        assertTrue(content.get(4).isInputImage());
        assertTrue(content.get(1).asInputText().text().contains("reference-image-1"));
        assertEquals("https://assets.example.com/chart.png",
                content.get(2).asInputImage().imageUrl().orElseThrow());
        assertTrue(content.get(3).asInputText().text().contains("reference-image-2"));
        assertEquals("https://assets.example.com/photo.jpg",
                content.get(4).asInputImage().imageUrl().orElseThrow());
    }
}
