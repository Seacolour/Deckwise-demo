package com.xiaoyuan.paperpuppybackendintl.service.version;

import com.xiaoyuan.paperpuppybackendintl.model.enums.version.VersionDecisionType;
import lombok.Builder;
import lombok.Data;

/**
 * Domain 侧判定结果（Filter/Controller 复用）
 */
@Data
@Builder
public class VersionDecision {
    private VersionDecisionType type;

    private String platform;
    private String version;

    private String message;
    private String updateUrl;
    private String resourceUrl;

    private String minSupported;
    private String maxSupported;
}


