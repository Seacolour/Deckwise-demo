package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.Data;

import java.util.List;

@Data
public class PrivateObjectResourceTokenResponse {

    private String fileBaseUrl;

    private String imageBaseUrl;

    private String resourceToken;

    private String scope;

    private List<String> prefixes;

    private Long expiresAt;

    private Long ttlSeconds;
}
