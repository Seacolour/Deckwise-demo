package com.xiaoyuan.paperpuppyagent.core.llm;

/** Server-owned endpoint and capability policy. Never build this from a user-supplied URL. */
public record ModelEndpoint(
        String providerId,
        String baseUrl,
        boolean supportsTools,
        boolean supportsImages,
        boolean supportsFiles,
        boolean supportsPriorityProcessing,
        boolean supportsContextManagement) {
}
