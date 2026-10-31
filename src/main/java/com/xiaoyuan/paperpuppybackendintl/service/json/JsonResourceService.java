package com.xiaoyuan.paperpuppybackendintl.service.json;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonResourceService {

    record JsonResourceSnapshot(
            String resourceKind,
            Long fileId,
            Long documentId,
            boolean writable,
            String revision,
            String sourceName,
            String documentType,
            JsonNode root
    ) {
    }

    JsonResourceSnapshot readResource(Long userId, Long projectId, Long fileId, Long documentId);
}
