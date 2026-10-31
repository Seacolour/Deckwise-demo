package com.xiaoyuan.paperpuppybackendintl.model.vo.version;

import com.xiaoyuan.paperpuppybackendintl.model.enums.version.VersionDecisionType;
import lombok.Data;

/**
 * 给客户端的版本判定结果（启动时拉取 / 调试用）
 */
@Data
public class VersionDecisionVO {
    private String platform;
    private String version;
    private VersionDecisionType decision;

    private String message;
    private String updateUrl;
    private String resourceUrl;

    private String minSupported;
    private String maxSupported;
}


