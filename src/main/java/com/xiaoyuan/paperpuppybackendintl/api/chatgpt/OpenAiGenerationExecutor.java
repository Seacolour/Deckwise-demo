package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.List;
import java.util.Objects;

/**
 * “生成式”调用：Chat Completions（支持 response_format）。
 */
public class OpenAiGenerationExecutor implements AiExecutor<GenerationParam, String> {

    @Override
    public String call(GenerationParam param) {
        Objects.requireNonNull(param, "param");
        List<String> fallbackModels = AiOptionReader.stringListOpt(param, "model.fallbackModels");
        List<String> models = fallbackModels.isEmpty()
                ? List.of(param.model())
                : concatModels(param.model(), fallbackModels);

        RuntimeException last = null;
        for (String model : models) {
            GenerationParam p2 = param.toBuilder().model(model).build();
            try {
                return doCallOnce(p2);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last == null ? new RuntimeException("OpenAI call failed") : last;
    }

    private static String doCallOnce(GenerationParam param) {
        OpenAIClient client = OpenAiClients.get(param);
        String model = Objects.toString(param.model(), "").trim();
        if (model.isBlank()) throw new IllegalArgumentException("model is blank");

        ChatCompletionCreateParams.Builder b = ChatCompletionCreateParams.builder()
                .model(model);

        if (param.messages() != null && !param.messages().isEmpty()) {
            b.messages(param.messages());
        } else {
            throw new IllegalArgumentException("messages is empty (please provide at least one user/system message)");
        }

        if (param.responseFormat() != null) {
            b.responseFormat(param.responseFormat());
        }

        // 可选项映射
        double temperature = AiOptionReader.doubleOpt(param, "model.temperature", Double.NaN);
        if (!Double.isNaN(temperature)) {
            trySetDouble(b, "temperature", temperature);
        }
        double topP = AiOptionReader.doubleOpt(param, "model.topP", Double.NaN);
        if (!Double.isNaN(topP)) {
            trySetDouble(b, "topP", topP);
        }
        int maxTokens = AiOptionReader.intOpt(param, "model.maxTokens", -1);
        if (maxTokens > 0) {
            trySetInt(b, "maxTokens", maxTokens);
        }
        String serviceTier = OpenAiServiceTierSupport.resolve(param);
        if (serviceTier != null && !serviceTier.isBlank()) {
            b.serviceTier(ChatCompletionCreateParams.ServiceTier.of(serviceTier));
        }

        var resp = client.chat().completions().create(b.build());

        // 兼容 SDK 的 Optional/nullable 结构：尽量抽取第一条 choice 的 message content
        if (resp.choices() == null || resp.choices().isEmpty()) return "";
        var choice0 = resp.choices().getFirst();
        if (choice0 == null || choice0.message() == null) return "";
        return Objects.toString(choice0.message().content(), "");
    }

    private static void trySetDouble(Object builder, String method, double v) {
        try {
            builder.getClass().getMethod(method, Double.TYPE).invoke(builder, v);
        } catch (Exception ignore) {
        }
    }

    private static void trySetInt(Object builder, String method, int v) {
        try {
            builder.getClass().getMethod(method, Integer.TYPE).invoke(builder, v);
        } catch (Exception ignore) {
        }
    }

    private static List<String> concatModels(String primary, List<String> fallback) {
        String p = Objects.toString(primary, "").trim();
        if (p.isBlank()) return fallback;
        var out = new java.util.ArrayList<String>(1 + fallback.size());
        out.add(p);
        for (String m : fallback) {
            String t = Objects.toString(m, "").trim();
            if (!t.isBlank() && !t.equals(p)) out.add(t);
        }
        return List.copyOf(out);
    }
}


