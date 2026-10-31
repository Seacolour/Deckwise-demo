package com.xiaoyuan.paperpuppybackendintl.service.file.jgda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JgdaImagePlacement {

    private double left;
    private double top;
    private double width;
    private double height;
    private double pageWidth;
    private double pageHeight;
    private double normalizedLeft;
    private double normalizedTop;
    private double normalizedWidth;
    private double normalizedHeight;

    public double getLeft() {
        return left;
    }

    public void setLeft(double left) {
        this.left = left;
    }

    public double getTop() {
        return top;
    }

    public void setTop(double top) {
        this.top = top;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public double getPageWidth() {
        return pageWidth;
    }

    public void setPageWidth(double pageWidth) {
        this.pageWidth = pageWidth;
    }

    public double getPageHeight() {
        return pageHeight;
    }

    public void setPageHeight(double pageHeight) {
        this.pageHeight = pageHeight;
    }

    public double getNormalizedLeft() {
        return normalizedLeft;
    }

    public void setNormalizedLeft(double normalizedLeft) {
        this.normalizedLeft = normalizedLeft;
    }

    public double getNormalizedTop() {
        return normalizedTop;
    }

    public void setNormalizedTop(double normalizedTop) {
        this.normalizedTop = normalizedTop;
    }

    public double getNormalizedWidth() {
        return normalizedWidth;
    }

    public void setNormalizedWidth(double normalizedWidth) {
        this.normalizedWidth = normalizedWidth;
    }

    public double getNormalizedHeight() {
        return normalizedHeight;
    }

    public void setNormalizedHeight(double normalizedHeight) {
        this.normalizedHeight = normalizedHeight;
    }
}
