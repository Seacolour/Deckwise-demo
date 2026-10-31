package com.xiaoyuan.paperpuppyagent.core.tool.document;

import java.util.List;

/**
 * 单个编辑 Hunk（差异块）。
 *
 * <p>
 * 参考 MockAgent 的设计，每个 Hunk 代表一次原子编辑操作。
 * </p>
 *
 * @param hunkId    Hunk 标识符（用于追溯）
 * @param op        操作类型：INSERT / DELETE / REPLACE
 * @param startLine 起始行号（1-based，包含）
 * @param endLine   结束行号（1-based，包含）。INSERT 时表示插入位置锚点（在该行之后插入）
 * @param before    原始内容（DELETE/REPLACE 时必填，用于校验）
 * @param after     新内容（INSERT/REPLACE 时必填）
 * @param reason    修改理由（用于追溯和用户展示）
 */
public record EditHunk(
        String hunkId,
        EditOp op,
        int startLine,
        int endLine,
        List<String> before,
        List<String> after,
        String reason) {
    /**
     * 从 JSON 参数构建 EditHunk。
     */
    public static EditHunk fromJson(com.fasterxml.jackson.databind.JsonNode node) {
        String hunkId = node.has("hunk_id") ? node.get("hunk_id").asText() : null;
        String opStr = node.has("op") ? node.get("op").asText().toUpperCase() : "REPLACE";
        EditOp op = EditOp.valueOf(opStr);
        int startLine = node.has("start_line") ? node.get("start_line").asInt() : 0;
        int endLine = node.has("end_line") ? node.get("end_line").asInt() : 0;

        List<String> before = null;
        if (node.has("before") && node.get("before").isArray()) {
            before = new java.util.ArrayList<>();
            for (var item : node.get("before")) {
                before.add(item.asText());
            }
        }

        List<String> after = null;
        if (node.has("after") && node.get("after").isArray()) {
            after = new java.util.ArrayList<>();
            for (var item : node.get("after")) {
                after.add(item.asText());
            }
        }

        String reason = node.has("reason") ? node.get("reason").asText() : null;

        return new EditHunk(hunkId, op, startLine, endLine, before, after, reason);
    }
}
