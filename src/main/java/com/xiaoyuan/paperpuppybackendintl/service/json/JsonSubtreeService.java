package com.xiaoyuan.paperpuppybackendintl.service.json;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonSubtreeService {

    record JsonSubtreeResult(
            JsonResourceService.JsonResourceSnapshot resource,
            String path,
            String nodeType,
            boolean truncated,
            int returnedNodeCount,
            int approxBytes,
            JsonNode value
    ) {
    }

    JsonSubtreeResult readSubtree(Long userId,
                                  Long projectId,
                                  Long fileId,
                                  Long documentId,
                                  String path,
                                  Integer maxBytes,
                                  Integer maxNodes);
}
