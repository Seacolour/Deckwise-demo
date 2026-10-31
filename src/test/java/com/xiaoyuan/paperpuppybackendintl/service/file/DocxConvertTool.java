package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.tinycircl.pandoc4j.Format;
import org.tinycircl.pandoc4j.spring.PandocAutoConfiguration;
import org.tinycircl.pandoc4j.spring.PandocClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 本地手动工具：基于 Spring Boot Test 读取配置文件中的 Pandoc / R2 配置，
 * 将任意 Pandoc 支持的输入文件转换为 Markdown，并尽量把附带资源替换为可公开访问的 R2 URL。
 *
 * <p>默认读取 {@code application.yml}，并自动叠加当前激活 profile 的配置。
 * 若当前测试进程未显式注入环境变量，则会额外从项目根目录的 {@code .env} 中补齐缺失变量，
 * 以便解析 {@code ${R2_*}} 这类占位符。
 *
 * <p>输入文件路径优先读取系统属性 {@code -Dpandoc.test.input=<path>}，
 * 其次读取环境变量 {@code PANDOC_TEST_INPUT}，
 * 最后回退到 {@link #DEFAULT_INPUT_FILE}。
 */
@SpringBootTest(
        classes = DocxConvertTool.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class DocxConvertTool {

    private static final String LOG_PREFIX = "[DocxConvertTool]";
    private static final String DOTENV_FILENAME = ".env";

    /** 开发阶段的默认输入文件路径；也可通过系统属性或环境变量覆盖。 */
    private static final String DEFAULT_INPUT_FILE = "C:\\Users\\Zhuang\\Desktop\\PDFTest\\kaiminghe\\LLMs.pptx";

    private static final String INPUT_FILE_PROPERTY = "pandoc.test.input";
    private static final String INPUT_FILE_ENV = "PANDOC_TEST_INPUT";
    private static final String MEDIA_DIR_SUFFIX = ".assets";
    private static final String R2_UPLOAD_SCOPE = "manual-pandoc";
    private static final int PREVIEW_CHARS = 1000;
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile(
            "!\\[(.*?)]\\((\\S+?)(?:\\s+\"([^\"]*)\")?\\)",
            Pattern.DOTALL);
    private static final Pattern PLACEHOLDER_IMAGE_TITLE_PATTERN = Pattern.compile(
            "^(?:内容占位符|占位符|content\\s+placeholder|placeholder)\\s*\\d+$",
            Pattern.CASE_INSENSITIVE);

    @Autowired(required = false)
    private PandocClient pandocClient;

    @Autowired(required = false)
    private StorageR2Properties storageR2Properties;

    @DynamicPropertySource
    static void registerDotenvProperties(DynamicPropertyRegistry registry) {
        loadDotEnvProperties().forEach((key, value) -> {
            if (isBlank(key) || isBlank(value)) {
                return;
            }
            if (System.getProperty(key) != null || System.getenv(key) != null) {
                return;
            }
            registry.add(key, () -> value);
        });
    }

    @Test
    void convert() throws Exception {
        Path inputPath = resolveInputPath();
        if (inputPath == null) {
            System.err.println(LOG_PREFIX + " 未提供输入文件路径。");
            System.err.println(LOG_PREFIX + " 可通过以下任一方式传入：");
            System.err.println(LOG_PREFIX + " 1) 修改 DEFAULT_INPUT_FILE 常量");
            System.err.println(LOG_PREFIX + " 2) VM options: -D" + INPUT_FILE_PROPERTY + "=<绝对路径>");
            System.err.println(LOG_PREFIX + " 3) 环境变量: " + INPUT_FILE_ENV + "=<绝对路径>");
            return;
        }

        if (!Files.isRegularFile(inputPath)) {
            System.err.println(LOG_PREFIX + " 输入文件不存在或不是普通文件: " + inputPath.toAbsolutePath());
            return;
        }

        if (pandocClient == null) {
            System.err.println(LOG_PREFIX + " Spring 上下文中未拿到 PandocClient。");
            System.err.println(LOG_PREFIX + " 请确认本机已安装 Pandoc，或已在配置中设置 pandoc.executable-path。");
            return;
        }

        System.out.println(LOG_PREFIX + " Pandoc 版本: " + pandocClient.getVersion());
        System.out.println(LOG_PREFIX + " Pandoc 路径: " + pandocClient.getInstallation().getExecutablePath());

        Optional<Format> detectedFormat = Format.fromExtension(getExtension(inputPath));
        System.out.println(LOG_PREFIX + " 推断输入格式: "
                + detectedFormat.map(Format::getPandocName).orElse("未知（交由 pandoc 自行处理）"));

        Path workingDirectory = resolveWorkingDirectory(inputPath);
        String mediaDirectoryName = resolveMediaDirectoryName(inputPath);
        Path mediaRoot = workingDirectory.resolve(mediaDirectoryName);

        deleteRecursively(mediaRoot);

        System.out.println(LOG_PREFIX + " 正在转换: " + inputPath.toAbsolutePath());
        System.out.println(LOG_PREFIX + " 资源抽取目录: " + mediaRoot.toAbsolutePath());

        var request = pandocClient.builder()
                .to(Format.MARKDOWN_GFM)
                .wrapNone()
                .workingDirectory(workingDirectory)
                .extractMedia(mediaDirectoryName);
        detectedFormat.ifPresent(request::from);

        String markdown = normalizeGeneratedMarkdown(request.convertFile(inputPath));
        int extractedCount = countRegularFiles(mediaRoot);
        System.out.println(LOG_PREFIX + " 抽取资源文件数: " + extractedCount);

        String finalMarkdown = markdown;
        boolean replacedWithPublicUrl = false;
        try (R2UploadSupport r2UploadSupport = createR2UploadSupport()) {
            if (extractedCount > 0) {
                if (r2UploadSupport == null) {
                    System.out.println(LOG_PREFIX + " 未检测到可用的 R2 配置，保留本地资源目录供手动检查。");
                } else {
                    List<UploadedMedia> uploadedMedia = uploadExtractedMedia(
                            mediaRoot, workingDirectory, inputPath, r2UploadSupport);
                    finalMarkdown = replaceMediaLinks(markdown, uploadedMedia);
                    replacedWithPublicUrl = true;
                    deleteRecursively(mediaRoot);
                    System.out.println(LOG_PREFIX + " 资源已上传到 R2，本地资源目录已清理。");
                }
            }
        } catch (Exception e) {
            System.err.println(LOG_PREFIX + " R2 上传或 Markdown 回写失败，将保留本地资源目录: " + e.getMessage());
        }

        Path outputPath = resolveMarkdownOutputPath(inputPath);
        Files.writeString(outputPath, finalMarkdown, StandardCharsets.UTF_8);

        System.out.println(LOG_PREFIX + " 转换完成！输出文件: " + outputPath.toAbsolutePath());
        if (replacedWithPublicUrl) {
            System.out.println(LOG_PREFIX + " Markdown 中的资源引用已替换为 R2 公网 URL。");
        } else if (extractedCount > 0) {
            System.out.println(LOG_PREFIX + " Markdown 仍保留本地资源引用，请结合目录使用: " + mediaRoot.toAbsolutePath());
        }
        System.out.println(LOG_PREFIX + " Markdown 内容（前 " + PREVIEW_CHARS + " 字符）:");
        System.out.println("─".repeat(60));
        System.out.println(finalMarkdown.length() > PREVIEW_CHARS
                ? finalMarkdown.substring(0, PREVIEW_CHARS) + "\n…（共 " + finalMarkdown.length() + " 字符）"
                : finalMarkdown);
        System.out.println("─".repeat(60));
    }

    private R2UploadSupport createR2UploadSupport() {
        if (storageR2Properties == null) {
            System.out.println(LOG_PREFIX + " Spring 上下文中未拿到 StorageR2Properties，跳过自动上传。");
            return null;
        }

        StorageR2Properties.Node node = storageR2Properties.effectiveNode();
        if (node == null) {
            System.out.println(LOG_PREFIX + " storage.r2 未配置有效节点，跳过自动上传。");
            return null;
        }

        String endpoint = trimToNull(node.getEndpoint());
        String bucket = trimToNull(node.getBucket());
        String region = trimToNull(node.getRegion());
        String accessKey = trimToNull(node.getAccessKey());
        String secretKey = trimToNull(node.getSecretKey());
        String pathPrefix = trimToNull(node.getPathPrefix());
        String publicDomain = trimToNull(node.getPublicDomain());

        if (isBlank(region)) {
            region = "auto";
        }
        if (isBlank(endpoint) || isBlank(bucket) || isBlank(accessKey) || isBlank(secretKey)) {
            System.out.println(LOG_PREFIX + " storage.r2 节点配置不完整，跳过自动上传。");
            return null;
        }

        S3Client s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();

        if (isBlank(publicDomain)) {
            System.out.println(LOG_PREFIX + " 未配置 R2 public domain，回写链接将退化为 endpoint/bucket/key 形式。");
        }

        System.out.println(LOG_PREFIX + " R2 Bucket: " + bucket);
        System.out.println(LOG_PREFIX + " R2 Path Prefix: " + (pathPrefix == null ? "<none>" : pathPrefix));

        return new R2UploadSupport(endpoint, bucket, pathPrefix, publicDomain, s3Client);
    }

    private static Path resolveInputPath() {
        String candidate = System.getProperty(INPUT_FILE_PROPERTY);
        if (candidate == null || candidate.isBlank()) {
            candidate = System.getenv(INPUT_FILE_ENV);
        }
        if (candidate == null || candidate.isBlank()) {
            candidate = DEFAULT_INPUT_FILE;
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        return Paths.get(candidate);
    }

    private static Map<String, String> loadDotEnvProperties() {
        Path dotenvPath = findDotEnvPath();
        if (dotenvPath == null || !Files.isRegularFile(dotenvPath)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(dotenvPath, StandardCharsets.UTF_8)) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int equalsIndex = line.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }
                String key = line.substring(0, equalsIndex).trim();
                String value = stripMatchingQuotes(line.substring(equalsIndex + 1).trim());
                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println(LOG_PREFIX + " 读取 .env 失败，将仅使用当前进程已有环境变量: " + e.getMessage());
        }
        return values;
    }

    private static Path findDotEnvPath() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 6 && current != null; depth++) {
            Path candidate = current.resolve(DOTENV_FILENAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String stripMatchingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Path resolveWorkingDirectory(Path inputPath) {
        Path parent = inputPath.toAbsolutePath().getParent();
        return parent != null ? parent : Path.of(".").toAbsolutePath().normalize();
    }

    private static String resolveMediaDirectoryName(Path inputPath) {
        String inputFilename = inputPath.getFileName().toString();
        String baseName = inputFilename.replaceFirst("(?i)\\.[^.]+$", "");
        if (baseName.isBlank()) {
            baseName = "pandoc-media";
        }
        return baseName + MEDIA_DIR_SUFFIX;
    }

    private static Path resolveMarkdownOutputPath(Path inputPath) {
        String inputFilename = inputPath.getFileName().toString();
        String baseName = inputFilename.replaceFirst("(?i)\\.[^.]+$", "");
        if (baseName.equals(inputFilename)) {
            baseName = inputFilename;
        }

        String outputFilename = baseName + ".md";
        if (outputFilename.equalsIgnoreCase(inputFilename)) {
            outputFilename = baseName + ".converted.md";
        }
        return inputPath.resolveSibling(outputFilename);
    }

    private static String getExtension(Path inputPath) {
        String filename = inputPath.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1);
    }

    private static List<UploadedMedia> uploadExtractedMedia(
            Path mediaRoot,
            Path workingDirectory,
            Path inputPath,
            R2UploadSupport uploadSupport) throws IOException {
        List<Path> files = listRegularFiles(mediaRoot);
        if (files.isEmpty()) {
            return List.of();
        }

        String uploadPrefix = buildUploadPrefix(uploadSupport.pathPrefix, inputPath);
        List<UploadedMedia> uploadedMedia = new ArrayList<>();
        for (Path file : files) {
            String relativeToMediaRoot = toUnixPath(mediaRoot.relativize(file));
            String relativeToWorkingDirectory = toUnixPath(workingDirectory.relativize(file));
            String objectKey = uploadPrefix + "/" + relativeToMediaRoot;
            String contentType = detectContentType(file);
            String publicUrl = uploadSupport.upload(objectKey, Files.readAllBytes(file), contentType);

            System.out.println(LOG_PREFIX + " 已上传资源: " + relativeToMediaRoot + " -> " + publicUrl);
            uploadedMedia.add(new UploadedMedia(relativeToWorkingDirectory, relativeToMediaRoot, publicUrl));
        }
        return uploadedMedia;
    }

    private static String buildUploadPrefix(String pathPrefix, Path inputPath) {
        String normalizedPathPrefix = trimToNull(pathPrefix);
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String baseName = sanitizeSegment(inputPath.getFileName().toString().replaceFirst("(?i)\\.[^.]+$", ""));
        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String basePath = R2_UPLOAD_SCOPE + "/" + date + "/" + baseName + "-" + runId;
        if (isBlank(normalizedPathPrefix)) {
            return basePath;
        }
        return normalizedPathPrefix.replaceAll("/$", "") + "/" + basePath;
    }

    private static String replaceMediaLinks(String markdown, List<UploadedMedia> uploadedMedia) {
        List<UploadedMedia> sortedMedia = new ArrayList<>(uploadedMedia);
        sortedMedia.sort(Comparator.comparingInt(DocxConvertTool::sortWeight).reversed());

        String rewritten = markdown;
        for (UploadedMedia media : sortedMedia) {
            rewritten = replaceMarkdownDestination(rewritten, media.relativePathFromWorkingDirectory(), media.publicUrl());
            rewritten = replaceMarkdownDestination(rewritten, "./" + media.relativePathFromWorkingDirectory(), media.publicUrl());
            rewritten = replaceMarkdownDestination(rewritten, media.relativePathFromMediaRoot(), media.publicUrl());
            rewritten = replaceMarkdownDestination(rewritten, "./" + media.relativePathFromMediaRoot(), media.publicUrl());
        }
        return rewritten;
    }

    private static int sortWeight(UploadedMedia media) {
        return Math.max(media.relativePathFromWorkingDirectory().length(), media.relativePathFromMediaRoot().length());
    }

    private static String replaceMarkdownDestination(String markdown, String sourcePath, String targetUrl) {
        if (isBlank(sourcePath) || isBlank(targetUrl) || sourcePath.equals(targetUrl)) {
            return markdown;
        }
        Pattern pattern = Pattern.compile("\\(" + Pattern.quote(sourcePath) + "(?=(?:\\s+\"[^\"]*\")?\\))");
        return pattern.matcher(markdown).replaceAll(Matcher.quoteReplacement("(" + targetUrl));
    }

    private static String normalizeGeneratedMarkdown(String markdown) {
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(markdown);
        StringBuffer normalized = new StringBuffer();
        while (matcher.find()) {
            String alt = normalizeImageAltText(matcher.group(1));
            String destination = matcher.group(2);
            String title = normalizeImageTitle(matcher.group(3));

            StringBuilder replacement = new StringBuilder();
            replacement.append("![")
                    .append(escapeMarkdownImageAltText(alt))
                    .append("](")
                    .append(destination);
            if (title != null) {
                replacement.append(" \"").append(title.replace("\"", "\\\"")).append('"');
            }
            replacement.append(')');
            matcher.appendReplacement(normalized, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    private static String normalizeImageAltText(String alt) {
        if (alt == null) {
            return "";
        }
        String normalized = alt.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s*\\n\\s*", " ")
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .trim();
        normalized = normalized.replaceAll("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})", "");
        return normalized;
    }

    private static String normalizeImageTitle(String title) {
        String normalized = trimToNull(title);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replaceAll("\\s+", " ");
        if (PLACEHOLDER_IMAGE_TITLE_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private static String escapeMarkdownImageAltText(String alt) {
        return alt.replace("\\", "\\\\")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private static List<Path> listRegularFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    private static int countRegularFiles(Path root) throws IOException {
        return listRegularFiles(root).size();
    }

    private static String detectContentType(Path file) throws IOException {
        String contentType = Files.probeContentType(file);
        if (!isBlank(contentType)) {
            return contentType;
        }
        return switch (getExtension(file).toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String sanitizeSegment(String input) {
        String sanitized = input == null ? "" : input.replaceAll("[^A-Za-z0-9._-]+", "-");
        sanitized = sanitized.replaceAll("-{2,}", "-").replaceAll("(^[-.]+|[-.]+$)", "");
        return sanitized.isBlank() ? "source" : sanitized;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private record UploadedMedia(
            String relativePathFromWorkingDirectory,
            String relativePathFromMediaRoot,
            String publicUrl) {
    }

    private static final class R2UploadSupport implements AutoCloseable {
        private final String endpoint;
        private final String bucket;
        private final String pathPrefix;
        private final String publicDomain;
        private final S3Client s3Client;

        private R2UploadSupport(
                String endpoint,
                String bucket,
                String pathPrefix,
                String publicDomain,
                S3Client s3Client) {
            this.endpoint = endpoint;
            this.bucket = bucket;
            this.pathPrefix = pathPrefix;
            this.publicDomain = publicDomain;
            this.s3Client = s3Client;
        }

        private String upload(String key, byte[] bytes, String contentType) {
            String normalizedKey = normalizeKey(key);
            String normalizedContentType = isBlank(contentType) ? "application/octet-stream" : contentType.trim();
            s3Client.putObject(
                    request -> request.bucket(bucket)
                            .key(normalizedKey)
                            .contentType(normalizedContentType),
                    RequestBody.fromBytes(bytes));
            return buildPublicUrl(normalizedKey);
        }

        private String buildPublicUrl(String key) {
            if (isBlank(publicDomain)) {
                return endpoint.replaceAll("/$", "") + "/" + bucket + "/" + normalizeKey(key);
            }
            return publicDomain.replaceAll("/$", "") + "/" + normalizeKey(key);
        }

        private String normalizeKey(String key) {
            String normalized = trimToNull(key);
            if (normalized == null) {
                throw new IllegalArgumentException("对象 key 不能为空");
            }
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            return normalized;
        }

        @Override
        public void close() {
            s3Client.close();
        }
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties(StorageR2Properties.class)
    @Import(PandocAutoConfiguration.class)
    static class TestApplication {
    }
}
