"use client";

import { useEffect, useRef, useState, type Ref } from "react";
import { Check, ChevronDown, CircleStop, Copy, FileText, Image as ImageIcon, LoaderCircle, Paperclip, TriangleAlert, Wrench } from "lucide-react";
import { copyTextToClipboard } from "@/src/lib/clipboard/copyTextToClipboard";
import { isHiddenAssistantText } from "./conversationText";
import { WorkbenchMarkdown } from "./WorkbenchMarkdown";
import type { ConversationAttachment, ConversationEntry, ConversationPart } from "./conversationTypes";
import styles from "./conversationTimeline.module.css";

export type { ConversationEntry, ConversationPart } from "./conversationTypes";

function UserAttachment({ attachment }: { attachment: ConversationAttachment }) {
  const Icon = attachment.kind === "image" ? ImageIcon : attachment.kind === "document" ? FileText : Paperclip;
  const suffix = attachment.name.includes(".") ? attachment.name.split(".").at(-1)?.toUpperCase() : null;
  const extension = suffix && suffix.length <= 8 ? suffix : "FILE";
  return (
    <span className={styles.userAttachment} aria-label={`已发送附件 ${attachment.name}`} title={attachment.name}>
      <span className={styles.userAttachmentIcon}><Icon size={17} aria-hidden="true" /></span>
      <span className={styles.userAttachmentText}>
        <span className={styles.userAttachmentName}>{attachment.name}</span>
        <span className={styles.userAttachmentType}>{extension}</span>
      </span>
    </span>
  );
}

type ToolPart = Extract<ConversationPart, { kind: "tool" }>;
type ToolMetadata = { label: string; value: string };

const DOCUMENT_TOOLS = new Set([
  "document_read", "document_diff", "document_save", "document_locate",
  "document_update_summary", "document_create",
]);
const FILE_TOOLS = new Set(["file_list", "file_content_read", "file_read_url", "image_read"]);
const JSON_TOOLS = new Set(["json_structure_read", "json_subtree_read"]);
const MAX_METADATA_JSON_LENGTH = 64_000;

const TOOL_STATUS = {
  running: { label: "执行中", Icon: LoaderCircle },
  completed: { label: "已完成", Icon: Check },
  error: { label: "执行失败", Icon: TriangleAlert },
  stopped: { label: "已停止", Icon: CircleStop },
} as const;

function parsedObject(raw: string | null): Record<string, unknown> | null {
  if (!raw || raw.length > MAX_METADATA_JSON_LENGTH) return null;
  try {
    const value: unknown = JSON.parse(raw);
    return value !== null && typeof value === "object" && !Array.isArray(value)
      ? value as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

function boundedInteger(value: unknown, min: number, max: number): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= min && value <= max
    ? value
    : null;
}

function safeToolMetadata(part: ToolPart): ToolMetadata[] {
  const isDocumentTool = DOCUMENT_TOOLS.has(part.name);
  const isFileTool = FILE_TOOLS.has(part.name);
  const isJsonTool = JSON_TOOLS.has(part.name);
  if (!isDocumentTool && !isFileTool && !isJsonTool) return [];

  const args = parsedObject(part.argumentsRaw);
  const result = parsedObject(part.resultRaw);
  const metadata: ToolMetadata[] = [];
  const addNumber = (label: string, value: unknown, min: number, max: number) => {
    const safe = boundedInteger(value, min, max);
    if (safe !== null) metadata.push({ label, value: String(safe) });
  };

  if (isDocumentTool || isJsonTool) {
    addNumber("文档 ID", args?.document_id ?? result?.document_id, 1, Number.MAX_SAFE_INTEGER);
  }
  if (isFileTool || isJsonTool) {
    addNumber("文件 ID", args?.file_id ?? result?.file_id, 1, Number.MAX_SAFE_INTEGER);
  }
  if (part.name === "document_diff" && Array.isArray(args?.hunks)) {
    addNumber("修改块", args.hunks.length, 0, 999);
  }
  if (part.name === "file_content_read") {
    addNumber("起始切片", args?.start_fragment, 1, 1_000_000);
    addNumber("请求切片", args?.max_fragments, 1, 50);
    addNumber("返回切片", result?.returned_fragments, 0, 50);
    addNumber("总切片", result?.total_fragments, 0, 1_000_000);
  }
  if (part.name === "file_list" && Array.isArray(result?.files)) {
    addNumber("文件数", result.files.length, 0, 999_999);
  }
  if (typeof result?.success === "boolean") {
    metadata.push({ label: "执行结果", value: result.success ? "成功" : "失败" });
  }
  return metadata;
}

function ToolRow({ part }: { part: ToolPart }) {
  const { label, Icon } = TOOL_STATUS[part.status];
  const metadata = safeToolMetadata(part);

  return (
    <details className={`${styles.tool} ${styles[`tool_${part.status}`]}`}>
      <summary className={styles.toolSummary}>
        <span className={styles.toolIcon} aria-hidden="true"><Wrench size={15} /></span>
        <span className={styles.toolName}>{part.name || "未命名工具"}</span>
        <span className={styles.toolStatus} role="status" aria-live="polite">
          <Icon size={14} aria-hidden="true" className={part.status === "running" ? styles.spin : undefined} />
          {label}
        </span>
        <ChevronDown size={15} className={styles.toolChevron} aria-hidden="true" />
      </summary>
      <div className={styles.toolDetails}>
        <span>执行状态：{label}。</span>
        {metadata.length > 0 && (
          <dl className={styles.toolMetadata}>
            {metadata.map(({ label: metadataLabel, value }) => (
              <div key={metadataLabel} className={styles.toolMetaItem}>
                <dt>{metadataLabel}</dt><dd>{value}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </details>
  );
}

function MessageCopyButton({ text, label }: { text: string; label: string }) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");
  const resetTimer = useRef<number | null>(null);

  useEffect(() => () => {
    if (resetTimer.current !== null) window.clearTimeout(resetTimer.current);
  }, []);

  const copy = async () => {
    const copied = await copyTextToClipboard(text);
    setCopyState(copied ? "copied" : "failed");
    if (resetTimer.current !== null) window.clearTimeout(resetTimer.current);
    resetTimer.current = window.setTimeout(() => setCopyState("idle"), 1800);
  };

  return (
    <button type="button" className={styles.copyButton} aria-label={label} title={label}
      onClick={() => void copy()}>
      {copyState === "copied" ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
      {copyState !== "idle" && <span role="status">{copyState === "copied" ? "已复制" : "复制失败"}</span>}
    </button>
  );
}

function AssistantEntry({ entry, activity }: { entry: Extract<ConversationEntry, { kind: "assistant" }>; activity?: string }) {
  const visibleText = entry.parts.flatMap((part) => part.kind === "text" && part.text.trim() && !isHiddenAssistantText(part.text)
    ? [part.text.trim()]
    : []);
  const hasVisibleText = visibleText.length > 0;
  const copyableText = visibleText.join("\n\n");
  const hasRunningTool = entry.parts.some((part) => part.kind === "tool" && part.status === "running");
  const showPending = entry.pending && (!hasVisibleText || hasRunningTool);
  const pendingLabel = hasRunningTool ? "工具执行中…" : activity?.trim() || "正在处理请求…";

  return (
    <article className={styles.assistantEntry} aria-label="AgentWorkbench 回复">
      <div className={styles.assistantContent}>
        <div className={styles.parts}>
          {entry.parts.map((part) => part.kind === "tool"
            ? <ToolRow key={part.id} part={part} />
            : part.text.trim() && !isHiddenAssistantText(part.text)
              ? <div className={styles.markdown} key={part.id}><WorkbenchMarkdown text={part.text} /></div>
              : null)}
          {showPending && (
            <div className={styles.pending} role="status" aria-live="polite">
              <LoaderCircle size={15} className={styles.spin} aria-hidden="true" />
              <span>{pendingLabel}</span>
            </div>
          )}
        </div>
        {copyableText && !entry.pending && (
          <div className={styles.assistantActions}>
            <MessageCopyButton text={copyableText} label="复制 AI 回复" />
          </div>
        )}
      </div>
    </article>
  );
}

export function ConversationTimeline({
  entries,
  activity,
  historyLoading = false,
  bottomRef,
}: {
  entries: ConversationEntry[];
  activity?: string;
  historyLoading?: boolean;
  bottomRef?: Ref<HTMLDivElement>;
}) {
  return (
    <div className={styles.timeline} aria-label="会话消息">
      {historyLoading && <p className={styles.historyLoading} role="status">正在加载历史消息…</p>}
      {entries.map((entry) => entry.kind === "user" ? (
        <article key={entry.id} className={styles.userEntry} aria-label="你的消息">
          <div className={styles.userContent}>
            {entry.attachments && entry.attachments.length > 0 && (
              <div className={styles.userAttachments} aria-label="这条消息的附件">
                {entry.attachments.map((attachment, index) => (
                  <UserAttachment key={`${attachment.kind}-${attachment.fileId ?? attachment.documentId ?? attachment.name}-${index}`}
                    attachment={attachment} />
                ))}
              </div>
            )}
            <p className={styles.userBubble}>{entry.text}</p>
            <div className={styles.userActions}>
              <MessageCopyButton text={entry.text} label="复制你的消息" />
            </div>
          </div>
        </article>
      ) : <AssistantEntry key={entry.id} entry={entry} activity={activity} />)}
      <div ref={bottomRef} aria-hidden="true" />
    </div>
  );
}
