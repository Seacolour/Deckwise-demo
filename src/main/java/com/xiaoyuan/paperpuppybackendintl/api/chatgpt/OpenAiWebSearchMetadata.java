package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import java.util.List;

public record OpenAiWebSearchMetadata(
        List<WebSearchCall> calls,
        List<WebSearchSource> sources,
        List<WebSearchCitation> citations
) {
    public OpenAiWebSearchMetadata {
        calls = calls == null ? List.of() : List.copyOf(calls);
        sources = sources == null ? List.of() : List.copyOf(sources);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public static OpenAiWebSearchMetadata empty() {
        return new OpenAiWebSearchMetadata(List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return calls.isEmpty() && sources.isEmpty() && citations.isEmpty();
    }

    public record WebSearchCall(
            String id,
            String action,
            String status,
            String query,
            List<String> queries,
            List<String> sourceUrls
    ) {
        public WebSearchCall {
            queries = queries == null ? List.of() : List.copyOf(queries);
            sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
        }
    }

    public record WebSearchSource(
            String url,
            String title
    ) {
    }

    public record WebSearchCitation(
            String url,
            String title,
            long startIndex,
            long endIndex
    ) {
    }
}
