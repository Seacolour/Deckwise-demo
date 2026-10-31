package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilePurgePreviewResponse {

    private Long projectId;

    private Long fileId;

    private String cacheKey;

    private String sourceType;

    private String fileName;

    private String mimeType;

    private Long sizeBytes;

    private String publicUrl;

    private Boolean purgeable;

    private Boolean alreadyDeleted;

    private List<FilePurgeBlockerDTO> blockers = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();
}
