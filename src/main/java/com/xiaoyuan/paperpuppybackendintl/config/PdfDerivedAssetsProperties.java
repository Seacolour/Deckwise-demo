package com.xiaoyuan.paperpuppybackendintl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pdf-derived-assets")
public record PdfDerivedAssetsProperties(
        boolean enabled,
        String extractorMode,
        String extractorBaseUrl,
        int timeoutMs,
        int dpi,
        int maxPages,
        int maxAssets,
        boolean renderPages,
        boolean extractEmbeddedImages,
        boolean cropFigures,
        String imageDetectionProvider,
        boolean enableDocumentAi,
        int documentAiMaxPages,
        boolean documentAiReturnImages,
        boolean documentAiReturnBoundingBoxes,
        boolean documentAiEnableImageAnnotation,
        boolean documentAiIncludeTables,
        double documentAiCropMargin,
        double documentAiMinPageAreaRatio,
        int openAiMaxPages,
        String openAiModel,
        double openAiTemperature,
        double openAiTopP,
        int openAiMaxOutputTokens,
        String openAiReasoningEffort,
        String openAiTextVerbosity,
        int openAiDetectionRenderDpi,
        int openAiRenderDpi,
        double openAiCropMargin,
        double openAiMinPageAreaRatio,
        int minEmbeddedWidth,
        int minEmbeddedHeight,
        long minEmbeddedFileSizeBytes,
        double minEmbeddedPageAreaRatio,
        boolean deduplicateEmbeddedImages,
        boolean aiImageTransformEnabled,
        String aiImageTransformBaseUrl,
        String aiImageTransformPreset,
        int aiImageTransformWidth,
        int aiImageTransformQuality,
        String aiImageTransformFormat
) {

    public PdfDerivedAssetsProperties {
        extractorMode = normalizeExtractorMode(extractorMode);
        extractorBaseUrl = normalizeBaseUrl(extractorBaseUrl);
        timeoutMs = timeoutMs <= 0 ? 120_000 : timeoutMs;
        dpi = dpi <= 0 ? 144 : dpi;
        maxPages = maxPages <= 0 ? 20 : maxPages;
        maxAssets = maxAssets <= 0 ? 4 : Math.min(maxAssets, 4);
        imageDetectionProvider = imageDetectionProvider == null ? "" : imageDetectionProvider.trim();
        documentAiMaxPages = documentAiMaxPages <= 0 ? 5 : documentAiMaxPages;
        documentAiCropMargin = documentAiCropMargin < 0 ? 0 : documentAiCropMargin;
        documentAiMinPageAreaRatio = documentAiMinPageAreaRatio < 0 ? 0.005 : documentAiMinPageAreaRatio;
        openAiMaxPages = openAiMaxPages <= 0 ? 4 : Math.min(openAiMaxPages, 4);
        openAiModel = openAiModel == null ? "" : openAiModel.trim();
        openAiTemperature = normalizeOptionalDouble(openAiTemperature, 0, 2);
        openAiTopP = normalizeOptionalDouble(openAiTopP, 0, 1);
        openAiMaxOutputTokens = Math.max(0, openAiMaxOutputTokens);
        openAiReasoningEffort = normalizeEnumLike(openAiReasoningEffort);
        openAiTextVerbosity = normalizeEnumLike(openAiTextVerbosity);
        openAiDetectionRenderDpi = openAiDetectionRenderDpi <= 0 ? 144 : openAiDetectionRenderDpi;
        openAiRenderDpi = openAiRenderDpi <= 0 ? dpi : openAiRenderDpi;
        openAiCropMargin = openAiCropMargin < 0 ? 0 : openAiCropMargin;
        openAiMinPageAreaRatio = openAiMinPageAreaRatio < 0 ? 0.005 : openAiMinPageAreaRatio;
        minEmbeddedWidth = Math.max(1, minEmbeddedWidth);
        minEmbeddedHeight = Math.max(1, minEmbeddedHeight);
        minEmbeddedFileSizeBytes = Math.max(0, minEmbeddedFileSizeBytes);
        minEmbeddedPageAreaRatio = Math.max(0, minEmbeddedPageAreaRatio);
        aiImageTransformBaseUrl = normalizeBaseUrl(aiImageTransformBaseUrl);
        aiImageTransformPreset = aiImageTransformPreset == null || aiImageTransformPreset.isBlank()
                ? "ai-outline"
                : aiImageTransformPreset.trim();
        aiImageTransformWidth = aiImageTransformWidth <= 0 ? 1600 : aiImageTransformWidth;
        aiImageTransformQuality = aiImageTransformQuality <= 0 ? 85 : Math.min(aiImageTransformQuality, 100);
        aiImageTransformFormat = normalizeTransformFormat(aiImageTransformFormat);
    }

    public boolean useLocalExtractor() {
        return "local".equals(extractorMode);
    }

    private static String normalizeExtractorMode(String value) {
        if (value == null || value.isBlank()) {
            return "remote";
        }
        String mode = value.trim().toLowerCase();
        return switch (mode) {
            case "local", "backend", "in-process", "inprocess" -> "local";
            default -> "remote";
        };
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private static double normalizeOptionalDouble(double value, double min, double max) {
        if (Double.isNaN(value) || value < 0) {
            return -1;
        }
        return Math.min(Math.max(value, min), max);
    }

    private static String normalizeEnumLike(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private static String normalizeTransformFormat(String value) {
        if (value == null || value.isBlank()) {
            return "jpeg";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "jpg" -> "jpeg";
            case "jpeg", "webp", "avif", "png", "auto" -> normalized;
            default -> "jpeg";
        };
    }
}
