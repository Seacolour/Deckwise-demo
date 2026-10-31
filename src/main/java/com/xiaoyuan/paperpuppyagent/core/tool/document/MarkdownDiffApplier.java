package com.xiaoyuan.paperpuppyagent.core.tool.document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Markdown Diff 应用器。
 *
 * <p>
 * 将 EditHunk 列表应用到文档内容上。
 * </p>
 *
 * <p>
 * 语义说明（与 MockAgent 一致）：
 * - 行号是 1-based，包含
 * - INSERT：在 endLine 之后插入（endLine == 0 表示在开头插入）
 * - DELETE/REPLACE：必须校验 before 内容匹配
 * - Hunks 按 startLine 降序应用，避免索引偏移问题
 * </p>
 */
public final class MarkdownDiffApplier {

    private MarkdownDiffApplier() {
    }

    /**
     * 应用 hunks 到文档内容。
     *
     * @param content 原始 Markdown 内容
     * @param hunks   要应用的 hunks
     * @return 应用结果
     */
    public static ApplyResult apply(String content, List<EditHunk> hunks) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(hunks, "hunks");

        // 【规范化】统一换行符为 \n（避免 Windows \r\n 混乱）
        String normalizedContent = content.replace("\r\n", "\n").replace("\r", "\n");

        // 分割为行列表（注意：split 会丢弃行分隔符，行号基于该行数组）
        List<String> lines = new ArrayList<>(List.of(normalizedContent.split("\n", -1)));
        List<String> appliedHunkIds = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 按 startLine 降序排序，避免索引偏移
        List<EditHunk> sorted = hunks.stream()
                .sorted(Comparator.comparingInt(EditHunk::startLine).reversed())
                .toList();

        // 原子语义：只要任意一个 hunk 出现 before mismatch，则本次 apply 视为失败（success=false），且不修改内容。
        for (EditHunk h : sorted) {
            try {
                applyOne(lines, h);
                if (h != null && h.hunkId() != null) {
                    appliedHunkIds.add(h.hunkId());
                }
            } catch (ApplyConflictException e) {
                warnings.add(buildConflictWarning(e));
                // 不提交任何修改：返回原始内容（normalized）
                return new ApplyResult(false, List.of(), normalizedContent, warnings);
            }
        }

        // 重新拼接为字符串
        String newContent = String.join("\n", lines);

        return new ApplyResult(true, appliedHunkIds, newContent, warnings);
    }

    private static String buildConflictWarning(ApplyConflictException e) {
        if (e == null) {
            return "apply conflict";
        }
        String msg = Objects.toString(e.getMessage(), "apply conflict");
        String actual = previewLines(e.actual(), 3);
        String expected = previewLines(e.expected(), 3);
        return msg + " | actual=" + actual + " | expected=" + expected;
    }

    private static String previewLines(List<String> lines, int max) {
        if (lines == null || lines.isEmpty()) {
            return "[]";
        }
        int n = Math.max(1, max);
        int end = Math.min(lines.size(), n);
        List<String> head = lines.subList(0, end);
        String suffix = lines.size() > end ? ",...(len=" + lines.size() + ")" : "";
        return head.toString() + suffix;
    }

    private static void applyOne(List<String> lines, EditHunk h) {
        if (h == null)
            return;
        if (h.op() == null)
            throw new IllegalArgumentException("hunk.op is required");

        int start = h.startLine();
        int end = h.endLine();

        // 判断是否为无行号模式
        boolean noLineNumberMode = (start <= 0 && end <= 0);

        if (noLineNumberMode) {
            // 无行号模式：使用 before 内容进行全文匹配
            applyWithBeforeMatch(lines, h);
        } else {
            // 有行号模式：使用原有逻辑
            applyWithLineNumbers(lines, h, start, end);
        }
    }

    /**
     * 有行号模式：基于 start_line/end_line 定位并应用。
     */
    private static void applyWithLineNumbers(List<String> lines, EditHunk h, int start, int end) {
        switch (h.op()) {
            case INSERT -> {
                int insertAt = insertIndex(end, lines.size());
                List<String> after = nvl(h.after());
                lines.addAll(insertAt, after);
            }
            case DELETE -> {
                int from = toIndex(start);
                int to = toIndex(end);
                ensureRange(lines, from, to, h);
                List<String> actual = slice(lines, from, to);
                if (!actual.equals(nvl(h.before()))) {
                    throw new ApplyConflictException("DELETE before mismatch for " + id(h), actual, nvl(h.before()));
                }
                lines.subList(from, to + 1).clear();
            }
            case REPLACE -> {
                int from = toIndex(start);
                int to = toIndex(end);
                ensureRange(lines, from, to, h);
                List<String> actual = slice(lines, from, to);
                if (!actual.equals(nvl(h.before()))) {
                    throw new ApplyConflictException("REPLACE before mismatch for " + id(h), actual, nvl(h.before()));
                }
                lines.subList(from, to + 1).clear();
                lines.addAll(from, nvl(h.after()));
            }
        }
    }

    /**
     * 无行号模式：使用 before 内容在全文中进行唯一匹配定位。
     */
    private static void applyWithBeforeMatch(List<String> lines, EditHunk h) {
        List<String> before = nvl(h.before());

        // INSERT 在无行号模式下需要特殊处理
        if (h.op() == EditOp.INSERT) {
            if (before.isEmpty()) {
                // 无 before 的 INSERT 无法定位，报错
                throw new ApplyConflictException(
                        "INSERT without line numbers requires 'before' to locate insertion point: " + id(h),
                        List.of(), before);
            }
            // 使用 before 定位插入点（在 before 匹配位置之后插入）
            List<Integer> matchPositions = findSubsequenceMatches(lines, before);
            validateMatchCount(matchPositions, h, before);

            int matchStart = matchPositions.get(0);
            int insertAt = matchStart + before.size(); // 在 before 之后插入
            lines.addAll(insertAt, nvl(h.after()));
            return;
        }

        // DELETE / REPLACE 必须有 before
        if (before.isEmpty()) {
            throw new ApplyConflictException(
                    h.op() + " without line numbers requires non-empty 'before': " + id(h),
                    List.of(), before);
        }

        // 在全文中查找 before 子序列匹配
        List<Integer> matchPositions = findSubsequenceMatches(lines, before);
        validateMatchCount(matchPositions, h, before);

        // 唯一匹配，执行操作
        int from = matchPositions.get(0);
        int to = from + before.size() - 1;

        switch (h.op()) {
            case DELETE -> lines.subList(from, to + 1).clear();
            case REPLACE -> {
                lines.subList(from, to + 1).clear();
                lines.addAll(from, nvl(h.after()));
            }
            default -> throw new IllegalArgumentException("Unexpected op in no-line-number mode: " + h.op());
        }
    }

    /**
     * 在 lines 中查找 pattern 子序列的所有匹配位置（0-based 起始索引）。
     */
    private static List<Integer> findSubsequenceMatches(List<String> lines, List<String> pattern) {
        List<Integer> positions = new ArrayList<>();
        if (pattern.isEmpty()) {
            return positions;
        }
        outer: for (int i = 0; i <= lines.size() - pattern.size(); i++) {
            for (int j = 0; j < pattern.size(); j++) {
                if (!lines.get(i + j).equals(pattern.get(j))) {
                    continue outer;
                }
            }
            positions.add(i);
        }
        return positions;
    }

    /**
     * 验证匹配数量：必须恰好为 1。
     */
    private static void validateMatchCount(List<Integer> positions, EditHunk h, List<String> before) {
        if (positions.isEmpty()) {
            throw new ApplyConflictException(
                    "NOT_FOUND: 'before' content not found in document for " + id(h) +
                            ". Use document_read or document_locate to get exact content.",
                    List.of(), before);
        }
        if (positions.size() > 1) {
            throw new ApplyConflictException(
                    "AMBIGUOUS: 'before' content matches " + positions.size() +
                            " locations in document for " + id(h) +
                            ". Use document_locate with more specific query, or provide start_line/end_line.",
                    List.of(), before);
        }
    }

    private static int insertIndex(int endLine, int size) {
        if (endLine == 0)
            return 0;
        int idx = endLine; // insert AFTER endLine
        if (idx < 0 || idx > size) {
            throw new IllegalArgumentException("insert anchor out of range: endLine=" + endLine + ", size=" + size);
        }
        return idx;
    }

    private static int toIndex(int line) {
        if (line <= 0)
            throw new IllegalArgumentException("line must be >= 1 for delete/replace, got: " + line);
        return line - 1;
    }

    private static void ensureRange(List<String> lines, int from, int to, EditHunk h) {
        if (from < 0 || to < 0 || from >= lines.size() || to >= lines.size() || to < from) {
            throw new IllegalArgumentException(
                    "range out of bounds for " + id(h) + ": [" + from + "," + to + "], size=" + lines.size());
        }
    }

    private static List<String> slice(List<String> lines, int from, int to) {
        return new ArrayList<>(lines.subList(from, to + 1));
    }

    private static List<String> nvl(List<String> v) {
        return v == null ? List.of() : v;
    }

    private static String id(EditHunk h) {
        return h.hunkId() == null ? "(no-hunkId)" : h.hunkId();
    }

    /**
     * 应用结果。
     */
    public record ApplyResult(
            boolean success,
            List<String> appliedHunkIds,
            String newContent,
            List<String> warnings) {
    }

    /**
     * 应用冲突异常。
     */
    public static class ApplyConflictException extends RuntimeException {
        private final List<String> actual;
        private final List<String> expected;

        public ApplyConflictException(String message, List<String> actual, List<String> expected) {
            super(message);
            this.actual = actual;
            this.expected = expected;
        }

        public List<String> actual() {
            return actual;
        }

        public List<String> expected() {
            return expected;
        }
    }
}
