package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileUploadBatchResponse {
    private String batchId;
    private Long projectId;
    private Integer totalCount;
    private List<FileUploadCacheResponse> files;
}
