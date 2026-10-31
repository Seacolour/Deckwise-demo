package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 Responses API 的 output 里抽取文本。
 */
final class OpenAiTextExtractor {

    private OpenAiTextExtractor() {
    }

    static String extractOutputText(List<ResponseOutputItem> output) {
        if (output == null || output.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ResponseOutputItem item : output) {
            item.message().ifPresent(msg -> {
                for (var c : msg.content()) {
                    c.outputText().ifPresent(t -> sb.append(t.text()));
                }
            });
        }
        return sb.toString();
    }

    /**
     * 若存在 refusal，返回第一条 refusal 文本；否则返回 null。
     */
    static String extractRefusal(List<ResponseOutputItem> output) {
        if (output == null || output.isEmpty()) return null;
        for (ResponseOutputItem item : output) {
            var msgOpt = item.message();
            if (msgOpt.isEmpty()) continue;
            var msg = msgOpt.get();
            for (var c : msg.content()) {
                var rOpt = c.refusal();
                if (rOpt.isPresent()) {
                    return rOpt.get().refusal();
                }
            }
        }
        return null;
    }

    static OpenAiWebSearchMetadata extractWebSearchMetadata(List<ResponseOutputItem> output) {
        if (output == null || output.isEmpty()) {
            return OpenAiWebSearchMetadata.empty();
        }
        ArrayList<OpenAiWebSearchMetadata.WebSearchCall> calls = new ArrayList<>();
        ArrayList<OpenAiWebSearchMetadata.WebSearchSource> sources = new ArrayList<>();
        ArrayList<OpenAiWebSearchMetadata.WebSearchCitation> citations = new ArrayList<>();
        Set<String> seenSources = new LinkedHashSet<>();
        for (ResponseOutputItem item : output) {
            item.webSearchCall().ifPresent(call -> {
                String id = safe(() -> call.id());
                String status = safe(() -> call.status().asString());
                try {
                    var action = call.action();
                    action.search().ifPresent(search -> {
                        ArrayList<String> queries = new ArrayList<>();
                        addIfPresent(queries, safe(() -> search.query().orElse("")));
                        search.queries().orElse(List.of()).forEach(query -> addIfPresent(queries, query));
                        ArrayList<String> sourceUrls = new ArrayList<>();
                        search.sources().orElse(List.of()).forEach(source -> {
                            String url = safe(() -> source.url());
                            if (!url.isBlank()) {
                                addIfPresent(sourceUrls, url);
                                addSource(sources, seenSources, url, "");
                            }
                        });
                        calls.add(new OpenAiWebSearchMetadata.WebSearchCall(
                                id,
                                "search",
                                status,
                                queries.isEmpty() ? "" : queries.getFirst(),
                                queries,
                                sourceUrls
                        ));
                    });
                    action.openPage().ifPresent(openPage -> {
                        String url = safe(() -> openPage.url().orElse(""));
                        if (!url.isBlank()) {
                            addSource(sources, seenSources, url, "");
                        }
                        calls.add(new OpenAiWebSearchMetadata.WebSearchCall(
                                id,
                                "open_page",
                                status,
                                url,
                                url.isBlank() ? List.of() : List.of(url),
                                url.isBlank() ? List.of() : List.of(url)
                        ));
                    });
                    action.findInPage().ifPresent(find -> {
                        String url = safe(() -> find.url());
                        if (!url.isBlank()) {
                            addSource(sources, seenSources, url, "");
                        }
                        calls.add(new OpenAiWebSearchMetadata.WebSearchCall(
                                id,
                                "find_in_page",
                                status,
                                safe(() -> find.pattern()),
                                List.of(safe(() -> find.pattern())),
                                url.isBlank() ? List.of() : List.of(url)
                        ));
                    });
                } catch (RuntimeException ignored) {
                    calls.add(new OpenAiWebSearchMetadata.WebSearchCall(
                            id,
                            "unknown",
                            status,
                            "",
                            List.of(),
                            List.of()
                    ));
                }
            });
            item.message().ifPresent(msg -> {
                for (var content : msg.content()) {
                    content.outputText().ifPresent(text -> extractUrlCitations(text, citations, sources, seenSources));
                }
            });
        }
        return new OpenAiWebSearchMetadata(calls, sources, citations);
    }

    private static void extractUrlCitations(ResponseOutputText text,
                                            List<OpenAiWebSearchMetadata.WebSearchCitation> citations,
                                            List<OpenAiWebSearchMetadata.WebSearchSource> sources,
                                            Set<String> seenSources) {
        if (text == null) {
            return;
        }
        for (var annotation : text.annotations()) {
            annotation.urlCitation().ifPresent(citation -> {
                String url = safe(() -> citation.url());
                if (url.isBlank()) {
                    return;
                }
                String title = safe(() -> citation.title());
                citations.add(new OpenAiWebSearchMetadata.WebSearchCitation(
                        url,
                        title,
                        safeLong(() -> citation.startIndex()),
                        safeLong(() -> citation.endIndex())
                ));
                addSource(sources, seenSources, url, title);
            });
        }
    }

    private static void addSource(List<OpenAiWebSearchMetadata.WebSearchSource> sources,
                                  Set<String> seen,
                                  String url,
                                  String title) {
        String normalizedUrl = normalize(url);
        if (normalizedUrl.isBlank() || !seen.add(normalizedUrl)) {
            return;
        }
        sources.add(new OpenAiWebSearchMetadata.WebSearchSource(normalizedUrl, normalize(title)));
    }

    private static void addIfPresent(List<String> values, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank() && !values.contains(normalized)) {
            values.add(normalized);
        }
    }

    private static String safe(SupplierWithException<String> supplier) {
        try {
            String value = supplier.get();
            return normalize(value);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static long safeLong(LongSupplierWithException supplier) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }

    @FunctionalInterface
    private interface LongSupplierWithException {
        long getAsLong();
    }
}


