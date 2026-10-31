package com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolBookManifestSpec {

    private String bookKey;
    private String title;
    private String description;
    private String status;
    private String version;
    private String defaultEntryNodeKey;
    private List<String> aliases = new ArrayList<>();
    private List<String> useWhen = new ArrayList<>();
    private List<String> avoidWhen = new ArrayList<>();
    private List<ReadingPathSpec> recommendedReadingPaths = new ArrayList<>();
    private List<NodeSpec> nodes = new ArrayList<>();

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReadingPathSpec {
        private String scenario;
        private List<String> nodeKeys = new ArrayList<>();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeSpec {
        private String nodeKey;
        private String parentKey;
        private String nodeType;
        private String title;
        private String summary;
        private String path;
        private Integer depth;
        private Integer sortOrder;
        private List<String> tags = new ArrayList<>();
    }
}
