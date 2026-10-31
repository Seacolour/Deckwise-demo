package com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolBookNodeDTO {

    private String nodeKey;
    private String parentKey;
    private String nodeType;
    private String title;
    private String summary;
    private Integer depth;
    private Integer sortOrder;
    private List<String> tags = new ArrayList<>();
}
