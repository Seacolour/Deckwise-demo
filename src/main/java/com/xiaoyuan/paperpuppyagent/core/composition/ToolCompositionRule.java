package com.xiaoyuan.paperpuppyagent.core.composition;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A whitelisted runtime composition rule.
 */
public record ToolCompositionRule(
        String source,
        String then,
        String when,
        int maxDepth,
        boolean allowWriteTools,
        JsonNode argMapping
) {
}
