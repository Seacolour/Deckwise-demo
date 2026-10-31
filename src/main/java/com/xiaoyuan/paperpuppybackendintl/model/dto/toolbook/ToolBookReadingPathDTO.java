package com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolBookReadingPathDTO {

    private String scenario;
    private List<String> nodeKeys = new ArrayList<>();
}
