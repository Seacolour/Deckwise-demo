package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import lombok.Data;

@Data
public class PendingProjectFileFinalizeRequest {
    private String pendingFileRef;
    private Long projectId;
    private Boolean extractPdfImages;
    private String uploadSource;
}
