package com.xiaoyuan.paperpuppybackendintl.model.dto.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilePurgeBlockerDTO {

    private String type;

    private Long count;

    private String message;
}
