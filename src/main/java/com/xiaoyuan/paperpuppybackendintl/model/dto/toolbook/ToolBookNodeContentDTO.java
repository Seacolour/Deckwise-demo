package com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolBookNodeContentDTO {

    private ToolBookSummaryDTO book;
    private ToolBookNodeDTO node;
    private List<ToolBookNodeDTO> breadcrumbs = new ArrayList<>();
    private List<ToolBookNodeDTO> children = new ArrayList<>();
    private String contentMd;
    private Integer contentLength;
    private Integer maxCharsApplied;
    private Boolean truncated;
}
