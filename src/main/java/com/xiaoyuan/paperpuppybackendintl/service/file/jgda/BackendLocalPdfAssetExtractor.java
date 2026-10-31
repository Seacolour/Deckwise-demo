package com.xiaoyuan.paperpuppybackendintl.service.file.jgda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.ApplicationInputImage;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.ApplicationParam;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.OpenAiApplicationExecutor;
import com.xiaoyuan.paperpuppybackendintl.config.PdfDerivedAssetsProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiProperties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.utils.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Component
@Slf4j
public class BackendLocalPdfAssetExtractor {

    private static final Pattern FIGURE_CAPTION = Pattern.compile("(?im)^\\s*(fig\\.?|figure)\\s+\\d+\\s*[:.].*");
    private static final int OPENAI_PAGE_CONCURRENCY = 4;
    private static final int OPENAI_REGIONS_PER_PAGE = 1;
    private static final int OPENAI_CLIENT_MAX_RETRIES = 2;
    private static final String OPENAI_TEMP_PAGE_RENDER_PREFIX = "tmp/openai/pdf-page-renders";
    private static final int DESCRIPTION_MAX_LENGTH = 240;

    private final PdfDerivedAssetsProperties properties;
    private final OpenAiProperties openAiProperties;
    private final R2Client r2Client;
    private final StorageR2Properties r2Properties;
    private final ObjectMapper objectMapper;
    private final OpenAiApplicationExecutor openAiExecutor;

    @Autowired
    public BackendLocalPdfAssetExtractor(PdfDerivedAssetsProperties properties,
                                         OpenAiProperties openAiProperties,
                                         R2Client r2Client,
                                         StorageR2Properties r2Properties,
                                         @Qualifier("jackson2Mapper") ObjectMapper objectMapper) {
        this(properties, openAiProperties, r2Client, r2Properties, objectMapper, new OpenAiApplicationExecutor());
    }

    BackendLocalPdfAssetExtractor(PdfDerivedAssetsProperties properties,
                                  OpenAiProperties openAiProperties,
                                  R2Client r2Client,
                                  StorageR2Properties r2Properties,
                                  ObjectMapper objectMapper,
                                  OpenAiApplicationExecutor openAiExecutor) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.openAiProperties = Objects.requireNonNull(openAiProperties, "openAiProperties");
        this.r2Client = Objects.requireNonNull(r2Client, "r2Client");
        this.r2Properties = Objects.requireNonNull(r2Properties, "r2Properties");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.openAiExecutor = Objects.requireNonNull(openAiExecutor, "openAiExecutor");
    }

    public JgdaPdfAssetExtractionResult extract(String fileName, byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IOException("PDF bytes are empty");
        }
        if (!"openai".equalsIgnoreCase(properties.imageDetectionProvider())) {
            throw new IOException("backend local PDF asset extractor currently requires imageDetectionProvider=openai");
        }
        String apiKey = Objects.toString(openAiProperties.apiKey(), "").trim();
        if (apiKey.isBlank()) {
            throw new IOException("OPENAI_API_KEY is required for backend local PDF asset extraction");
        }

        Path outputDir = Files.createTempDirectory("paperpuppy-pdf-assets-");
        String safeFileName = safeFileName(fileName);
        Path pdfPath = outputDir.resolve(safeFileName);
        Files.write(pdfPath, pdfBytes);

        long startedAt = System.nanoTime();
        List<CandidatePage> candidatePages = selectCandidatePages(pdfPath, properties.maxPages());
        int pageCount = pageCount(pdfPath);
        List<Integer> selectedPages = resolveOpenAiPages(candidatePages, pageCount);
        log.info("[PdfDerivedAssets] backend local candidate page selection finished in {} ms, candidates={}",
                elapsedMs(startedAt),
                candidatePages.stream().map(CandidatePage::pageNumber).toList());

        long detectionStartedAt = System.nanoTime();
        List<DetectedRegion> regions = detectRegions(pdfPath, outputDir, safeFileName, selectedPages);
        log.info("[PdfDerivedAssets] backend local OpenAI detection finished in {} ms, pages={}, regions={}",
                elapsedMs(detectionStartedAt),
                selectedPages,
                regions.size());

        long cropStartedAt = System.nanoTime();
        List<JgdaExtractedImageAsset> crops = cropRegions(pdfPath, outputDir, regions);
        log.info("[PdfDerivedAssets] backend local OpenAI crop finished in {} ms, crops={}",
                elapsedMs(cropStartedAt),
                crops.size());

        JgdaPdfAssetExtractionResult result = new JgdaPdfAssetExtractionResult();
        result.setPdfPath(pdfPath.toString());
        result.setOutputDir(outputDir.toString());
        result.setPageCount(pageCount);
        result.setProcessedPageCount(Math.min(pageCount, properties.maxPages()));
        result.setDocumentAiProcessedPageCount(selectedPages.size());
        result.setDocumentAiDetectedRegionCount(regions.size());
        result.setDocumentAiCropCount(crops.size());
        result.setAssets(crops);
        result.setGeneratedAt(OffsetDateTime.now());
        log.info("[PdfDerivedAssets] backend local extraction finished in {} ms, totalAssets={}",
                elapsedMs(startedAt),
                crops.size());
        return result;
    }

    private List<DetectedRegion> detectRegions(Path pdfPath,
                                               Path outputDir,
                                               String safeFileName,
                                               List<Integer> selectedPages) throws IOException {
        if (selectedPages.isEmpty()) {
            return List.of();
        }
        String uploadBatchId = UUID.randomUUID().toString().replace("-", "");
        List<PageImage> pageImages = new ArrayList<>();
        long renderStartedAt = System.nanoTime();
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            for (Integer pageNumber : selectedPages) {
                if (pageNumber == null || pageNumber <= 0 || pageNumber > document.getNumberOfPages()) {
                    continue;
                }
                PDPage page = document.getPage(pageNumber - 1);
                PDRectangle cropBox = page.getCropBox();
                BufferedImage pageImage = renderer.renderImageWithDPI(
                        pageNumber - 1,
                        properties.openAiDetectionRenderDpi(),
                        ImageType.RGB);
                byte[] jpeg = encodeJpeg(pageImage, 0.85f);
                pageImages.add(new PageImage(
                        pageNumber,
                        cropBox,
                        pageImage.getWidth(),
                        pageImage.getHeight(),
                        jpeg));
            }
        }
        log.debug("[PdfDerivedAssets] backend local OpenAI page render finished in {} ms, pages={}, dpi={}, totalImageBytes={}",
                elapsedMs(renderStartedAt),
                pageImages.stream().map(PageImage::pageNumber).toList(),
                properties.openAiDetectionRenderDpi(),
                pageImages.stream().mapToInt(page -> page.jpeg().length).sum());

        if (pageImages.isEmpty()) {
            return List.of();
        }
        if (pageImages.size() <= 1) {
            List<DetectedRegion> regions = new ArrayList<>();
            for (PageImage pageImage : pageImages) {
                regions.addAll(detectPageImageSafely(outputDir, uploadBatchId, safeFileName, pageImage));
            }
            return List.copyOf(regions);
        }

        int concurrency = Math.min(OPENAI_PAGE_CONCURRENCY, pageImages.size());
        log.debug("[PdfDerivedAssets] backend local OpenAI detection running in parallel, pages={}, concurrency={}",
                pageImages.stream().map(PageImage::pageNumber).toList(),
                concurrency);
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<CompletableFuture<List<DetectedRegion>>> futures = pageImages.stream()
                    .map(pageImage -> CompletableFuture.supplyAsync(
                            () -> detectPageImageSafely(outputDir, uploadBatchId, safeFileName, pageImage),
                            executor))
                    .toList();
            List<DetectedRegion> regions = new ArrayList<>();
            for (CompletableFuture<List<DetectedRegion>> future : futures) {
                regions.addAll(future.join());
            }
            return List.copyOf(regions);
        }
    }

    private List<DetectedRegion> detectPageImageSafely(
            Path outputDir,
            String uploadBatchId,
            String safeFileName,
            PageImage pageImage) {
        try {
            return detectPageImage(outputDir, uploadBatchId, safeFileName, pageImage);
        } catch (Exception e) {
            log.warn("[PdfDerivedAssets] backend local OpenAI page detection skipped: page={}, err={}",
                    pageImage.pageNumber(),
                    summarizeFailure(e));
            log.debug("[PdfDerivedAssets] backend local OpenAI page detection skipped with stack: page={}",
                    pageImage.pageNumber(),
                    e);
            return List.of();
        }
    }

    private List<DetectedRegion> detectPageImage(
            Path outputDir,
            String uploadBatchId,
            String safeFileName,
            PageImage pageImage) throws IOException {
        long uploadStartedAt = System.nanoTime();
        String imageUrl = uploadPageImage(uploadBatchId, safeFileName, pageImage.pageNumber(), pageImage.jpeg());
        long uploadMs = elapsedMs(uploadStartedAt);
        long openAiStartedAt = System.nanoTime();
        String output = callOpenAi(
                pageImage.pageNumber(),
                pageImage.width(),
                pageImage.height(),
                imageUrl);
        long openAiMs = elapsedMs(openAiStartedAt);
        Files.writeString(outputDir.resolve("openai-page-%03d.json".formatted(pageImage.pageNumber())), output);
        long parseStartedAt = System.nanoTime();
        List<DetectedRegion> regions = parseRegions(pageImage.pageNumber(), pageImage.cropBox(), output);
        log.debug("[PdfDerivedAssets] backend local OpenAI page detection finished: page={}, uploadMs={}, openAiMs={}, parseMs={}, regions={}",
                pageImage.pageNumber(),
                uploadMs,
                openAiMs,
                elapsedMs(parseStartedAt),
                regions.size());
        return regions;
    }

    private String callOpenAi(int pageNumber, int width, int height, String imageUrl) {
        String model = firstNonBlank(properties.openAiModel(), openAiProperties.model(), "gpt-4.1-mini");
        String serviceTier = openAiProperties.serviceTier();
        Map<String, Object> options = openAiOptions();
        log.debug("[PdfDerivedAssets] backend local OpenAI request prepared: page={}, model={}, serviceTier={}, temperature={}, topP={}, maxOutputTokens={}, reasoningEffort={}, textVerbosity={}",
                pageNumber,
                model,
                firstNonBlank(serviceTier, "default"),
                options.get("model.temperature"),
                options.get("model.topP"),
                options.get("model.maxOutputTokens"),
                options.get("model.reasoningEffort"),
                options.get("model.textVerbosity"));
        ApplicationParam param = ApplicationParam.builder()
                .apiKey(openAiProperties.apiKey())
                .baseUrl(openAiProperties.baseUrl())
                .orgId(openAiProperties.orgId())
                .projectId(openAiProperties.projectId())
                .serviceTier(serviceTier)
                .model(model)
                .timeoutMs(properties.timeoutMs())
                .clientMaxRetries(OPENAI_CLIENT_MAX_RETRIES)
                .instructions("""
                        You select reusable visual evidence from rendered PDF pages for document understanding.
                        Return only the single best visual region on this page, if one is worth cropping as a reference image.
                        Prefer dominant, large, semantically meaningful visuals: main photos, charts, diagrams, maps, tables, or infographic blocks.
                        When several candidates exist, choose the largest meaningful visual block that would help a reader understand the document.
                        Ignore logos, icons, badges, decorative shapes, page backgrounds, tiny thumbnails, author portraits, isolated captions, page numbers, and low-value inset images.
                        If the page has a large main image and several small supporting images, choose the large main image.
                        Return no regions if the page only contains small decorative visuals or text-heavy layout fragments.
                        Exclude surrounding paragraphs, headers, footers, page numbers, and figure captions unless the caption is visually inside the figure.
                        If one figure has multiple panels, return one bounding box around the whole figure.
                        For each returned region, include a concise description that explains what the visual shows and why it may be useful as document evidence.
                        Do not transcribe full text; keep each description under 25 words.
                        """)
                .input("""
                        Locate reusable visual regions on PDF page %d.
                        The rendered page image size is %d x %d px.
                        Return bbox_1000 coordinates: x, y, width, and height are integers from 0 to 1000 relative to the full page image.
                        Return descriptions in English or the page language.
                        Return at most %d region: the best main visual for this page.
                        """.formatted(pageNumber, width, height, OPENAI_REGIONS_PER_PAGE))
                .inputImages(List.of(new ApplicationInputImage(
                        imageUrl,
                        "Rendered PDF page " + pageNumber + " for visual region detection.")))
                .jsonSchema(responseSchema())
                .jsonSchemaName("pdf_page_visual_regions")
                .jsonSchemaStrict(true)
                .options(options)
                .build();
        return openAiExecutor.call(param);
    }

    private Map<String, Object> openAiOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        if (properties.openAiTemperature() >= 0) {
            options.put("model.temperature", properties.openAiTemperature());
        }
        if (properties.openAiTopP() >= 0) {
            options.put("model.topP", properties.openAiTopP());
        }
        if (properties.openAiMaxOutputTokens() > 0) {
            options.put("model.maxOutputTokens", properties.openAiMaxOutputTokens());
        }
        if (StringUtils.hasText(properties.openAiReasoningEffort())) {
            options.put("model.reasoningEffort", properties.openAiReasoningEffort());
        }
        if (StringUtils.hasText(properties.openAiTextVerbosity())) {
            options.put("model.textVerbosity", properties.openAiTextVerbosity());
        }
        return options.isEmpty() ? Map.of() : Map.copyOf(options);
    }

    private List<DetectedRegion> parseRegions(int pageNumber, PDRectangle cropBox, String output) throws IOException {
        JsonNode root = objectMapper.readTree(output);
        if (!root.path("hasVisual").asBoolean(false) || !root.path("regions").isArray()) {
            return List.of();
        }
        DetectedRegion best = null;
        int regionIndex = 0;
        for (JsonNode node : root.path("regions")) {
            int x = clamp(node.path("x").asInt(), 0, 1000);
            int y = clamp(node.path("y").asInt(), 0, 1000);
            int width = clamp(node.path("width").asInt(), 1, 1000 - x);
            int height = clamp(node.path("height").asInt(), 1, 1000 - y);
            double area = (width / 1000.0) * (height / 1000.0);
            if (area < properties.openAiMinPageAreaRatio()) {
                continue;
            }
            regionIndex++;
            DetectedRegion candidate = new DetectedRegion(
                    pageNumber,
                    regionIndex,
                    Objects.toString(node.path("kind").asText(), "visual"),
                    sanitizeDescription(node.path("description").asText(null)),
                    placementFromNormalized(
                            x / 1000.0,
                            y / 1000.0,
                            width / 1000.0,
                            height / 1000.0,
                            cropBox.getWidth(),
                            cropBox.getHeight()),
                    node.path("confidence").asDouble(0),
                    area);
            if (best == null || regionScore(candidate) > regionScore(best)) {
                best = candidate;
            }
        }
        return best == null ? List.of() : List.of(best);
    }

    private List<JgdaExtractedImageAsset> cropRegions(
            Path pdfPath,
            Path outputDir,
            List<DetectedRegion> regions) throws IOException {
        if (regions.isEmpty()) {
            return List.of();
        }
        List<JgdaExtractedImageAsset> assets = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            renderer.setSubsamplingAllowed(true);
            int assetIndex = 0;
            for (DetectedRegion region : regions) {
                if (region.pageNumber() > document.getNumberOfPages()) {
                    continue;
                }
                BufferedImage pageImage = renderer.renderImageWithDPI(
                        region.pageNumber() - 1,
                        properties.openAiRenderDpi(),
                        ImageType.RGB);
                JgdaImagePlacement placement = expanded(region.placement(), properties.openAiCropMargin());
                BufferedImage crop = crop(pageImage, placement);
                if (crop == null) {
                    continue;
                }
                assetIndex++;
                byte[] bytes = encodePng(crop);
                String fileName = "page-%03d-openai-%03d.png".formatted(region.pageNumber(), assetIndex);
                Path output = outputDir.resolve(fileName);
                Files.write(output, bytes);

                JgdaExtractedImageAsset asset = new JgdaExtractedImageAsset();
                asset.setKind("openai-crop");
                asset.setPageNumber(region.pageNumber());
                asset.setAssetIndex(assetIndex);
                asset.setSourceName(region.kind() + ":confidence=" + region.confidence());
                asset.setDescription(region.description());
                asset.setWidth(crop.getWidth());
                asset.setHeight(crop.getHeight());
                asset.setFormat("png");
                asset.setFileSizeBytes(bytes.length);
                asset.setSha256(HashUtils.sha256Hex(bytes));
                asset.setFileName(fileName);
                asset.setRelativePath(outputDir.relativize(output).toString().replace('\\', '/'));
                asset.setPath(output.toString());
                asset.setPlacement(placement);
                assets.add(asset);
            }
        }
        return List.copyOf(assets);
    }

    private List<CandidatePage> selectCandidatePages(Path pdfPath, int maxPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int pagesToProcess = Math.min(document.getNumberOfPages(), maxPages <= 0 ? Integer.MAX_VALUE : maxPages);
            List<CandidatePage> candidates = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                int pageNumber = pageIndex + 1;
                int embeddedImageCount = countEmbeddedImages(document.getPage(pageIndex));
                boolean hasFigureCaption = hasFigureCaption(document, pageNumber);
                if (embeddedImageCount > 0 || hasFigureCaption) {
                    candidates.add(new CandidatePage(pageNumber, embeddedImageCount, hasFigureCaption));
                }
            }
            return List.copyOf(candidates);
        }
    }

    private int countEmbeddedImages(PDPage page) throws IOException {
        ImageCounter counter = new ImageCounter();
        counter.processPage(page);
        return counter.count();
    }

    private boolean hasFigureCaption(PDDocument document, int pageNumber) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        String text = stripper.getText(document);
        return FIGURE_CAPTION.matcher(text).find();
    }

    private List<Integer> resolveOpenAiPages(List<CandidatePage> candidatePages, int pageCount) {
        List<Integer> selected = candidatePages.stream()
                .map(CandidatePage::pageNumber)
                .distinct()
                .limit(properties.openAiMaxPages())
                .toList();
        if (!selected.isEmpty()) {
            return selected;
        }
        List<Integer> fallback = new ArrayList<>();
        int fallbackPageCount = Math.min(pageCount, properties.openAiMaxPages());
        for (int page = 1; page <= fallbackPageCount; page++) {
            fallback.add(page);
        }
        return List.copyOf(fallback);
    }

    private int pageCount(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            return document.getNumberOfPages();
        }
    }

    private String uploadPageImage(String uploadBatchId, String safeFileName, int pageNumber, byte[] bytes) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = OPENAI_TEMP_PAGE_RENDER_PREFIX + "/" + date + "/" + uploadBatchId + "/"
                + safeFileName + "-page-%03d.jpg".formatted(pageNumber);
        String prefix = r2Properties.effectivePathPrefix();
        if (StringUtils.hasText(prefix)) {
            key = prefix.replaceAll("/$", "") + "/" + key;
        }
        R2Client.UploadResult uploaded = r2Client.uploadWithMeta(key, bytes, "image/jpeg");
        return uploaded.publicUrl();
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG ImageWriter available");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(imageOut);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage crop(BufferedImage pageImage, JgdaImagePlacement placement) {
        int x = clamp((int) Math.floor(placement.getNormalizedLeft() * pageImage.getWidth()), 0, pageImage.getWidth() - 1);
        int y = clamp((int) Math.floor(placement.getNormalizedTop() * pageImage.getHeight()), 0, pageImage.getHeight() - 1);
        int right = clamp((int) Math.ceil((placement.getNormalizedLeft() + placement.getNormalizedWidth()) * pageImage.getWidth()), x + 1, pageImage.getWidth());
        int bottom = clamp((int) Math.ceil((placement.getNormalizedTop() + placement.getNormalizedHeight()) * pageImage.getHeight()), y + 1, pageImage.getHeight());
        int width = right - x;
        int height = bottom - y;
        if (width <= 1 || height <= 1) {
            return null;
        }
        return pageImage.getSubimage(x, y, width, height);
    }

    private JgdaImagePlacement expanded(JgdaImagePlacement placement, double margin) {
        double left = clamp(placement.getLeft() - margin, 0, placement.getPageWidth());
        double top = clamp(placement.getTop() - margin, 0, placement.getPageHeight());
        double right = clamp(placement.getLeft() + placement.getWidth() + margin, left, placement.getPageWidth());
        double bottom = clamp(placement.getTop() + placement.getHeight() + margin, top, placement.getPageHeight());
        return placement(left, top, right - left, bottom - top, placement.getPageWidth(), placement.getPageHeight());
    }

    private JgdaImagePlacement placementFromNormalized(
            double left,
            double top,
            double width,
            double height,
            double pageWidth,
            double pageHeight) {
        return placement(left * pageWidth, top * pageHeight, width * pageWidth, height * pageHeight, pageWidth, pageHeight);
    }

    private JgdaImagePlacement placement(
            double left,
            double top,
            double width,
            double height,
            double pageWidth,
            double pageHeight) {
        JgdaImagePlacement placement = new JgdaImagePlacement();
        placement.setLeft(round(left, 2));
        placement.setTop(round(top, 2));
        placement.setWidth(round(width, 2));
        placement.setHeight(round(height, 2));
        placement.setPageWidth(round(pageWidth, 2));
        placement.setPageHeight(round(pageHeight, 2));
        placement.setNormalizedLeft(round(ratio(left, pageWidth), 4));
        placement.setNormalizedTop(round(ratio(top, pageHeight), 4));
        placement.setNormalizedWidth(round(ratio(width, pageWidth), 4));
        placement.setNormalizedHeight(round(ratio(height, pageHeight), 4));
        return placement;
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> regionSchema = new LinkedHashMap<>();
        regionSchema.put("type", "object");
        regionSchema.put("additionalProperties", false);
        regionSchema.put("required", List.of("kind", "description", "x", "y", "width", "height", "confidence", "includeCaption"));
        regionSchema.put("properties", Map.of(
                "kind", Map.of("type", "string", "enum", List.of(
                        "figure", "chart", "diagram", "photo", "table_like_visual", "other_visual")),
                "description", Map.of("type", "string"),
                "x", Map.of("type", "integer", "minimum", 0, "maximum", 1000),
                "y", Map.of("type", "integer", "minimum", 0, "maximum", 1000),
                "width", Map.of("type", "integer", "minimum", 1, "maximum", 1000),
                "height", Map.of("type", "integer", "minimum", 1, "maximum", 1000),
                "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                "includeCaption", Map.of("type", "boolean")));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("hasVisual", "regions"));
        schema.put("properties", Map.of(
                "hasVisual", Map.of("type", "boolean"),
                "regions", Map.of(
                        "type", "array",
                        "maxItems", OPENAI_REGIONS_PER_PAGE,
                        "items", regionSchema)));
        return schema;
    }

    private double regionScore(DetectedRegion region) {
        if (region == null) {
            return 0;
        }
        return region.areaRatio() * Math.max(0.1, region.confidence());
    }

    private String sanitizeDescription(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= DESCRIPTION_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, DESCRIPTION_MAX_LENGTH).trim();
    }

    private String safeFileName(String fileName) {
        String name = Objects.toString(fileName, "input.pdf").trim();
        if (name.isBlank()) {
            name = "input.pdf";
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]+", "-");
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? name : name + ".pdf";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String summarizeFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null
                && ("java.util.concurrent.CompletionException".equals(current.getClass().getName())
                || "java.lang.RuntimeException".equals(current.getClass().getName()))) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown";
        }
        String message = Objects.toString(current.getMessage(), "").trim();
        return message.isBlank()
                ? current.getClass().getSimpleName()
                : current.getClass().getSimpleName() + ": " + message;
    }

    private long elapsedMs(long startedAtNs) {
        return (System.nanoTime() - startedAtNs) / 1_000_000L;
    }

    private double ratio(double value, double total) {
        return total <= 0 ? 0 : value / total;
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CandidatePage(int pageNumber, int embeddedImageCount, boolean hasFigureCaption) {
    }

    private record PageImage(
            int pageNumber,
            PDRectangle cropBox,
            int width,
            int height,
            byte[] jpeg) {
    }

    private record DetectedRegion(
            int pageNumber,
            int regionIndex,
            String kind,
            String description,
            JgdaImagePlacement placement,
            double confidence,
            double areaRatio) {
    }

    private static class ImageCounter extends PDFStreamEngine {
        private int count;

        ImageCounter() {
            addOperator(new Concatenate(this));
            addOperator(new DrawObject(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
            addOperator(new SetGraphicsStateParameters(this));
            addOperator(new SetMatrix(this));
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.getFirst() instanceof COSName name) {
                PDXObject xObject = getResources().getXObject(name);
                if (xObject instanceof PDImageXObject) {
                    count++;
                    return;
                }
                if (xObject instanceof PDFormXObject formObject) {
                    showForm(formObject);
                    return;
                }
            }
            super.processOperator(operator, operands);
        }

        int count() {
            return count;
        }
    }
}
