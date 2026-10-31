package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.JacksonConfig;
import com.xiaoyuan.paperpuppybackendintl.utils.file.DocxImageExtractor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocxImageExtractorManualTest {

    private static final String INPUT_PROPERTY = "docx.image.extract.input";
    private static final String INPUT_ENV = "DOCX_IMAGE_EXTRACT_INPUT";
    private static final String OUTPUT_PROPERTY = "docx.image.extract.output";
    private static final String OUTPUT_ENV = "DOCX_IMAGE_EXTRACT_OUTPUT";
    private static final Path DEFAULT_INPUT_DOCX = Path.of("D:/web/test/docx/docx-image-test.docx");
    private static final Path DEFAULT_OUTPUT_ROOT = Path.of("target/manual-docx-image-extraction");
    private static final ObjectMapper OBJECT_MAPPER = new JacksonConfig().jackson2Mapper();

    /**
     * Manual research entrypoint.
     *
     * <p>Run with either:
     * <pre>
     * ./mvnw -Dtest=DocxImageExtractorManualTest -Ddocx.image.extract.input=D:/tmp/sample.docx test
     * </pre>
     * or set DOCX_IMAGE_EXTRACT_INPUT.
     */
    @Test
    void extractDocxImagesToLocalReport() throws Exception {
        Path input = resolveInputPath();
        assumeTrue(Files.isRegularFile(input), "manual DOCX input file not found: " + input);

        Path outputDir = resolveOutputDirectory(input);
        Files.createDirectories(outputDir);

        DocxImageExtractor.ExtractionResult result = new DocxImageExtractor().extract(input, outputDir);
        Path reportJson = outputDir.resolve("report.json");
        Path reportMarkdown = outputDir.resolve("report.md");

        Files.writeString(
                reportJson,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toReport(result))
                        + System.lineSeparator(),
                StandardCharsets.UTF_8);
        Files.writeString(reportMarkdown, toMarkdown(result), StandardCharsets.UTF_8);

        assertTrue(Files.exists(reportJson), "report.json should be written");
        assertTrue(Files.exists(reportMarkdown), "report.md should be written");

        System.out.printf(
                """
                DOCX image extraction finished.
                input: %s
                outputDir: %s
                images: %d, referenced: %d, unreferenced: %d, rasterReadable: %d, vectorLike: %d, externalRefs: %d
                reportJson: %s
                reportMarkdown: %s
                """,
                result.docxPath(),
                result.outputDirectory(),
                result.images().size(),
                result.referencedImageCount(),
                result.unreferencedImageCount(),
                result.rasterReadableCount(),
                result.vectorLikeCount(),
                result.externalImages().size(),
                reportJson.toAbsolutePath().normalize(),
                reportMarkdown.toAbsolutePath().normalize());
    }

    private Path resolveInputPath() {
        String configured = firstNonBlank(System.getProperty(INPUT_PROPERTY), System.getenv(INPUT_ENV));
        return configured == null ? DEFAULT_INPUT_DOCX : Path.of(configured);
    }

    private Path resolveOutputDirectory(Path input) {
        String configured = firstNonBlank(System.getProperty(OUTPUT_PROPERTY), System.getenv(OUTPUT_ENV));
        if (configured != null) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String baseName = stripExtension(input.getFileName().toString());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return DEFAULT_OUTPUT_ROOT.resolve(safePathSegment(baseName) + "-" + timestamp)
                .toAbsolutePath()
                .normalize();
    }

    private Map<String, Object> toReport(DocxImageExtractor.ExtractionResult result) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("docxPath", result.docxPath().toString());
        report.put("outputDirectory", result.outputDirectory().toString());
        report.put("summary", summary(result));
        report.put("images", result.images().stream().map(this::imageReport).toList());
        report.put("uses", result.uses().stream().map(this::useReport).toList());
        report.put("externalImages", result.externalImages().stream().map(this::externalImageReport).toList());
        return report;
    }

    private Map<String, Object> summary(DocxImageExtractor.ExtractionResult result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("imageCount", result.images().size());
        summary.put("referencedImageCount", result.referencedImageCount());
        summary.put("unreferencedImageCount", result.unreferencedImageCount());
        summary.put("rasterReadableCount", result.rasterReadableCount());
        summary.put("vectorLikeCount", result.vectorLikeCount());
        summary.put("imageUseCount", result.uses().size());
        summary.put("externalImageRefCount", result.externalImages().size());
        return summary;
    }

    private Map<String, Object> imageReport(DocxImageExtractor.ImageAsset image) {
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

    private Map<String, Object> useReport(DocxImageExtractor.ImageUse use) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", use.index());
        item.put("sourcePart", use.sourcePart());
        item.put("relationshipId", use.relationshipId());
        item.put("targetPath", use.targetPath());
        item.put("packagePath", use.packagePath());
        item.put("docPrName", use.docPrName());
        item.put("docPrTitle", use.docPrTitle());
        item.put("docPrDescription", use.docPrDescription());
        item.put("paragraphText", use.paragraphText());
        return item;
    }

    private Map<String, Object> externalImageReport(DocxImageExtractor.ExternalImageRef ref) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", ref.index());
        item.put("sourcePart", ref.sourcePart());
        item.put("relationshipId", ref.relationshipId());
        item.put("targetUrl", ref.targetUrl());
        return item;
    }

    private String toMarkdown(DocxImageExtractor.ExtractionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DOCX Image Extraction Report\n\n");
        appendLine(sb, "Input", result.docxPath().toString());
        appendLine(sb, "Output", result.outputDirectory().toString());
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
        for (DocxImageExtractor.ImageAsset image : result.images()) {
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
            for (DocxImageExtractor.ImageUse use : result.uses()) {
                sb.append("### Use ").append(use.index()).append("\n\n");
                appendLine(sb, "Source part", use.sourcePart());
                appendLine(sb, "Relationship", use.relationshipId());
                appendLine(sb, "Package path", use.packagePath());
                appendLine(sb, "docPr name", use.docPrName());
                appendLine(sb, "docPr title", use.docPrTitle());
                appendLine(sb, "docPr description", use.docPrDescription());
                appendLine(sb, "Paragraph text", use.paragraphText());
                sb.append('\n');
            }
        }
        if (!result.externalImages().isEmpty()) {
            sb.append("\n## External Image Refs\n\n");
            for (DocxImageExtractor.ExternalImageRef ref : result.externalImages()) {
                sb.append("- ")
                        .append(md(ref.sourcePart()))
                        .append(" ")
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

    private String dimensions(DocxImageExtractor.ImageAsset image) {
        if (image.width() == null || image.height() == null) {
            return "";
        }
        return image.width() + "x" + image.height();
    }

    private String md(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private String stripExtension(String value) {
        int dot = value == null ? -1 : value.lastIndexOf('.');
        return dot <= 0 ? firstNonBlank(value, "docx") : value.substring(0, dot);
    }

    private String safePathSegment(String value) {
        String safe = firstNonBlank(value, "docx").replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isBlank() ? "docx" : safe;
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
}
