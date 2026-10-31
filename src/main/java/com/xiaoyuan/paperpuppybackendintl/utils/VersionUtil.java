package com.xiaoyuan.paperpuppybackendintl.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 版本号比较工具（偏向 semver 的“数字段”比较）。
 *
 * 约定：
 * - 主要处理形如 1.2.3 / 1.2 / 1.2.3-beta（beta 部分会被忽略数字后缀，仅用于“尽量不崩”）
 * - 如果解析失败，则退化为字符串比较（不推荐，但比直接报错更安全）
 */
public class VersionUtil {

    private VersionUtil() {
    }

    /**
     * 比较 v1 与 v2：
     * - v1 < v2 => -1
     * - v1 = v2 => 0
     * - v1 > v2 => 1
     */
    public static int compare(String v1, String v2) {
        if (Objects.equals(v1, v2)) {
            return 0;
        }
        if (StringUtils.isBlank(v1)) {
            return -1;
        }
        if (StringUtils.isBlank(v2)) {
            return 1;
        }

        try {
            List<Integer> a = parseNumericParts(v1);
            List<Integer> b = parseNumericParts(v2);
            int n = Math.max(a.size(), b.size());
            for (int i = 0; i < n; i++) {
                int ai = i < a.size() ? a.get(i) : 0;
                int bi = i < b.size() ? b.get(i) : 0;
                if (ai != bi) {
                    return Integer.compare(ai, bi);
                }
            }
            return 0;
        } catch (Exception ignore) {
            // 兜底：避免因为异常导致门禁失效/崩溃
            return v1.compareTo(v2);
        }
    }

    public static boolean isLower(String client, String target) {
        return compare(client, target) < 0;
    }

    public static boolean isHigher(String client, String target) {
        return compare(client, target) > 0;
    }

    private static List<Integer> parseNumericParts(String v) {
        String s = v.trim();
        // 分隔符：. 或 -（例如 1.2.3-beta）
        String[] parts = s.split("[\\.-]");
        List<Integer> out = new ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            out.add(parseLeadingInt(p));
        }
        return out;
    }

    private static int parseLeadingInt(String token) {
        int n = 0;
        boolean seen = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= '0' && c <= '9') {
                seen = true;
                n = n * 10 + (c - '0');
            } else {
                break;
            }
        }
        return seen ? n : 0;
    }
}


