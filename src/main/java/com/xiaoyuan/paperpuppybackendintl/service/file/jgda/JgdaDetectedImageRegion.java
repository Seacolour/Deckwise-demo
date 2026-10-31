package com.xiaoyuan.paperpuppybackendintl.service.file.jgda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JgdaDetectedImageRegion {

    private String provider;
    private String kind;
    private int pageNumber;
    private int regionIndex;
    private String sourceName;
    private JgdaImagePlacement placement;
    private double confidence;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getRegionIndex() {
        return regionIndex;
    }

    public void setRegionIndex(int regionIndex) {
        this.regionIndex = regionIndex;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public JgdaImagePlacement getPlacement() {
        return placement;
    }

    public void setPlacement(JgdaImagePlacement placement) {
        this.placement = placement;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
