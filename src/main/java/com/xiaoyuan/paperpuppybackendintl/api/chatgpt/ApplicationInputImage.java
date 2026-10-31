package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

public record ApplicationInputImage(String imageUrl, String label, String detail) {

    public ApplicationInputImage(String imageUrl, String label) {
        this(imageUrl, label, null);
    }
}
