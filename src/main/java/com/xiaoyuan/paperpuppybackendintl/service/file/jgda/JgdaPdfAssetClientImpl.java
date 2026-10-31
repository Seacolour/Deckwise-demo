package com.xiaoyuan.paperpuppybackendintl.service.file.jgda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoyuan.paperpuppybackendintl.config.PdfDerivedAssetsProperties;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class JgdaPdfAssetClientImpl implements JgdaPdfAssetClient {

    private static final MediaType PDF = MediaType.parse("application/pdf");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PdfDerivedAssetsProperties properties;
    private final BackendLocalPdfAssetExtractor localPdfAssetExtractor;

    public JgdaPdfAssetClientImpl(OkHttpClient httpClient,
                                  @org.springframework.beans.factory.annotation.Qualifier("jackson2Mapper") ObjectMapper objectMapper,
                                  PdfDerivedAssetsProperties properties,
                                  BackendLocalPdfAssetExtractor localPdfAssetExtractor) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.localPdfAssetExtractor = localPdfAssetExtractor;
    }

    @Override
    public JgdaPdfAssetExtractionResult extract(String fileName, byte[] pdfBytes) throws IOException {
        if (properties.enabled() && properties.useLocalExtractor()) {
            return localPdfAssetExtractor.extract(fileName, pdfBytes);
        }
        if (!properties.enabled() || properties.extractorBaseUrl().isBlank()) {
            return new JgdaPdfAssetExtractionResult();
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IOException("PDF bytes are empty");
        }
        String safeName = (fileName == null || fileName.isBlank()) ? "input.pdf" : fileName.trim();
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", safeName, RequestBody.create(pdfBytes, PDF))
                .addFormDataPart("dpi", String.valueOf(properties.dpi()))
                .addFormDataPart("maxPages", String.valueOf(properties.maxPages()))
                .addFormDataPart("renderPages", String.valueOf(properties.renderPages()))
                .addFormDataPart("extractEmbeddedImages", String.valueOf(properties.extractEmbeddedImages()))
                .addFormDataPart("cropFigures", String.valueOf(properties.cropFigures()))
                .addFormDataPart("imageDetectionProvider", properties.imageDetectionProvider())
                .addFormDataPart("enableDocumentAi", String.valueOf(properties.enableDocumentAi()))
                .addFormDataPart("documentAiMaxPages", String.valueOf(properties.documentAiMaxPages()))
                .addFormDataPart("documentAiReturnImages", String.valueOf(properties.documentAiReturnImages()))
                .addFormDataPart("documentAiReturnBoundingBoxes", String.valueOf(properties.documentAiReturnBoundingBoxes()))
                .addFormDataPart("documentAiEnableImageAnnotation", String.valueOf(properties.documentAiEnableImageAnnotation()))
                .addFormDataPart("documentAiIncludeTables", String.valueOf(properties.documentAiIncludeTables()))
                .addFormDataPart("documentAiCropMargin", String.valueOf(properties.documentAiCropMargin()))
                .addFormDataPart("documentAiMinPageAreaRatio", String.valueOf(properties.documentAiMinPageAreaRatio()))
                .addFormDataPart("openAiMaxPages", String.valueOf(properties.openAiMaxPages()))
                .addFormDataPart("openAiModel", properties.openAiModel())
                .addFormDataPart("openAiRenderDpi", String.valueOf(properties.openAiRenderDpi()))
                .addFormDataPart("openAiCropMargin", String.valueOf(properties.openAiCropMargin()))
                .addFormDataPart("openAiMinPageAreaRatio", String.valueOf(properties.openAiMinPageAreaRatio()))
                .addFormDataPart("minEmbeddedWidth", String.valueOf(properties.minEmbeddedWidth()))
                .addFormDataPart("minEmbeddedHeight", String.valueOf(properties.minEmbeddedHeight()))
                .addFormDataPart("minEmbeddedFileSizeBytes", String.valueOf(properties.minEmbeddedFileSizeBytes()))
                .addFormDataPart("minEmbeddedPageAreaRatio", String.valueOf(properties.minEmbeddedPageAreaRatio()))
                .addFormDataPart("deduplicateEmbeddedImages", String.valueOf(properties.deduplicateEmbeddedImages()))
                .build();

        Request request = new Request.Builder()
                .url(properties.extractorBaseUrl() + "/pdf-assets/extract")
                .post(body)
                .build();
        OkHttpClient client = httpClient.newBuilder()
                .connectTimeout(properties.timeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.timeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.timeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String raw = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("JGDA PDF asset extraction failed: HTTP " + response.code() + " " + raw);
            }
            if (raw.isBlank()) {
                return new JgdaPdfAssetExtractionResult();
            }
            return objectMapper.readValue(raw, JgdaPdfAssetExtractionResult.class);
        }
    }
}
