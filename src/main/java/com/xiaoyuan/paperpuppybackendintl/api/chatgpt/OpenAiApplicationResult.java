package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

public record OpenAiApplicationResult(
        String outputText,
        OpenAiWebSearchMetadata webSearch
) {
    public OpenAiApplicationResult {
        outputText = outputText == null ? "" : outputText;
        webSearch = webSearch == null ? OpenAiWebSearchMetadata.empty() : webSearch;
    }
}
