package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.Data;

/**
 * 用于批量 upsert 项目文件关联（project_file_links）。
 */
@Data
public class ProjectFileLinkUpsertItem {
    private Long fileId;
    private String displayName;
    private String kind;
}

