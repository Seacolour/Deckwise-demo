package com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolBookTocDTO {

    private ToolBookSummaryDTO book;
    private List<ToolBookReadingPathDTO> recommendedReadingPaths = new ArrayList<>();
    private List<ToolBookNodeDTO> nodes = new ArrayList<>();
}
