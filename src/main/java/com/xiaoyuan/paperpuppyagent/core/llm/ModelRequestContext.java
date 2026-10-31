package com.xiaoyuan.paperpuppyagent.core.llm;

import java.util.Objects;

/** Request-scoped model route and credential; must not be persisted or logged. */
public final class ModelRequestContext {
    private final ModelEndpoint endpoint;
    private final String upstreamModel;
    private final String apiKey;
    private final boolean legacyOpenAiConfiguration;

    ModelRequestContext(ModelEndpoint endpoint, String upstreamModel, String apiKey,
                        boolean legacyOpenAiConfiguration) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.upstreamModel = Objects.requireNonNull(upstreamModel, "upstreamModel");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.legacyOpenAiConfiguration = legacyOpenAiConfiguration;
    }

    public ModelEndpoint endpoint() {
        return endpoint;
    }

    public String providerId() {
        return endpoint.providerId();
    }

    public String upstreamModel() {
        return upstreamModel;
    }

    String apiKey() {
        return apiKey;
    }

    boolean legacyOpenAiConfiguration() {
        return legacyOpenAiConfiguration;
    }

    @Override
    public String toString() {
        return "ModelRequestContext[providerId=" + providerId()
                + ", upstreamModel=" + upstreamModel + ", apiKey=<redacted>]";
    }
}
