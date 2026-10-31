package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import java.util.Objects;

final class OpenAiServiceTierSupport {

    private OpenAiServiceTierSupport() {
    }

    static String resolve(AiParam param) {
        String explicit = AiOptionReader.stringOpt(param, "model.serviceTier", null);
        String fallback = param == null ? null : param.serviceTier();
        String raw = firstNonBlank(explicit, fallback);
        return raw == null ? null : normalize(raw);
    }

    static String normalize(String raw) {
        String normalized = Objects.toString(raw, "").trim().toLowerCase();
        return switch (normalized) {
            case "" -> "";
            case "fast" -> "priority";
            case "standard" -> "default";
            default -> normalized;
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
