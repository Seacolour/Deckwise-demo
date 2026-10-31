package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

/**
 * 百炼风格的轻量入口：参数对象 -> 一行调用。
 *
 * <p>说明：这里不依赖 Spring Bean，适合写小工具/脚本式调用。</p>
 */
public final class OpenAiLite {

    private OpenAiLite() {
    }

    private static final OpenAiApplicationExecutor APPLICATION = new OpenAiApplicationExecutor();
    private static final OpenAiGenerationExecutor GENERATION = new OpenAiGenerationExecutor();

    /**
     * instructions + input -> Responses API -> text
     */
    public static String call(ApplicationParam param) {
        return APPLICATION.call(param);
    }

    /**
     * messages(+response_format) -> Chat Completions -> assistant content
     */
    public static String call(GenerationParam param) {
        return GENERATION.call(param);
    }
}


