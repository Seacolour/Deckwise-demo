package com.xiaoyuan.paperpuppybackendintl.service.file.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.PdfDerivedAssetsProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisClient;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.redis.RedisKey;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.model.entity.PdfDerivedMediaAssetCacheEntry;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageChannel;
import com.xiaoyuan.paperpuppybackendintl.model.enums.StorageVisibility;
import com.xiaoyuan.paperpuppybackendintl.repository.FileAccessGrantRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.FileDerivedMediaAssetRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.PdfDerivedMediaAssetCacheRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.service.file.jgda.JgdaPdfAssetClient;
import com.xiaoyuan.paperpuppybackendintl.service.file.jgda.JgdaPdfAssetExtractionResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfDerivedMediaAssetExtractionServiceImplTest {

    @Test
    void requestExtractionSkipsOpenAiWorkWhenSourceFileIsAlreadyLocked() throws Exception {
        Fixture fixture = new Fixture();
        ObjectStorageFile source = pdfFile(387L);
        String lockPrefix = RedisKey.lock("pdf-derived-assets:36:");
        when(fixture.assetRepository().countAvailableByProjectAndSourceFileId(218L, 387L)).thenReturn(0);
        when(fixture.redisClient().setIfAbsent(startsWith(lockPrefix), anyString(), eq(30L), eq(TimeUnit.MINUTES)))
                .thenReturn(false);

        boolean queued = fixture.service().requestExtractionAfterCommit(
                36L,
                218L,
                source,
                new byte[]{1, 2, 3},
                "brief.pdf");

        assertTrue(queued);
        verify(fixture.jgdaPdfAssetClient(), never()).extract(anyString(), aryEq(new byte[]{1, 2, 3}));
        verify(fixture.redisClient(), never()).deleteIfValue(startsWith(lockPrefix), anyString());
    }

    @Test
    void requestExtractionReleasesLockAfterExtractionCompletes() throws Exception {
        Fixture fixture = new Fixture();
        ObjectStorageFile source = pdfFile(387L);
        String lockPrefix = RedisKey.lock("pdf-derived-assets:36:");
        when(fixture.assetRepository().countAvailableByProjectAndSourceFileId(218L, 387L)).thenReturn(0);
        when(fixture.redisClient().setIfAbsent(startsWith(lockPrefix), anyString(), eq(30L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(fixture.jgdaPdfAssetClient().extract(eq("brief.pdf"), aryEq(new byte[]{1, 2, 3})))
                .thenReturn(new JgdaPdfAssetExtractionResult());

        boolean queued = fixture.service().requestExtractionAfterCommit(
                36L,
                218L,
                source,
                new byte[]{1, 2, 3},
                "brief.pdf");

        assertTrue(queued);
        verify(fixture.jgdaPdfAssetClient()).extract(eq("brief.pdf"), aryEq(new byte[]{1, 2, 3}));
        verify(fixture.redisClient()).set(
                startsWith("pp:intl:derived-media:extraction:pdf:218:387:"),
                eq("empty"),
                eq(7L),
                eq(TimeUnit.DAYS));
        verify(fixture.redisClient()).deleteIfValue(startsWith(lockPrefix), anyString());
    }

    @Test
    void requestExtractionLinksCachedAssetsAndSkipsExtractor() throws Exception {
        Fixture fixture = new Fixture();
        ObjectStorageFile source = pdfFile(387L);
        PdfDerivedMediaAssetCacheEntry cached = cachedAsset(918L, 612L);
        when(fixture.assetRepository().countAvailableByProjectAndSourceFileId(218L, 387L)).thenReturn(0);
        when(fixture.cacheRepository().findAvailableByOwnerHashAndSignature(eq(36L), anyString(), anyString(), eq(4)))
                .thenReturn(List.of(cached));

        boolean queued = fixture.service().requestExtractionAfterCommit(
                36L,
                218L,
                source,
                new byte[]{1, 2, 3},
                "brief.pdf");

        assertFalse(queued);
        verify(fixture.projectFileLinkRepository()).upsertLink(218L, 612L, 36L, "pdf-figure.png", "image");
        verify(fixture.fileAccessGrantRepository()).upsertLink(36L, 612L, 218L);
        verify(fixture.cacheRepository()).linkAssetsToProject(eq(218L), eq(387L), eq(36L), anyCollection());
        verify(fixture.jgdaPdfAssetClient(), never()).extract(anyString(), aryEq(new byte[]{1, 2, 3}));
    }

    @Test
    void requestDocxExtractionPersistsRasterImagesAndFiltersEmf() throws Exception {
        Fixture fixture = new Fixture();
        ObjectStorageFile source = docxFile(387L);
        byte[] docxBytes = sampleDocxWithPngAndEmf();
        String lockPrefix = RedisKey.lock("docx-derived-assets:36:");
        when(fixture.assetRepository().countAvailableByProjectAndSourceFileId(218L, 387L)).thenReturn(0);
        when(fixture.projectFileLinkRepository().existsActiveLink(218L, 387L)).thenReturn(true);
        when(fixture.objectStorageFileRepository().findById(387L)).thenReturn(java.util.Optional.of(source));
        when(fixture.r2Client().downloadBytes(StorageChannel.PUBLIC, "uploads/source.docx")).thenReturn(docxBytes);
        when(fixture.redisClient().setIfAbsent(startsWith(lockPrefix), anyString(), eq(30L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(fixture.objectStorageFileRepository().findAvailableByOwnerSourceAndChannel(eq(36L), eq("derived"), anyString(), eq("private")))
                .thenReturn(java.util.Optional.empty());
        when(fixture.objectStorageFileRepository().findByOwnerSourceAndChannel(eq(36L), eq("derived"), anyString(), eq("private")))
                .thenReturn(java.util.Optional.empty());
        when(fixture.objectStorageFileRepository().insert(any(ObjectStorageFile.class))).thenAnswer(invocation -> {
            ObjectStorageFile file = invocation.getArgument(0, ObjectStorageFile.class);
            file.setId(612L);
            return file;
        });
        when(fixture.cacheRepository().insertAvailable(any(PdfDerivedMediaAssetCacheEntry.class))).thenAnswer(invocation -> {
            PdfDerivedMediaAssetCacheEntry entry = invocation.getArgument(0, PdfDerivedMediaAssetCacheEntry.class);
            entry.setId(918L);
            entry.setDerivedFileName("image1.png");
            return java.util.Optional.of(entry);
        });

        boolean queued = fixture.service().requestDocxExtractionAfterCommit(
                36L, 218L, source, docxBytes, "brief.docx");

        assertTrue(queued);
        verify(fixture.r2Client(), times(1)).uploadWithMeta(eq(StorageChannel.PRIVATE), anyString(), any(byte[].class), eq("image/png"));
        verify(fixture.objectStorageFileRepository()).insert(argThat(file ->
                StorageChannel.PRIVATE.value().equals(file.getStorageChannel())
                        && StorageVisibility.PRIVATE.value().equals(file.getVisibility())));
        verify(fixture.cacheRepository()).insertAvailable(argThat(entry ->
                "docx_embedded_image".equals(entry.getKind())
                        && "docx_package_media".equals(entry.getProvider())
                        && Integer.valueOf(1).equals(entry.getPageNumber())
                        && Integer.valueOf(1).equals(entry.getAssetIndex())));
        verify(fixture.projectFileLinkRepository()).upsertLink(218L, 612L, 36L, "image1.png", "image");
        verify(fixture.fileAccessGrantRepository()).upsertLink(36L, 612L, 218L);
        verify(fixture.redisClient()).deleteIfValue(startsWith(lockPrefix), anyString());
    }

    @Test
    void requestPptxExtractionPersistsRasterImagesAndFiltersEmf() throws Exception {
        Fixture fixture = new Fixture();
        ObjectStorageFile source = pptxFile(388L);
        byte[] pptxBytes = samplePptxWithPngAndEmf();
        String lockPrefix = RedisKey.lock("pptx-derived-assets:36:");
        when(fixture.assetRepository().countAvailableByProjectAndSourceFileId(218L, 388L)).thenReturn(0);
        when(fixture.projectFileLinkRepository().existsActiveLink(218L, 388L)).thenReturn(true);
        when(fixture.objectStorageFileRepository().findById(388L)).thenReturn(java.util.Optional.of(source));
        when(fixture.r2Client().downloadBytes(StorageChannel.PUBLIC, "uploads/source.pptx")).thenReturn(pptxBytes);
        when(fixture.redisClient().setIfAbsent(startsWith(lockPrefix), anyString(), eq(30L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(fixture.objectStorageFileRepository().findAvailableByOwnerSourceAndChannel(eq(36L), eq("derived"), anyString(), eq("private")))
                .thenReturn(java.util.Optional.empty());
        when(fixture.objectStorageFileRepository().findByOwnerSourceAndChannel(eq(36L), eq("derived"), anyString(), eq("private")))
                .thenReturn(java.util.Optional.empty());
        when(fixture.objectStorageFileRepository().insert(any(ObjectStorageFile.class))).thenAnswer(invocation -> {
            ObjectStorageFile file = invocation.getArgument(0, ObjectStorageFile.class);
            file.setId(613L);
            return file;
        });
        when(fixture.cacheRepository().insertAvailable(any(PdfDerivedMediaAssetCacheEntry.class))).thenAnswer(invocation -> {
            PdfDerivedMediaAssetCacheEntry entry = invocation.getArgument(0, PdfDerivedMediaAssetCacheEntry.class);
            entry.setId(919L);
            entry.setDerivedFileName("slide-image.png");
            return java.util.Optional.of(entry);
        });

        boolean queued = fixture.service().requestPptxExtractionAfterCommit(
                36L, 218L, source, pptxBytes, "deck.pptx");

        assertTrue(queued);
        verify(fixture.r2Client(), times(1)).uploadWithMeta(eq(StorageChannel.PRIVATE), anyString(), any(byte[].class), eq("image/png"));
        verify(fixture.objectStorageFileRepository()).insert(argThat(file ->
                StorageChannel.PRIVATE.value().equals(file.getStorageChannel())
                        && StorageVisibility.PRIVATE.value().equals(file.getVisibility())));
        verify(fixture.cacheRepository()).insertAvailable(argThat(entry ->
                "pptx_embedded_image".equals(entry.getKind())
                        && "pptx_package_media".equals(entry.getProvider())
                        && Integer.valueOf(1).equals(entry.getPageNumber())
                        && Integer.valueOf(1).equals(entry.getAssetIndex())
                        && "Sales flow".equals(entry.getDescription())));
        verify(fixture.projectFileLinkRepository()).upsertLink(218L, 613L, 36L, "slide-image.png", "image");
        verify(fixture.fileAccessGrantRepository()).upsertLink(36L, 613L, 218L);
        verify(fixture.redisClient()).deleteIfValue(startsWith(lockPrefix), anyString());
    }

    @Test
    void requestPptxExtractionPrioritizesProminentImagesOverFragmentImages() throws Exception {
        Fixture fixture = new Fixture();
        ObjectStorageFile source = pptxFile(389L);
        byte[] pptxBytes = samplePptxWithSmallAndLargePng();
        String lockPrefix = RedisKey.lock("pptx-derived-assets:36:");
        when(fixture.assetRepository().countAvailableByProjectAndSourceFileId(218L, 389L)).thenReturn(0);
        when(fixture.projectFileLinkRepository().existsActiveLink(218L, 389L)).thenReturn(true);
        when(fixture.objectStorageFileRepository().findById(389L)).thenReturn(java.util.Optional.of(source));
        when(fixture.r2Client().downloadBytes(StorageChannel.PUBLIC, "uploads/source.pptx")).thenReturn(pptxBytes);
        when(fixture.redisClient().setIfAbsent(startsWith(lockPrefix), anyString(), eq(30L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(fixture.objectStorageFileRepository().findAvailableByOwnerSourceAndChannel(eq(36L), eq("derived"), anyString(), eq("private")))
                .thenReturn(java.util.Optional.empty());
        when(fixture.objectStorageFileRepository().findByOwnerSourceAndChannel(eq(36L), eq("derived"), anyString(), eq("private")))
                .thenReturn(java.util.Optional.empty());
        when(fixture.objectStorageFileRepository().insert(any(ObjectStorageFile.class))).thenAnswer(invocation -> {
            ObjectStorageFile file = invocation.getArgument(0, ObjectStorageFile.class);
            file.setId(614L);
            return file;
        });
        when(fixture.cacheRepository().insertAvailable(any(PdfDerivedMediaAssetCacheEntry.class))).thenAnswer(invocation -> {
            PdfDerivedMediaAssetCacheEntry entry = invocation.getArgument(0, PdfDerivedMediaAssetCacheEntry.class);
            entry.setId(920L);
            entry.setDerivedFileName("hero-image.png");
            return java.util.Optional.of(entry);
        });

        boolean queued = fixture.service().requestPptxExtractionAfterCommit(
                36L, 218L, source, pptxBytes, "deck.pptx");

        var entryCaptor = forClass(PdfDerivedMediaAssetCacheEntry.class);
        assertTrue(queued);
        verify(fixture.r2Client(), times(1)).uploadWithMeta(eq(StorageChannel.PRIVATE), anyString(), any(byte[].class), eq("image/png"));
        verify(fixture.objectStorageFileRepository()).insert(argThat(file ->
                StorageChannel.PRIVATE.value().equals(file.getStorageChannel())
                        && StorageVisibility.PRIVATE.value().equals(file.getVisibility())));
        verify(fixture.cacheRepository(), times(1)).insertAvailable(entryCaptor.capture());
        assertEquals("Hero screen", entryCaptor.getValue().getDescription());
        assertEquals(Integer.valueOf(1), entryCaptor.getValue().getAssetIndex());
        verify(fixture.projectFileLinkRepository()).upsertLink(218L, 614L, 36L, "hero-image.png", "image");
        verify(fixture.fileAccessGrantRepository()).upsertLink(36L, 614L, 218L);
        verify(fixture.redisClient()).deleteIfValue(startsWith(lockPrefix), anyString());
    }

    private static ObjectStorageFile pdfFile(Long fileId) {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(fileId);
        file.setOwnerUserId(36L);
        file.setFileExt("pdf");
        file.setMimeType("application/pdf");
        file.setOriginalName("brief.pdf");
        file.setStatus("available");
        file.setDeleted(false);
        return file;
    }

    private static ObjectStorageFile docxFile(Long fileId) {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(fileId);
        file.setOwnerUserId(36L);
        file.setFileExt("docx");
        file.setMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        file.setOriginalName("brief.docx");
        file.setObjectKey("uploads/source.docx");
        file.setStatus("available");
        file.setDeleted(false);
        return file;
    }

    private static ObjectStorageFile pptxFile(Long fileId) {
        ObjectStorageFile file = new ObjectStorageFile();
        file.setId(fileId);
        file.setOwnerUserId(36L);
        file.setFileExt("pptx");
        file.setMimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        file.setOriginalName("deck.pptx");
        file.setObjectKey("uploads/source.pptx");
        file.setStatus("available");
        file.setDeleted(false);
        return file;
    }




    private static PdfDerivedMediaAssetCacheEntry cachedAsset(Long cacheId, Long derivedFileId) {
        PdfDerivedMediaAssetCacheEntry entry = new PdfDerivedMediaAssetCacheEntry();
        entry.setId(cacheId);
        entry.setDerivedFileId(derivedFileId);
        entry.setDerivedFileName("pdf-figure.png");
        entry.setStatus("available");
        entry.setDeleted(false);
        return entry;
    }

    private static byte[] sampleDocxWithPngAndEmf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Default Extension="png" ContentType="image/png"/>
                      <Default Extension="emf" ContentType="image/x-emf"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                                xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                      <w:body>
                        <w:p>
                          <w:r><w:t>Chart context</w:t></w:r>
                          <w:r><w:drawing><wp:inline><wp:docPr id="1" name="Picture 1" descr="Revenue chart"/>
                            <a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed="rId1"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>
                          </wp:inline></w:drawing></w:r>
                          <w:r><w:drawing><wp:inline><wp:docPr id="2" name="Vector 1" descr="Flowchart"/>
                            <a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed="rId2"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>
                          </wp:inline></w:drawing></w:r>
                        </w:p>
                      </w:body>
                    </w:document>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "word/_rels/document.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image2.emf"/>
                    </Relationships>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "word/media/image1.png", pngBytes());
            writeZipEntry(zip, "word/media/image2.emf", new byte[]{1, 2, 3, 4});
        }
        return out.toByteArray();
    }

    private static byte[] samplePptxWithPngAndEmf() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Default Extension="png" ContentType="image/png"/>
                      <Default Extension="emf" ContentType="image/x-emf"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "ppt/slides/slide1.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                           xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <p:cSld>
                        <p:spTree>
                          <p:sp>
                            <p:nvSpPr><p:cNvPr id="2" name="Title 1"/></p:nvSpPr>
                            <p:txBody><a:p><a:r><a:t>Interview preparation workflow</a:t></a:r></a:p></p:txBody>
                          </p:sp>
                          <p:pic>
                            <p:nvPicPr><p:cNvPr id="4" name="Picture 4" descr="Sales flow"/></p:nvPicPr>
                            <p:blipFill><a:blip r:embed="rId1"/></p:blipFill>
                          </p:pic>
                          <p:pic>
                            <p:nvPicPr><p:cNvPr id="5" name="Vector 5" descr="Vector flow"/></p:nvPicPr>
                            <p:blipFill><a:blip r:embed="rId2"/></p:blipFill>
                          </p:pic>
                        </p:spTree>
                      </p:cSld>
                    </p:sld>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "ppt/slides/_rels/slide1.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image2.emf"/>
                    </Relationships>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "ppt/media/image1.png", pngBytes(640, 640));
            writeZipEntry(zip, "ppt/media/image2.emf", new byte[]{1, 2, 3, 4});
        }
        return out.toByteArray();
    }

    private static byte[] samplePptxWithSmallAndLargePng() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Default Extension="png" ContentType="image/png"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "ppt/slides/slide1.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                           xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                           xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <p:cSld>
                        <p:spTree>
                          <p:pic>
                            <p:nvPicPr><p:cNvPr id="4" name="Tiny Icon" descr="Tiny fragment"/></p:nvPicPr>
                            <p:blipFill><a:blip r:embed="rId1"/></p:blipFill>
                          </p:pic>
                          <p:pic>
                            <p:nvPicPr><p:cNvPr id="5" name="Hero Picture" descr="Hero screen"/></p:nvPicPr>
                            <p:blipFill><a:blip r:embed="rId2"/></p:blipFill>
                          </p:pic>
                        </p:spTree>
                      </p:cSld>
                    </p:sld>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "ppt/slides/_rels/slide1.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image2.png"/>
                    </Relationships>
                    """.getBytes(StandardCharsets.UTF_8));
            writeZipEntry(zip, "ppt/media/image1.png", pngBytes(64, 64));
            writeZipEntry(zip, "ppt/media/image2.png", pngBytes(640, 640));
        }
        return out.toByteArray();
    }

    private static void writeZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] pngBytes() throws Exception {
        return pngBytes(64, 64);
    }

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(width - 1, height - 1, Color.BLUE.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static final class Fixture {
        private final JgdaPdfAssetClient jgdaPdfAssetClient = mock(JgdaPdfAssetClient.class);
        private final FileDerivedMediaAssetRepository assetRepository = mock(FileDerivedMediaAssetRepository.class);
        private final PdfDerivedMediaAssetCacheRepository cacheRepository = mock(PdfDerivedMediaAssetCacheRepository.class);
        private final ProjectFileLinkRepository projectFileLinkRepository = mock(ProjectFileLinkRepository.class);
        private final FileAccessGrantRepository fileAccessGrantRepository = mock(FileAccessGrantRepository.class);
        private final RedisClient redisClient = mock(RedisClient.class);
        private final ObjectStorageFileRepository objectStorageFileRepository = mock(ObjectStorageFileRepository.class);
        private final R2Client r2Client = mock(R2Client.class);
        private final PdfDerivedMediaAssetExtractionServiceImpl service;

        private Fixture() {
            service = new PdfDerivedMediaAssetExtractionServiceImpl(
                    enabledProperties(),
                    jgdaPdfAssetClient,
                    objectStorageFileRepository,
                    projectFileLinkRepository,
                    fileAccessGrantRepository,
                    assetRepository,
                    cacheRepository,
                    redisClient,
                    r2Client,
                    new StorageR2Properties(),
                    directExecutor(),
                    new ObjectMapper());
        }

        private PdfDerivedMediaAssetExtractionServiceImpl service() {
            return service;
        }

        private JgdaPdfAssetClient jgdaPdfAssetClient() {
            return jgdaPdfAssetClient;
        }

        private FileDerivedMediaAssetRepository assetRepository() {
            return assetRepository;
        }

        private RedisClient redisClient() {
            return redisClient;
        }

        private PdfDerivedMediaAssetCacheRepository cacheRepository() {
            return cacheRepository;
        }

        private ProjectFileLinkRepository projectFileLinkRepository() {
            return projectFileLinkRepository;
        }

        private FileAccessGrantRepository fileAccessGrantRepository() {
            return fileAccessGrantRepository;
        }

        private ObjectStorageFileRepository objectStorageFileRepository() {
            return objectStorageFileRepository;
        }

        private R2Client r2Client() {
            return r2Client;
        }
    }

    private static TaskExecutor directExecutor() {
        return Runnable::run;
    }

    private static PdfDerivedAssetsProperties enabledProperties() {
        return new PdfDerivedAssetsProperties(
                true,
                "local",
                "",
                120_000,
                144,
                20,
                4,
                true,
                true,
                true,
                "openai",
                false,
                5,
                false,
                false,
                false,
                false,
                0,
                0.005,
                4,
                "gpt-5.4-mini",
                0,
                1,
                1024,
                "",
                "",
                144,
                144,
                0,
                0.005,
                1,
                1,
                0,
                0,
                true,
                true,
                "",
                "ai-outline",
                1600,
                85,
                "jpeg");
    }
}
