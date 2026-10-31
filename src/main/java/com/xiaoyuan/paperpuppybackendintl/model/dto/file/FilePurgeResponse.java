package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilePurgeResponse {

    private Long projectId;

    private Long fileId;

    private Boolean purged;

    private String status;

    private Integer scheduledObjectDeleteCount;

    private FilePurgePreviewResponse preview;
}
