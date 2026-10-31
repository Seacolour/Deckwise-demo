package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.Data;

@Data
public class FilePurgeRequest {

    private Boolean confirm;

    private String expectedCacheKey;
}
