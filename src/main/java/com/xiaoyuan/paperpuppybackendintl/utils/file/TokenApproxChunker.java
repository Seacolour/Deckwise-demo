package com.xiaoyuan.paperpuppybackendintl.utils.file;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * token 近似分片器（不依赖 tokenizer，实现足够稳定的“上限控制”）
 *
 * 目标：
 * - 以段落/页为优先边界，尽量保持语义连续
 * - 对极长段落进行句子级拆分，最终必要时硬切
 *
 * 说明：
 * - 这里的 token 估算只用于“上限控制”，不会追求与某个模型 tokenizer 完全一致
 * - 默认用于 4000 tokens/片 的场景
 */
public final class TokenApproxChunker {

    /**
     * 近似 token：英文/数字词、CJK 单字、标点都算一个 token。
     * - 对英文会略偏保守（token 偏大），但更安全
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[A-Za-z0-9]+"
                    + "|[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]"
                    + "|[^\\s]"
    );

    private static final Pattern PAGE_MARKER = Pattern.compile("^--- PAGE \\d+ ---$");

    private TokenApproxChunker() {
    }

    /**
     * 对外暴露的 token 近似计数（用于上下文预算控制）。
     */
    public static int estimateTokensPublic(String s) {
        return estimateTokens(s);
    }

    public static List<String> chunk(String text, int maxTokensPerChunk) {
        return chunk(text, maxTokensPerChunk, 20000);
    }

    /**
     * @param maxCharsPerChunk 额外的硬限制，防止某些情况下 chunk 字符过大（例如 token 估算偏小）
     */
    public static List<String> chunk(String text, int maxTokensPerChunk, int maxCharsPerChunk) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int maxTokens = Math.max(1, maxTokensPerChunk);
        int maxChars = Math.max(256, maxCharsPerChunk);

        List<String> units = splitToUnits(text);
        List<String> out = new ArrayList<>();

        StringBuilder cur = new StringBuilder();

        for (String unit : units) {
            if (unit == null) {
                continue;
            }
            String u = unit.trim();
            // 保留页标记（即使周围是空白）
            boolean isPageMarker = PAGE_MARKER.matcher(u).matches();
            if (!isPageMarker && u.isBlank()) {
                continue;
            }

            int uTokens = estimateTokens(u);
            // 单元过大：先落当前 chunk，再把单元拆分
            if (uTokens > maxTokens || u.length() > maxChars) {
                flush(out, cur);
                List<String> split = splitOversizedUnit(u, maxTokens, maxChars);
                for (String piece : split) {
                    if (piece == null || piece.isBlank()) {
                        continue;
                    }
                    out.add(piece.trim());
                }
                continue;
            }

            // 尝试合并到当前 chunk
            String candidate = cur.isEmpty() ? u : (cur + "\n\n" + u);
            int candidateTokens = estimateTokens(candidate);
            if (candidateTokens <= maxTokens && candidate.length() <= maxChars) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                flush(out, cur);
                cur.append(u);
            }
        }

        flush(out, cur);
        return out;
    }

    /**
     * 把文本拆成“语义单元”：优先页标记/段落。
     */
    private static List<String> splitToUnits(String text) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> units = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();

        for (String line : lines) {
            String l = line == null ? "" : line;
            String trimmed = l.trim();
            if (PAGE_MARKER.matcher(trimmed).matches()) {
                // flush paragraph
                if (!paragraph.isEmpty()) {
                    units.add(paragraph.toString().trim());
                    paragraph.setLength(0);
                }
                units.add(trimmed);
                continue;
            }

            // 空行作为段落边界
            if (trimmed.isBlank()) {
                if (!paragraph.isEmpty()) {
                    units.add(paragraph.toString().trim());
                    paragraph.setLength(0);
                }
                continue;
            }

            if (!paragraph.isEmpty()) {
                paragraph.append('\n');
            }
            paragraph.append(l);
        }

        if (!paragraph.isEmpty()) {
            units.add(paragraph.toString().trim());
        }
        return units;
    }

    private static void flush(List<String> out, StringBuilder cur) {
        if (cur == null || cur.isEmpty()) {
            return;
        }
        String s = cur.toString().trim();
        if (!s.isBlank()) {
            out.add(s);
        }
        cur.setLength(0);
    }

    private static int estimateTokens(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        Matcher m = TOKEN_PATTERN.matcher(s);
        int c = 0;
        while (m.find()) {
            c++;
        }
        return c;
    }

    private static List<String> splitOversizedUnit(String unit, int maxTokens, int maxChars) {
        // 1) 句子级拆分
        List<String> pieces = splitBySentence(unit);
        if (pieces.size() <= 1) {
            // 2) 再兜底：按行拆
            pieces = splitByLine(unit);
        }

        // 重新打包（句子 -> chunk）
        List<String> packed = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        for (String p : pieces) {
            if (p == null || p.isBlank()) {
                continue;
            }
            String piece = p.trim();
            int t = estimateTokens(piece);

            if (t > maxTokens || piece.length() > maxChars) {
                flush(packed, cur);
                // 3) 最终兜底：硬切（按字符滑窗 + token 回退）
                packed.addAll(hardCut(piece, maxTokens, maxChars));
                continue;
            }

            String candidate = cur.isEmpty() ? piece : (cur + " " + piece);
            if (estimateTokens(candidate) <= maxTokens && candidate.length() <= maxChars) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                flush(packed, cur);
                cur.append(piece);
            }
        }
        flush(packed, cur);
        return packed;
    }

    private static List<String> splitBySentence(String text) {
        // 简易句子边界（中英文标点）
        String[] arr = text.split("(?<=[\\.\\!\\?。？！])\\s+");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private static List<String> splitByLine(String text) {
        String[] arr = text.split("\\n+");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private static List<String> hardCut(String text, int maxTokens, int maxChars) {
        List<String> out = new ArrayList<>();
        String t = text == null ? "" : text.trim();
        if (t.isBlank()) {
            return out;
        }

        int start = 0;
        while (start < t.length()) {
            int end = Math.min(t.length(), start + maxChars);
            String slice = t.substring(start, end).trim();
            if (slice.isBlank()) {
                break;
            }

            // 如果 token 超限，回退 end
            while (slice.length() > 256 && estimateTokens(slice) > maxTokens) {
                end = start + Math.max(256, (int) (slice.length() * 0.8));
                end = Math.min(end, t.length());
                slice = t.substring(start, end).trim();
            }

            // 仍超限：再进一步缩小（保底）
            while (estimateTokens(slice) > maxTokens && slice.length() > 64) {
                end = start + Math.max(64, slice.length() - 64);
                end = Math.min(end, t.length());
                slice = t.substring(start, end).trim();
            }

            if (!slice.isBlank()) {
                out.add(slice);
            }
            start = end;
        }
        return out;
    }
}


