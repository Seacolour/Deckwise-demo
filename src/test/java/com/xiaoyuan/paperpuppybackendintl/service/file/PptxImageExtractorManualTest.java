package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.JacksonConfig;
import com.xiaoyuan.paperpuppybackendintl.utils.file.PptxImageExtractor;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PptxImageExtractorManualTest {

    private static final String INPUT_PROPERTY = "pptx.image.extract.input";
    private static final String INPUT_ENV = "PPTX_IMAGE_EXTRACT_INPUT";
    private static final String OUTPUT_PROPERTY = "pptx.image.extract.output";
    private static final String OUTPUT_ENV = "PPTX_IMAGE_EXTRACT_OUTPUT";
    private static final Path DEFAULT_INPUT_PPTX = Path.of("D:/web/test/pptx/pptx-image-test.pptx");
    private static final Path DEFAULT_OUTPUT_ROOT = Path.of("target/manual-pptx-image-extraction");
    private static final ObjectMapper OBJECT_MAPPER = new JacksonConfig().jackson2Mapper();

    /**
     * Manual research entrypoint.
     *
     * <p>Run with either:
     * <pre>
     * ./mvnw -Dtest=PptxImageExtractorManualTest -Dpptx.image.extract.input=D:/tmp/sample.pptx test
     * </pre>
     * or set PPTX_IMAGE_EXTRACT_INPUT. The input can also be an HTTP(S) URL,
     * which makes sourceLoadMs represent the download time for that URL.
     */
    @Test
    void extractPptxImagesToLocalReport() throws Exception {
        long startedAt = System.nanoTime();
        String inputLocation = resolveInputLocation();
        Path outputDir = resolveOutputDirectory(inputLocation);
        Files.createDirectories(outputDir);

        TimedInput input = materializeInput(inputLocation, outputDir);
        assumeTrue(Files.isRegularFile(input.path()), "manual PPTX input file not found: " + inputLocation);

        long extractionStartedAt = System.nanoTime();
        PptxImageExtractor.ExtractionResult result = new PptxImageExtractor().extract(input.path(), outputDir);
        long extractionMs = elapsedMs(extractionStartedAt);

        long reportStartedAt = System.nanoTime();
        Path reportJson = outputDir.resolve("report.json");
        Path reportMarkdown = outputDir.resolve("report.md");
        Timing timing = new Timing(input.sourceKind(), input.sourceLoadMs(), extractionMs, 0, elapsedMs(startedAt));
        writeUtf8String(
                reportJson,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toReport(result, input, timing))
                        + System.lineSeparator());
        writeUtf8String(reportMarkdown, toMarkdown(result, input, timing));
        long reportWriteMs = elapsedMs(reportStartedAt);
        timing = new Timing(input.sourceKind(), input.sourceLoadMs(), extractionMs, reportWriteMs, elapsedMs(startedAt));
        writeUtf8String(
                reportJson,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toReport(result, input, timing))
                        + System.lineSeparator());
        writeUtf8String(reportMarkdown, toMarkdown(result, input, timing));

        assertTrue(Files.exists(reportJson), "report.json should be written");
        assertTrue(Files.exists(reportMarkdown), "report.md should be written");

        System.out.printf(
                """
                PPTX image extraction finished.
                input: %s
                resolvedInput: %s
                outputDir: %s
                images: %d, referenced: %d, unreferenced: %d, rasterReadable: %d, vectorLike: %d, externalRefs: %d
                timingMs: sourceLoad=%d, extraction=%d, reportWrite=%d, elapsed=%d
                reportJson: %s
                reportMarkdown: %s
                """,
                input.location(),
                result.pptxPath(),
                result.outputDirectory(),
                result.images().size(),
                result.referencedImageCount(),
                result.unreferencedImageCount(),
                result.rasterReadableCount(),
                result.vectorLikeCount(),
                result.externalImages().size(),
                timing.sourceLoadMs(),
                timing.extractionMs(),
                timing.reportWriteMs(),
                timing.elapsedMs(),
                reportJson.toAbsolutePath().normalize(),
                reportMarkdown.toAbsolutePath().normalize());
    }

    private String resolveInputLocation() {
        return firstNonBlank(System.getProperty(INPUT_PROPERTY), System.getenv(INPUT_ENV), DEFAULT_INPUT_PPTX.toString());
    }

    private Path resolveOutputDirectory(String inputLocation) {
        String configured = firstNonBlank(System.getProperty(OUTPUT_PROPERTY), System.getenv(OUTPUT_ENV));
        if (configured != null) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String baseName = stripExtension(inputFileName(inputLocation));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return DEFAULT_OUTPUT_ROOT.resolve(safePathSegment(baseName) + "-" + timestamp)
                .toAbsolutePath()
                .normalize();
    }

    private TimedInput materializeInput(String inputLocation, Path outputDir) throws Exception {
        if (!isHttpUrl(inputLocation)) {
            Path path = Path.of(inputLocation).toAbsolutePath().normalize();
            long sizeBytes = Files.isRegularFile(path) ? Files.size(path) : 0;
            return new TimedInput(inputLocation, path, "local-file", sizeBytes, 0);
        }

        long startedAt = System.nanoTime();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(inputLocation))
                .timeout(Duration.ofMinutes(3))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalArgumentException("Failed to download PPTX input, status=" + status + ", url=" + inputLocation);
        }
        byte[] bytes = response.body();
        Path sourceDir = outputDir.resolve("source");
        Files.createDirectories(sourceDir);
        Path path = sourceDir.resolve(safePathSegment(inputFileName(inputLocation)));
        Files.write(path, bytes);
        return new TimedInput(inputLocation, path, "http-url", bytes.length, elapsedMs(startedAt));
    }

    private Map<String, Object> toReport(PptxImageExtractor.ExtractionResult result,
                                         TimedInput input,
                                         Timing timing) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("inputLocation", input.location());
        report.put("pptxPath", result.pptxPath().toString());
        report.put("outputDirectory", result.outputDirectory().toString());
        report.put("summary", summary(result, input, timing));
        report.put("images", result.images().stream().map(this::imageReport).toList());
        report.put("uses", result.uses().stream().map(this::useReport).toList());
        report.put("externalImages", result.externalImages().stream().map(this::externalImageReport).toList());
        return report;
    }

    private Map<String, Object> summary(PptxImageExtractor.ExtractionResult result,
                                        TimedInput input,
                                        Timing timing) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("sourceKind", input.sourceKind());
        summary.put("sourceSizeBytes", input.sizeBytes());
        summary.put("sourceLoadMs", timing.sourceLoadMs());
        summary.put("extractionMs", timing.extractionMs());
        summary.put("reportWriteMs", timing.reportWriteMs());
        summary.put("elapsedMs", timing.elapsedMs());
        summary.put("imageCount", result.images().size());
        summary.put("referencedImageCount", result.referencedImageCount());
        summary.put("unreferencedPackageMediaCount", result.unreferencedImageCount());
        summary.put("rasterReadableCount", result.rasterReadableCount());
        summary.put("vectorLikeCount", result.vectorLikeCount());
        summary.put("imageUseCount", result.uses().size());
        summary.put("externalImageRefCount", result.externalImages().size());
        return summary;
    }

    private Map<String, Object> imageReport(PptxImageExtractor.ImageAsset image) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("packagePath", image.packagePath());
        item.put("outputRelativePath", image.outputRelativePath());
        item.put("fileName", image.fileName());
        item.put("extension", image.extension());
        item.put("contentType", image.contentType());
        item.put("sizeBytes", image.sizeBytes());
        item.put("sha256", image.sha256());
        item.put("width", image.width());
        item.put("height", image.height());
        item.put("rasterReadable", image.rasterReadable());
        item.put("vectorLike", image.vectorLike());
        item.put("referenced", image.referenced());
        item.put("useCount", image.useCount());
        item.put("uses", image.uses().stream().map(this::useReport).toList());
        return item;
    }

    private Map<String, Object> useReport(PptxImageExtractor.ImageUse use) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", use.index());
        item.put("sourcePart", use.sourcePart());
        item.put("slideNumber", use.slideNumber());
        item.put("relationshipId", use.relationshipId());
        item.put("targetPath", use.targetPath());
        item.put("packagePath", use.packagePath());
        item.put("shapeName", use.shapeName());
        item.put("shapeTitle", use.shapeTitle());
        item.put("shapeDescription", use.shapeDescription());
        item.put("nearbyText", use.nearbyText());
        return item;
    }

    private Map<String, Object> externalImageReport(PptxImageExtractor.ExternalImageRef ref) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", ref.index());
        item.put("sourcePart", ref.sourcePart());
        item.put("slideNumber", ref.slideNumber());
        item.put("relationshipId", ref.relationshipId());
        item.put("targetUrl", ref.targetUrl());
        return item;
    }

    private String toMarkdown(PptxImageExtractor.ExtractionResult result,
                              TimedInput input,
                              Timing timing) {
        StringBuilder sb = new StringBuilder();
        sb.append("# PPTX Image Extraction Report\n\n");
        appendLine(sb, "Input", input.location());
        appendLine(sb, "Resolved input", result.pptxPath().toString());
        appendLine(sb, "Output", result.outputDirectory().toString());
        sb.append('\n');
        sb.append("## Timing\n\n");
        sb.append("- Source kind: ").append(input.sourceKind()).append('\n');
        sb.append("- Source size bytes: ").append(input.sizeBytes()).append('\n');
        sb.append("- Source load ms: ").append(timing.sourceLoadMs()).append('\n');
        sb.append("- Extraction ms: ").append(timing.extractionMs()).append('\n');
        sb.append("- Report write ms: ").append(timing.reportWriteMs()).append('\n');
        sb.append("- Elapsed ms: ").append(timing.elapsedMs()).append('\n');
        sb.append('\n');
        sb.append("## Summary\n\n");
        sb.append("- Images: ").append(result.images().size()).append('\n');
        sb.append("- Referenced images: ").append(result.referencedImageCount()).append('\n');
        sb.append("- Unreferenced package media: ").append(result.unreferencedImageCount()).append('\n');
        sb.append("- Raster readable by ImageIO: ").append(result.rasterReadableCount()).append('\n');
        sb.append("- Vector-like resources: ").append(result.vectorLikeCount()).append('\n');
        sb.append("- XML image uses: ").append(result.uses().size()).append('\n');
        sb.append("- External image refs: ").append(result.externalImages().size()).append('\n');
        sb.append("\n## Images\n\n");
        sb.append("| # | Package path | Output | Ext | Bytes | Dimensions | Readable | Vector | Referenced | Uses |\n");
        sb.append("|---|---|---|---|---:|---|---|---|---|---:|\n");
        int index = 1;
        for (PptxImageExtractor.ImageAsset image : result.images()) {
            sb.append('|').append(index++).append('|')
                    .append(md(image.packagePath())).append('|')
                    .append(md(image.outputRelativePath())).append('|')
                    .append(md(image.extension())).append('|')
                    .append(image.sizeBytes()).append('|')
                    .append(md(dimensions(image))).append('|')
                    .append(image.rasterReadable()).append('|')
                    .append(image.vectorLike()).append('|')
                    .append(image.referenced()).append('|')
                    .append(image.useCount()).append("|\n");
        }
        sb.append("\n## Uses\n\n");
        if (result.uses().isEmpty()) {
            sb.append("No XML image uses found.\n");
        } else {
            for (PptxImageExtractor.ImageUse use : result.uses()) {
                sb.append("### Use ").append(use.index()).append("\n\n");
                appendLine(sb, "Source part", use.sourcePart());
                appendLine(sb, "Slide number", use.slideNumber() == null ? null : String.valueOf(use.slideNumber()));
                appendLine(sb, "Relationship", use.relationshipId());
                appendLine(sb, "Package path", use.packagePath());
                appendLine(sb, "Shape name", use.shapeName());
                appendLine(sb, "Shape title", use.shapeTitle());
                appendLine(sb, "Shape description", use.shapeDescription());
                appendLine(sb, "Nearby text", use.nearbyText());
                sb.append('\n');
            }
        }
        if (!result.externalImages().isEmpty()) {
            sb.append("\n## External Image Refs\n\n");
            for (PptxImageExtractor.ExternalImageRef ref : result.externalImages()) {
                sb.append("- ")
                        .append(md(ref.sourcePart()))
                        .append(" slide=")
                        .append(ref.slideNumber() == null ? "" : ref.slideNumber())
                        .append(' ')
                        .append(md(ref.relationshipId()))
                        .append(" -> ")
                        .append(md(ref.targetUrl()))
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append("- ").append(label).append(": ").append(value).append('\n');
    }

    private void writeUtf8String(Path path, String value) throws Exception {
        Files.writeString(path, sanitizeUtf16(value), StandardCharsets.UTF_8);
    }

    private String dimensions(PptxImageExtractor.ImageAsset image) {
        if (image.width() == null || image.height() == null) {
            return "";
        }
        return image.width() + "x" + image.height();
    }

    private String md(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private String inputFileName(String inputLocation) {
        if (isHttpUrl(inputLocation)) {
            try {
                String path = URI.create(inputLocation).getPath();
                int slash = path == null ? -1 : path.lastIndexOf('/');
                String name = slash >= 0 ? path.substring(slash + 1) : path;
                return firstNonBlank(name, "pptx-image-test.pptx");
            } catch (Exception ignored) {
                return "pptx-image-test.pptx";
            }
        }
        try {
            Path fileName = Path.of(inputLocation).getFileName();
            return fileName == null ? "pptx-image-test.pptx" : fileName.toString();
        } catch (Exception ignored) {
            return "pptx-image-test.pptx";
        }
    }

    private boolean isHttpUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private String stripExtension(String value) {
        int dot = value == null ? -1 : value.lastIndexOf('.');
        return dot <= 0 ? firstNonBlank(value, "pptx") : value.substring(0, dot);
    }

    private String safePathSegment(String value) {
        String safe = firstNonBlank(value, "pptx").replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isBlank() ? "pptx" : safe;
    }

    private String sanitizeUtf16(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
                    sb.append(ch).append(value.charAt(++i));
                } else {
                    sb.append('\uFFFD');
                }
            } else if (Character.isLowSurrogate(ch)) {
                sb.append('\uFFFD');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private long elapsedMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private record TimedInput(String location,
                              Path path,
                              String sourceKind,
                              long sizeBytes,
                              long sourceLoadMs) {
    }

    private record Timing(String sourceKind,
                          long sourceLoadMs,
                          long extractionMs,
                          long reportWriteMs,
                          long elapsedMs) {
    }
}
