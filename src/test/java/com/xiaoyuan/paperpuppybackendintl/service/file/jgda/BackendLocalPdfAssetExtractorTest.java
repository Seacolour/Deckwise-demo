package com.xiaoyuan.paperpuppybackendintl.service.file.jgda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.ApplicationParam;
import com.xiaoyuan.paperpuppybackendintl.api.chatgpt.OpenAiApplicationExecutor;
import com.xiaoyuan.paperpuppybackendintl.config.PdfDerivedAssetsProperties;
import com.xiaoyuan.paperpuppybackendintl.config.StorageR2Properties;
import com.xiaoyuan.paperpuppybackendintl.config.openai.OpenAiProperties;
import com.xiaoyuan.paperpuppybackendintl.infrastructure.r2.R2Client;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackendLocalPdfAssetExtractorTest {

    @Test
    void extractSkipsFailedOpenAiPageAndKeepsSuccessfulRegions() throws Exception {
        R2Client r2Client = mock(R2Client.class);
        List<String> uploadedKeys = new CopyOnWriteArrayList<>();
        when(r2Client.uploadWithMeta(anyString(), any(byte[].class), eq("image/jpeg")))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0, String.class);
                    uploadedKeys.add(key);
                    return new R2Client.UploadResult(
                            "https://assets.example.test/" + key,
                            "etag",
                            null,
                            OffsetDateTime.now());
                });
        RecordingOpenAiExecutor openAiExecutor = new RecordingOpenAiExecutor(2);
        BackendLocalPdfAssetExtractor extractor = new BackendLocalPdfAssetExtractor(
                properties(),
                new OpenAiProperties("sk-test", "https://api.openai.com/v1", "gpt-5.4-mini", "", "", "default"),
                r2Client,
                new StorageR2Properties(),
                new ObjectMapper(),
                openAiExecutor);

        JgdaPdfAssetExtractionResult result = extractor.extract("input.pdf", blankPdf(3));

        assertEquals(3, result.getDocumentAiProcessedPageCount());
        assertEquals(2, result.getDocumentAiDetectedRegionCount());
        assertEquals(2, result.getDocumentAiCropCount());
        assertEquals(2, result.getAssets().size());
        assertEquals(3, uploadedKeys.size());
        assertEquals(3, uploadedKeys.stream()
                .filter(key -> key.startsWith("tmp/openai/pdf-page-renders/"))
                .count());
        assertEquals(3, openAiExecutor.calls().size());
        assertEquals(List.of(2, 2, 2), openAiExecutor.calls().stream()
                .map(ApplicationParam::clientMaxRetries)
                .toList());
    }

    private static byte[] blankPdf(int pages) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static PdfDerivedAssetsProperties properties() {
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
                3,
                "gpt-5.4-mini",
                -1,
                -1,
                1024,
                "low",
                "low",
                72,
                72,
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

    private static final class RecordingOpenAiExecutor extends OpenAiApplicationExecutor {
        private static final Pattern PAGE_PATTERN = Pattern.compile("PDF page (\\d+)");
        private final int failingPage;
        private final List<ApplicationParam> calls = new CopyOnWriteArrayList<>();

        private RecordingOpenAiExecutor(int failingPage) {
            this.failingPage = failingPage;
        }

        @Override
        public String call(ApplicationParam param) {
            calls.add(param);
            int pageNumber = pageNumber(param.input());
            if (pageNumber == failingPage) {
                throw new RuntimeException("InternalServerException: 503: null");
            }
            return """
                    {
                      "hasVisual": true,
                      "regions": [
                        {
                          "kind": "diagram",
                          "description": "Main visual block",
                          "x": 100,
                          "y": 120,
                          "width": 500,
                          "height": 360,
                          "confidence": 0.9,
                          "includeCaption": false
                        }
                      ]
                    }
                    """;
        }

        private List<ApplicationParam> calls() {
            return calls;
        }

        private static int pageNumber(String input) {
            Matcher matcher = PAGE_PATTERN.matcher(input == null ? "" : input);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        }
    }
}
