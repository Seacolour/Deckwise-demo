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
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxImageExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsReferencedRasterAndUnreferencedVectorMediaFromDocxPackage() throws Exception {
        Path docx = tempDir.resolve("sample.docx");
        writeSampleDocx(docx);

        Path outputDir = tempDir.resolve("out");
        DocxImageExtractor.ExtractionResult result = new DocxImageExtractor().extract(docx, outputDir);

        assertEquals(2, result.images().size());
        assertEquals(1, result.referencedImageCount());
        assertEquals(1, result.unreferencedImageCount());
        assertEquals(1, result.rasterReadableCount());
        assertEquals(1, result.vectorLikeCount());
        assertEquals(1, result.uses().size());

        DocxImageExtractor.ImageAsset png = result.images().stream()
                .filter(asset -> "png".equals(asset.extension()))
                .findFirst()
                .orElseThrow();
        assertTrue(png.referenced());
        assertTrue(png.rasterReadable());
        assertEquals(2, png.width());
        assertEquals(3, png.height());
        assertTrue(Files.exists(outputDir.resolve(png.outputRelativePath())));
        assertEquals("Picture 1", png.uses().getFirst().docPrName());
        assertEquals("Architecture diagram", png.uses().getFirst().docPrDescription());
        assertTrue(png.uses().getFirst().paragraphText().contains("Diagram context"));

        DocxImageExtractor.ImageAsset svg = result.images().stream()
                .filter(asset -> "svg".equals(asset.extension()))
                .findFirst()
                .orElseThrow();
        assertFalse(svg.referenced());
        assertFalse(svg.rasterReadable());
        assertTrue(svg.vectorLike());
        assertNotNull(svg.sha256());
        assertTrue(Files.exists(outputDir.resolve(svg.outputRelativePath())));
    }

    private void writeSampleDocx(Path docx) throws Exception {
        Map<String, byte[]> entries = Map.of(
                "[Content_Types].xml", contentTypes().getBytes(StandardCharsets.UTF_8),
                "word/document.xml", documentXml().getBytes(StandardCharsets.UTF_8),
                "word/_rels/document.xml.rels", documentRels().getBytes(StandardCharsets.UTF_8),
                "word/media/image1.png", pngBytes(),
                "word/media/vector1.svg", svgBytes()
        );
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(docx))) {
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
                  <Default Extension="svg" ContentType="image/svg+xml"/>
                </Types>
                """;
    }

    private String documentRels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId5"
                                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
                                Target="media/image1.png"/>
                </Relationships>
                """;
    }

    private String documentXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <w:body>
                    <w:p>
                      <w:r><w:t>Diagram context before image</w:t></w:r>
                      <w:r>
                        <w:drawing>
                          <wp:inline>
                            <wp:docPr id="1" name="Picture 1" descr="Architecture diagram"/>
                            <a:graphic>
                              <a:graphicData>
                                <pic:pic>
                                  <pic:blipFill>
                                    <a:blip r:embed="rId5"/>
                                  </pic:blipFill>
                                </pic:pic>
                              </a:graphicData>
                            </a:graphic>
                          </wp:inline>
                        </w:drawing>
                      </w:r>
                    </w:p>
                  </w:body>
                </w:document>
                """;
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 2, Color.BLUE.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] svgBytes() {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
                  <circle cx="5" cy="5" r="4" fill="red"/>
                </svg>
                """.getBytes(StandardCharsets.UTF_8);
    }
}
