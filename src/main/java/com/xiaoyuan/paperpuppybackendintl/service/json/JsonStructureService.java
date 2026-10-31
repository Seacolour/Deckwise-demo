package com.xiaoyuan.paperpuppybackendintl.service.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface JsonStructureService {

    record JsonStructureResult(
            JsonResourceService.JsonResourceSnapshot resource,
            String path,
            String nodeType,
            boolean truncated,
            ObjectNode stats,
            JsonNode skeleton,
            List<String> children
    ) {
    }

    JsonStructureResult readStructure(Long userId,
                                      Long projectId,
                                      Long fileId,
                                      Long documentId,
                                      String path,
                                      Integer maxDepth,
                                      Integer sampleArrayItems,
                                      Integer maxKeysPerObject);
}
