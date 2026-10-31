package com.xiaoyuan.paperpuppybackendintl.utils.file;

import com.xiaoyuan.paperpuppybackendintl.utils.HashUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Research utility for extracting packaged image resources from PPTX files.
 *
 * <p>This follows the same no-vision-model approach as DOCX extraction. It reads
 * the OOXML package, copies every file under ppt/media, and records which slide
 * XML nodes reference each media part.
 */
public final class PptxImageExtractor {

    private static final String MEDIA_PREFIX = "ppt/media/";
    private static final int MAX_CONTEXT_CHARS = 320;

    public ExtractionResult extract(Path pptxPath, Path outputDirectory) throws IOException {
        Path input = requireRegularFile(pptxPath);
        Path output = outputDirectory.toAbsolutePath().normalize();
        Path imageOutputDirectory = output.resolve("images");
        Files.createDirectories(imageOutputDirectory);

        try (ZipFile zipFile = new ZipFile(input.toFile())) {
            List<ZipEntry> mediaEntries = mediaEntries(zipFile);
            Map<String, String> contentTypes = parseContentTypes(zipFile);
            Map<String, Map<String, Relationship>> relationships = parseRelationships(zipFile);
            List<ImageUse> imageUses = parseImageUses(zipFile, relationships);
            List<ExternalImageRef> externalImages = parseExternalImageRefs(zipFile, relationships);
            Map<String, List<ImageUse>> usesByPackagePath = indexUsesByPackagePath(imageUses);

            List<ImageAsset> assets = new ArrayList<>();
            int outputIndex = 1;
            for (ZipEntry entry : mediaEntries) {
                String packagePath = normalizePackagePath(entry.getName());
                byte[] bytes = readEntry(zipFile, entry);
                String extension = extension(packagePath);
                String outputFileName = "%03d-%s".formatted(outputIndex++, safeFileName(fileName(packagePath)));
                Path outputPath = imageOutputDirectory.resolve(outputFileName);
                Files.write(outputPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                ImageDimensions dimensions = readDimensions(bytes);
                List<ImageUse> uses = usesByPackagePath.getOrDefault(packagePath, List.of());
                assets.add(new ImageAsset(
                        packagePath,
                        output.relativize(outputPath).toString().replace('\\', '/'),
                        fileName(packagePath),
                        extension,
                        contentType(packagePath, contentTypes),
                        bytes.length,
                        HashUtils.sha256Hex(bytes),
                        dimensions.width(),
                        dimensions.height(),
                        dimensions.readable(),
                        isVectorLike(extension),
                        !uses.isEmpty(),
                        uses.size(),
                        List.copyOf(uses)
                ));
            }

            return new ExtractionResult(
                    input,
                    output,
                    List.copyOf(assets),
                    List.copyOf(imageUses),
                    List.copyOf(externalImages)
            );
        }
    }

    private Path requireRegularFile(Path input) {
        if (input == null) {
            throw new IllegalArgumentException("pptxPath is required");
        }
        Path normalized = input.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("PPTX file does not exist: " + normalized);
        }
        return normalized;
    }

    private List<ZipEntry> mediaEntries(ZipFile zipFile) {
        return zipFile.stream()
                .map(ZipEntry.class::cast)
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> normalizePackagePath(entry.getName()).startsWith(MEDIA_PREFIX))
                .sorted(Comparator.comparing(entry -> normalizePackagePath(entry.getName()).toLowerCase(Locale.ROOT)))
                .toList();
    }

    private Map<String, String> parseContentTypes(ZipFile zipFile) throws IOException {
        ZipEntry entry = zipFile.getEntry("[Content_Types].xml");
        if (entry == null) {
            return Map.of();
        }
        Document document = parseXml(readEntryString(zipFile, entry));
        Map<String, String> out = new LinkedHashMap<>();
        NodeList defaults = document.getElementsByTagNameNS("*", "Default");
        for (int i = 0; i < defaults.getLength(); i++) {
            Element element = (Element) defaults.item(i);
            String extension = trimToNull(element.getAttribute("Extension"));
            String contentType = trimToNull(element.getAttribute("ContentType"));
            if (extension != null && contentType != null) {
                out.put("ext:" + extension.toLowerCase(Locale.ROOT), contentType);
            }
        }
        NodeList overrides = document.getElementsByTagNameNS("*", "Override");
        for (int i = 0; i < overrides.getLength(); i++) {
            Element element = (Element) overrides.item(i);
            String partName = trimToNull(element.getAttribute("PartName"));
            String contentType = trimToNull(element.getAttribute("ContentType"));
            if (partName != null && contentType != null) {
                out.put("part:" + normalizePackagePath(partName), contentType);
            }
        }
        return Map.copyOf(out);
    }

    private Map<String, Map<String, Relationship>> parseRelationships(ZipFile zipFile) throws IOException {
        Map<String, Map<String, Relationship>> out = new LinkedHashMap<>();
        List<ZipEntry> relEntries = zipFile.stream()
                .map(ZipEntry.class::cast)
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> normalizePackagePath(entry.getName()).endsWith(".rels"))
                .sorted(Comparator.comparing(entry -> normalizePackagePath(entry.getName()).toLowerCase(Locale.ROOT)))
                .toList();
        for (ZipEntry entry : relEntries) {
            String relsPath = normalizePackagePath(entry.getName());
            String sourcePart = sourcePartForRelsPath(relsPath);
            if (sourcePart == null || sourcePart.isBlank()) {
                continue;
            }
            Document document = parseXml(readEntryString(zipFile, entry));
            NodeList nodes = document.getElementsByTagNameNS("*", "Relationship");
            Map<String, Relationship> byId = new LinkedHashMap<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String id = trimToNull(element.getAttribute("Id"));
                String type = trimToNull(element.getAttribute("Type"));
                String target = trimToNull(element.getAttribute("Target"));
                String targetMode = trimToNull(element.getAttribute("TargetMode"));
                if (id == null || target == null) {
                    continue;
                }
                boolean external = "external".equalsIgnoreCase(targetMode);
                String resolvedTarget = external ? target : resolvePackageTarget(sourcePart, target);
                byId.put(id, new Relationship(id, type, target, resolvedTarget, external));
            }
            if (!byId.isEmpty()) {
                out.put(sourcePart, Map.copyOf(byId));
            }
        }
        return Map.copyOf(out);
    }

    private List<ImageUse> parseImageUses(ZipFile zipFile,
                                          Map<String, Map<String, Relationship>> relationships) throws IOException {
        List<ImageUse> out = new ArrayList<>();
        List<ZipEntry> xmlEntries = presentationXmlEntries(zipFile);
        int index = 1;
        for (ZipEntry entry : xmlEntries) {
            String sourcePart = normalizePackagePath(entry.getName());
            Document document = parseXml(readEntryString(zipFile, entry));
            List<ImageUse> partUses = new ArrayList<>();
            collectImageUses(document.getDocumentElement(), sourcePart, relationships, partUses);
            for (ImageUse use : partUses) {
                out.add(new ImageUse(
                        index++,
                        use.sourcePart(),
                        use.slideNumber(),
                        use.relationshipId(),
                        use.targetPath(),
                        use.packagePath(),
                        use.shapeName(),
                        use.shapeTitle(),
                        use.shapeDescription(),
                        use.nearbyText()
                ));
            }
        }
        return List.copyOf(out);
    }

    private List<ExternalImageRef> parseExternalImageRefs(ZipFile zipFile,
                                                          Map<String, Map<String, Relationship>> relationships) throws IOException {
        List<ExternalImageRef> out = new ArrayList<>();
        List<ZipEntry> xmlEntries = presentationXmlEntries(zipFile);
        int index = 1;
        for (ZipEntry entry : xmlEntries) {
            String sourcePart = normalizePackagePath(entry.getName());
            Document document = parseXml(readEntryString(zipFile, entry));
            List<ExternalImageRef> partRefs = new ArrayList<>();
            collectExternalImageRefs(document.getDocumentElement(), sourcePart, relationships, partRefs);
            for (ExternalImageRef ref : partRefs) {
                out.add(new ExternalImageRef(index++, ref.sourcePart(), ref.slideNumber(), ref.relationshipId(), ref.targetUrl()));
            }
        }
        return List.copyOf(out);
    }

    private List<ZipEntry> presentationXmlEntries(ZipFile zipFile) {
        return zipFile.stream()
                .map(ZipEntry.class::cast)
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> {
                    String path = normalizePackagePath(entry.getName());
                    return path.startsWith("ppt/")
                            && path.endsWith(".xml")
                            && !path.startsWith(MEDIA_PREFIX)
                            && !path.contains("/_rels/");
                })
                .sorted(Comparator.comparing(entry -> presentationPartWeight(normalizePackagePath(entry.getName()))))
                .toList();
    }

    private String presentationPartWeight(String path) {
        int weight;
        if (path.startsWith("ppt/slides/slide")) {
            weight = 0;
        } else if (path.startsWith("ppt/notesSlides/notesSlide")) {
            weight = 1;
        } else if (path.startsWith("ppt/slideLayouts/slideLayout")) {
            weight = 2;
        } else if (path.startsWith("ppt/slideMasters/slideMaster")) {
            weight = 3;
        } else {
            weight = 10;
        }
        return "%02d:%08d:%s".formatted(weight, numericSuffix(fileName(path)), path.toLowerCase(Locale.ROOT));
    }

    private void collectImageUses(Node node,
                                  String sourcePart,
                                  Map<String, Map<String, Relationship>> relationships,
                                  List<ImageUse> out) {
        if (node instanceof Element element) {
            String relId = imageRelationshipId(element);
            if (relId != null) {
                Relationship relationship = relationships.getOrDefault(sourcePart, Map.of()).get(relId);
                if (relationship != null && !relationship.external()
                        && relationship.targetPath() != null
                        && relationship.targetPath().startsWith(MEDIA_PREFIX)) {
                    NearbyImageText text = nearbyImageText(element);
                    out.add(new ImageUse(
                            0,
                            sourcePart,
                            slideNumber(sourcePart),
                            relId,
                            relationship.target(),
                            relationship.targetPath(),
                            text.name(),
                            text.title(),
                            text.description(),
                            text.nearbyText()
                    ));
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectImageUses(children.item(i), sourcePart, relationships, out);
        }
    }

    private void collectExternalImageRefs(Node node,
                                          String sourcePart,
                                          Map<String, Map<String, Relationship>> relationships,
                                          List<ExternalImageRef> out) {
        if (node instanceof Element element) {
            String relId = imageRelationshipId(element);
            if (relId != null) {
                Relationship relationship = relationships.getOrDefault(sourcePart, Map.of()).get(relId);
                if (relationship != null && relationship.external()) {
                    out.add(new ExternalImageRef(0, sourcePart, slideNumber(sourcePart), relId, relationship.targetPath()));
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectExternalImageRefs(children.item(i), sourcePart, relationships, out);
        }
    }

    private String imageRelationshipId(Element element) {
        String localName = localName(element);
        if ("blip".equals(localName)) {
            return firstNonBlank(
                    element.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed"),
                    element.getAttribute("r:embed"),
                    element.getAttribute("embed"),
                    element.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "link"),
                    element.getAttribute("r:link"),
                    element.getAttribute("link")
            );
        }
        if ("imagedata".equals(localName)) {
            return firstNonBlank(
                    element.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"),
                    element.getAttribute("r:id"),
                    element.getAttribute("id")
            );
        }
        return null;
    }

    private NearbyImageText nearbyImageText(Element imageElement) {
        Element metadata = nearestDescendantOnAncestorPath(imageElement, "cNvPr");
        String name = metadata == null ? null : trimToNull(metadata.getAttribute("name"));
        String title = metadata == null ? null : trimToNull(metadata.getAttribute("title"));
        String description = metadata == null ? null : firstNonBlank(
                metadata.getAttribute("descr"),
                metadata.getAttribute("description")
        );

        Element spTree = nearestAncestor(imageElement, "spTree");
        Element fallback = spTree == null ? nearestDocumentElement(imageElement) : spTree;
        String nearbyText = sanitizeContextText(fallback == null ? "" : collectText(fallback));
        return new NearbyImageText(name, title, description, nearbyText);
    }

    private Element nearestAncestor(Node node, String localName) {
        Node current = node;
        while (current != null) {
            if (current instanceof Element element && localName.equals(localName(element))) {
                return element;
            }
            current = current.getParentNode();
        }
        return null;
    }

    private Element nearestDocumentElement(Node node) {
        Node current = node;
        while (current != null && current.getParentNode() != null) {
            current = current.getParentNode();
        }
        return current instanceof Document document ? document.getDocumentElement()
                : current instanceof Element element ? element
                : null;
    }

    private Element nearestDescendantOnAncestorPath(Node node, String targetLocalName) {
        Node current = node;
        while (current != null) {
            if (current instanceof Element element) {
                Element found = firstDescendant(element, targetLocalName);
                if (found != null) {
                    return found;
                }
                if ("spTree".equals(localName(element))) {
                    return null;
                }
            }
            current = current.getParentNode();
        }
        return null;
    }

    private Element firstDescendant(Node node, String targetLocalName) {
        if (node instanceof Element element && targetLocalName.equals(localName(element))) {
            return element;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element found = firstDescendant(children.item(i), targetLocalName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String collectText(Node node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        return sb.toString();
    }

    private void collectText(Node node, StringBuilder sb) {
        if (node instanceof Element element) {
            String localName = localName(element);
            if ("t".equals(localName) || "instrText".equals(localName)) {
                sb.append(element.getTextContent()).append(' ');
            } else if ("tab".equals(localName) || "br".equals(localName)) {
                sb.append(' ');
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectText(children.item(i), sb);
        }
    }

    private Map<String, List<ImageUse>> indexUsesByPackagePath(List<ImageUse> imageUses) {
        Map<String, List<ImageUse>> out = new LinkedHashMap<>();
        for (ImageUse use : imageUses) {
            out.computeIfAbsent(use.packagePath(), ignored -> new ArrayList<>()).add(use);
        }
        return out;
    }

    private String contentType(String packagePath, Map<String, String> contentTypes) {
        String part = contentTypes.get("part:" + normalizePackagePath(packagePath));
        if (part != null) {
            return part;
        }
        String byExtension = contentTypes.get("ext:" + extension(packagePath));
        if (byExtension != null) {
            return byExtension;
        }
        return switch (extension(packagePath)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "emf" -> "image/x-emf";
            case "wmf" -> "image/x-wmf";
            default -> "application/octet-stream";
        };
    }

    private ImageDimensions readDimensions(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return new ImageDimensions(null, null, false);
            }
            return new ImageDimensions(image.getWidth(), image.getHeight(), true);
        } catch (Exception e) {
            return new ImageDimensions(null, null, false);
        }
    }

    private boolean isVectorLike(String extension) {
        return Set.of("svg", "emf", "wmf").contains(Objects.toString(extension, "").toLowerCase(Locale.ROOT));
    }

    private Document parseXml(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            setXmlFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setXmlFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            setXmlFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IOException("Failed to parse PPTX XML", e);
        }
    }

    private void setXmlFeature(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
        }
    }

    private byte[] readEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (InputStream in = zipFile.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private String readEntryString(ZipFile zipFile, ZipEntry entry) throws IOException {
        return new String(readEntry(zipFile, entry), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String sourcePartForRelsPath(String relsPath) {
        String normalized = normalizePackagePath(relsPath);
        int marker = normalized.lastIndexOf("/_rels/");
        if (marker < 0) {
            return null;
        }
        String prefix = normalized.substring(0, marker);
        String relFile = normalized.substring(marker + "/_rels/".length());
        if (!relFile.endsWith(".rels")) {
            return null;
        }
        relFile = relFile.substring(0, relFile.length() - ".rels".length());
        if (relFile.isBlank()) {
            return null;
        }
        return prefix.isBlank() ? relFile : prefix + "/" + relFile;
    }

    private String resolvePackageTarget(String sourcePart, String target) {
        String rawTarget = Objects.toString(target, "").replace('\\', '/').trim();
        if (rawTarget.startsWith("/")) {
            return normalizePackagePath(rawTarget.substring(1));
        }
        String sourceDirectory = "";
        int slash = sourcePart.lastIndexOf('/');
        if (slash >= 0) {
            sourceDirectory = sourcePart.substring(0, slash + 1);
        }
        return normalizePackagePath(sourceDirectory + rawTarget);
    }

    private String normalizePackagePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = sanitizeUtf16(value).replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String[] parts = normalized.split("/");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!out.isEmpty()) {
                    out.remove(out.size() - 1);
                }
                continue;
            }
            out.add(part);
        }
        return String.join("/", out);
    }

    private String fileName(String packagePath) {
        String normalized = normalizePackagePath(packagePath);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String extension(String packagePath) {
        String name = fileName(packagePath);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String safeFileName(String value) {
        String fileName = sanitizeUtf16(Objects.toString(value, "image")).trim();
        if (fileName.isBlank()) {
            return "image";
        }
        String safe = fileName.replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isBlank() ? "image" : safe;
    }

    private String localName(Element element) {
        String localName = element.getLocalName();
        if (localName != null && !localName.isBlank()) {
            return localName;
        }
        String nodeName = element.getNodeName();
        int colon = nodeName.indexOf(':');
        return colon >= 0 ? nodeName.substring(colon + 1) : nodeName;
    }

    private String sanitizeContextText(String value) {
        String normalized = sanitizeUtf16(Objects.toString(value, "")).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_CONTEXT_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_CONTEXT_CHARS).trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = sanitizeUtf16(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private Integer slideNumber(String sourcePart) {
        String normalized = normalizePackagePath(sourcePart);
        if (!normalized.startsWith("ppt/slides/slide") || !normalized.endsWith(".xml")) {
            return null;
        }
        int number = numericSuffix(fileName(normalized));
        return number > 0 ? number : null;
    }

    private int numericSuffix(String value) {
        String name = Objects.toString(value, "");
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        int end = name.length();
        int start = end;
        while (start > 0 && Character.isDigit(name.charAt(start - 1))) {
            start--;
        }
        if (start == end) {
            return 0;
        }
        try {
            return Integer.parseInt(name.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private record Relationship(String id,
                                String type,
                                String target,
                                String targetPath,
                                boolean external) {
    }

    private record NearbyImageText(String name,
                                   String title,
                                   String description,
                                   String nearbyText) {
    }

    private record ImageDimensions(Integer width,
                                   Integer height,
                                   boolean readable) {
    }

    public record ExtractionResult(Path pptxPath,
                                   Path outputDirectory,
                                   List<ImageAsset> images,
                                   List<ImageUse> uses,
                                   List<ExternalImageRef> externalImages) {

        public long referencedImageCount() {
            return images.stream().filter(ImageAsset::referenced).count();
        }

        public long unreferencedImageCount() {
            return images.stream().filter(asset -> !asset.referenced()).count();
        }

        public long rasterReadableCount() {
            return images.stream().filter(ImageAsset::rasterReadable).count();
        }

        public long vectorLikeCount() {
            return images.stream().filter(ImageAsset::vectorLike).count();
        }
    }

    public record ImageAsset(String packagePath,
                             String outputRelativePath,
                             String fileName,
                             String extension,
                             String contentType,
                             long sizeBytes,
                             String sha256,
                             Integer width,
                             Integer height,
                             boolean rasterReadable,
                             boolean vectorLike,
                             boolean referenced,
                             int useCount,
                             List<ImageUse> uses) {
    }

    public record ImageUse(int index,
                           String sourcePart,
                           Integer slideNumber,
                           String relationshipId,
                           String targetPath,
                           String packagePath,
                           String shapeName,
                           String shapeTitle,
                           String shapeDescription,
                           String nearbyText) {
    }

    public record ExternalImageRef(int index,
                                   String sourcePart,
                                   Integer slideNumber,
                                   String relationshipId,
                                   String targetUrl) {
    }
}
