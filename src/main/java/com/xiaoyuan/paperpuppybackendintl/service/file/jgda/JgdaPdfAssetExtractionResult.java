package com.xiaoyuan.paperpuppybackendintl.service.file.jgda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JgdaPdfAssetExtractionResult {

    private String pdfPath;
    private String outputDir;
    private int pageCount;
    private int processedPageCount;
    private int documentAiProcessedPageCount;
    private int documentAiDetectedRegionCount;
    private int documentAiCropCount;
    private List<JgdaDetectedImageRegion> documentAiRegions = List.of();
    private List<JgdaExtractedImageAsset> assets = List.of();
    private OffsetDateTime generatedAt;

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getProcessedPageCount() {
        return processedPageCount;
    }

    public void setProcessedPageCount(int processedPageCount) {
        this.processedPageCount = processedPageCount;
    }

    public int getDocumentAiProcessedPageCount() {
        return documentAiProcessedPageCount;
    }

    public void setDocumentAiProcessedPageCount(int documentAiProcessedPageCount) {
        this.documentAiProcessedPageCount = documentAiProcessedPageCount;
    }

    public int getDocumentAiDetectedRegionCount() {
        return documentAiDetectedRegionCount;
    }

    public void setDocumentAiDetectedRegionCount(int documentAiDetectedRegionCount) {
        this.documentAiDetectedRegionCount = documentAiDetectedRegionCount;
    }

    public int getDocumentAiCropCount() {
        return documentAiCropCount;
    }

    public void setDocumentAiCropCount(int documentAiCropCount) {
        this.documentAiCropCount = documentAiCropCount;
    }

    public List<JgdaDetectedImageRegion> getDocumentAiRegions() {
        return documentAiRegions == null ? List.of() : documentAiRegions;
    }

    public void setDocumentAiRegions(List<JgdaDetectedImageRegion> documentAiRegions) {
        this.documentAiRegions = documentAiRegions == null ? List.of() : documentAiRegions;
    }

    public List<JgdaExtractedImageAsset> getAssets() {
        return assets == null ? List.of() : assets;
    }

    public void setAssets(List<JgdaExtractedImageAsset> assets) {
        this.assets = assets == null ? List.of() : assets;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
