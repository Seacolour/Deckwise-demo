package com.xiaoyuan.paperpuppybackendintl.utils.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptxImageExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsReferencedRasterUnreferencedVectorAndExternalRefsFromPptxPackage() throws Exception {
        Path pptx = tempDir.resolve("sample.pptx");
        writeSamplePptx(pptx);

        Path outputDir = tempDir.resolve("out");
        PptxImageExtractor.ExtractionResult result = new PptxImageExtractor().extract(pptx, outputDir);

        assertEquals(2, result.images().size());
        assertEquals(1, result.referencedImageCount());
        assertEquals(1, result.unreferencedImageCount());
        assertEquals(1, result.rasterReadableCount());
        assertEquals(1, result.vectorLikeCount());
        assertEquals(1, result.uses().size());
        assertEquals(1, result.externalImages().size());

        PptxImageExtractor.ImageAsset png = result.images().stream()
                .filter(asset -> "png".equals(asset.extension()))
                .findFirst()
                .orElseThrow();
        assertTrue(png.referenced());
        assertTrue(png.rasterReadable());
        assertEquals(4, png.width());
        assertEquals(5, png.height());
        assertTrue(Files.exists(outputDir.resolve(png.outputRelativePath())));
        assertEquals(1, png.uses().getFirst().slideNumber());
        assertEquals("Picture 4", png.uses().getFirst().shapeName());
        assertEquals("Sales flow", png.uses().getFirst().shapeDescription());
        assertTrue(png.uses().getFirst().nearbyText().contains("Quarterly results"));

        PptxImageExtractor.ImageAsset emf = result.images().stream()
                .filter(asset -> "emf".equals(asset.extension()))
                .findFirst()
                .orElseThrow();
        assertFalse(emf.referenced());
        assertFalse(emf.rasterReadable());
        assertTrue(emf.vectorLike());
        assertNotNull(emf.sha256());
        assertTrue(Files.exists(outputDir.resolve(emf.outputRelativePath())));

        PptxImageExtractor.ExternalImageRef external = result.externalImages().getFirst();
        assertEquals(1, external.slideNumber());
        assertEquals("rId3", external.relationshipId());
        assertEquals("https://example.com/external.png", external.targetUrl());
    }

    private void writeSamplePptx(Path pptx) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", contentTypes().getBytes(StandardCharsets.UTF_8));
        entries.put("ppt/slides/slide1.xml", slideXml().getBytes(StandardCharsets.UTF_8));
        entries.put("ppt/slides/_rels/slide1.xml.rels", slideRels().getBytes(StandardCharsets.UTF_8));
        entries.put("ppt/media/image1.png", pngBytes());
        entries.put("ppt/media/diagram1.emf", emfBytes());

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pptx))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="png" ContentType="image/png"/>
                  <Default Extension="emf" ContentType="image/x-emf"/>
                </Types>
                """;
    }

    private String slideRels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId2"
                                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                                Target="../media/image1.png"/>
                  <Relationship Id="rId3"
                                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                                Target="https://example.com/external.png"
                                TargetMode="External"/>
                </Relationships>
                """;
    }

    private String slideXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                       xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                       xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <p:cSld>
                    <p:spTree>
                      <p:sp>
                        <p:nvSpPr><p:cNvPr id="2" name="Title 1"/></p:nvSpPr>
                        <p:txBody>
                          <a:p><a:r><a:t>Quarterly results and operating model</a:t></a:r></a:p>
                        </p:txBody>
                      </p:sp>
                      <p:pic>
                        <p:nvPicPr>
                          <p:cNvPr id="4" name="Picture 4" descr="Sales flow"/>
                        </p:nvPicPr>
                        <p:blipFill>
                          <a:blip r:embed="rId2"/>
                        </p:blipFill>
                      </p:pic>
                      <p:pic>
                        <p:nvPicPr>
                          <p:cNvPr id="5" name="Linked Picture 5"/>
                        </p:nvPicPr>
                        <p:blipFill>
                          <a:blip r:link="rId3"/>
                        </p:blipFill>
                      </p:pic>
                    </p:spTree>
                  </p:cSld>
                </p:sld>
                """;
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 5, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(3, 4, Color.BLUE.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] emfBytes() {
        return "fake-emf-vector-resource".getBytes(StandardCharsets.UTF_8);
    }
}
