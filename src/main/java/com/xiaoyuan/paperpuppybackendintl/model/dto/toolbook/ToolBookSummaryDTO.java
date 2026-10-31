package com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolBookSummaryDTO {

    private String bookKey;
    private String title;
    private String description;
    private String status;
    private String version;
    private String defaultEntryNodeKey;
    private List<String> aliases = new ArrayList<>();
    private List<String> useWhen = new ArrayList<>();
    private List<String> avoidWhen = new ArrayList<>();
}
