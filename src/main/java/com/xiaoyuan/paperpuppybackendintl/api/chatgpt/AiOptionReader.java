package com.xiaoyuan.paperpuppybackendintl.api.chatgpt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从 {@link AiParam#options()} 中读取常见配置的轻量工具。
 */
final class AiOptionReader {

    private AiOptionReader() {
    }

    static Map<String, Object> options(AiParam p) {
        return p == null || p.options() == null ? Map.of() : p.options();
    }

    static int intOpt(AiParam p, String key, int def) {
        Object v = opt(p, key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(Objects.toString(v, "").trim());
        } catch (Exception ignore) {
            return def;
        }
    }

    static double doubleOpt(AiParam p, String key, double def) {
        Object v = opt(p, key);
        if (v == null) return def;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(Objects.toString(v, "").trim());
        } catch (Exception ignore) {
            return def;
        }
    }

    static List<String> stringListOpt(AiParam p, String key) {
        Object v = opt(p, key);
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                String s = Objects.toString(o, "").trim();
                if (!s.isBlank()) out.add(s);
            }
            return List.copyOf(out);
        }
        String s = Objects.toString(v, "").trim();
        return s.isBlank() ? List.of() : List.of(s);
    }

    static String stringOpt(AiParam p, String key, String def) {
        Object v = opt(p, key);
        if (v == null) return def;
        String s = Objects.toString(v, "").trim();
        return s.isBlank() ? def : s;
    }

    private static Object opt(AiParam p, String... keys) {
        if (keys == null || keys.length == 0) return null;
        Map<String, Object> m = options(p);
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            Object v = m.get(k);
            if (v != null) return v;
        }
        return null;
    }
}


