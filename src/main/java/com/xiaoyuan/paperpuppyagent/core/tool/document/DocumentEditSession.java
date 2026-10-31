package com.xiaoyuan.paperpuppyagent.core.tool.document;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档编辑会话。
 *
 * <p>
 * 会话级别的文档工作副本管理，持有 Agent 侧的权威文档状态。
 * </p>
 *
 * <p>
 * 生命周期：
 * - 创建：首次 document_diff 调用时
 * - 更新：每次 diff 应用后
 * - 销毁：会话结束时
 * </p>
 */
@Slf4j
@Getter
public class DocumentEditSession {

    private final long documentId;
    private final int documentVersion;
    private String workingContent;
    private int version;
    private final List<AppliedHunk> history;

    public DocumentEditSession(long documentId, String initialContent, int documentVersion) {
        this.documentId = documentId;
        this.documentVersion = Math.max(documentVersion, 1);
        this.workingContent = initialContent != null ? initialContent : "";
        this.version = 1;
        this.history = new ArrayList<>();
        log.info("[DocumentEditSession] Created: documentId={}, documentVersion={}, initialContentLen={}",
                documentId, this.documentVersion, this.workingContent.length());
    }

    /**
     * 应用 hunks 到工作副本。
     *
     * @param hunks 要应用的 hunks
     * @return 应用结果
     */
    public MarkdownDiffApplier.ApplyResult applyHunks(List<EditHunk> hunks) {
        log.info("[DocumentEditSession] Applying {} hunks to document {}", hunks.size(), documentId);

        MarkdownDiffApplier.ApplyResult result = MarkdownDiffApplier.apply(workingContent, hunks);

        if (result.success()) {
            // 更新工作副本
            this.workingContent = result.newContent();
            this.version++;

            // 记录历史
            if (result.appliedHunkIds() != null && !result.appliedHunkIds().isEmpty()) {
                for (EditHunk h : hunks) {
                    if (h == null || h.hunkId() == null) {
                        continue;
                    }
                    if (!result.appliedHunkIds().contains(h.hunkId())) {
                        continue;
                    }
                    history.add(new AppliedHunk(
                            h.hunkId(),
                            h.op(),
                            h.reason(),
                            version));
                }
            }

            log.info(
                    "[DocumentEditSession] Applied hunks: documentId={}, newVersion={}, appliedCount={}, newContentLen={}",
                    documentId, version, result.appliedHunkIds().size(), workingContent.length());
        }

        return result;
    }

    /**
     * 获取内容预览（截断）。
     */
    public String getContentPreview(int maxLength) {
        if (workingContent.length() <= maxLength) {
            return workingContent;
        }
        return workingContent.substring(0, maxLength) + "...";
    }

    /**
     * 已应用的 Hunk 记录。
     */
    public record AppliedHunk(
            String hunkId,
            EditOp op,
            String reason,
            int appliedAtVersion) {
    }
}
