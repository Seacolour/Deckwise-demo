package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Seacolour
 * @date 2026/1/15 18:46
 * @description:
 */
@Data
@AllArgsConstructor
public class ProjectLinkItem {
    private final Long fileId;
    private final String displayName;
    private final String kind;
}
