package com.xiaoyuan.paperpuppyagent.core.memory;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record StoredToolResult(
        String agentRunId,
        String callId,
        String toolName,
        JsonNode result,
        String resultJson,
        int resultChars,
        Instant storedAt
) {
}
