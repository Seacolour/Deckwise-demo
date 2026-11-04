import { apiFetchWithAuth } from "@/src/lib/api/fetcherWithAuth";
import type { FileUploadBatchResponse } from "@/src/lib/types/all/fileUploadBatchResponse";
import type { ProjectFileMappingDTO } from "@/src/lib/types/all/projectFileMappingDTO";
import type { PrivateObjectAccessUrlResponse } from "@/src/lib/types/all/privateObjectAccessUrlResponse";
import type { ConversationAttachment, ConversationEntry, ConversationPart } from "./conversationTypes";
import { parseWorkbenchToolEvent, toolResultStatus } from "./conversationEvents";

export type WorkbenchProject = {
  id: string | number;
  name: string;
  description?: string | null;
};

export type WorkbenchSession = {
  id: string | number;
  projectId: string | number;
  title?: string | null;
  lastUserMessage?: string | null;
  lastActiveAt?: string | null;
};

export type WorkbenchFile = {
  id: string;
  cacheKey: string;
  name: string;
  sizeBytes: number | null;
  documentId: string | null;
  image: boolean;
};

export async function listWorkbenchFiles(projectId: string): Promise<WorkbenchFile[]> {
  const files = await apiFetchWithAuth<ProjectFileMappingDTO[]>(`/projects/${projectId}/files`, {
    requestPolicy: "query",
  });
  return files.flatMap((file) => {
    const cacheKey = file.cacheKey?.trim();
    if (file.id == null || !cacheKey) return [];
    const id = String(file.id);
    const name = file.fileName?.trim() || `文件 ${id}`;
    return [{
      id,
      cacheKey,
      name,
      sizeBytes: file.sizeBytes ?? null,
      documentId: file.documentId == null ? null : String(file.documentId),
      image: /\.(?:png|jpe?g|webp|gif)$/i.test(name) || /\.(?:png|jpe?g|webp|gif)$/i.test(file.objectKey ?? ""),
    }];
  });
}

export async function uploadWorkbenchFile(projectId: string, file: File, modelId?: string): Promise<FileUploadBatchResponse> {
  const body = new FormData();
  body.append("files", file);
  return apiFetchWithAuth<FileUploadBatchResponse>("/files/upload/batch", {
    method: "POST",
    params: { projectId, uploadSource: "user_upload", ...(modelId ? { model: modelId } : {}) },
    body,
    requestPolicy: "upload",
  });
}

export async function getWorkbenchFilePreviewUrl(projectId: string, fileId: string): Promise<string> {
  const response = await apiFetchWithAuth<PrivateObjectAccessUrlResponse>(`/files/${fileId}/access-url`, {
    params: { projectId },
    requestPolicy: "query",
  });
  const rawUrl = response.accessUrl?.trim();
  if (!rawUrl) throw new Error("暂时无法预览这个文件");
  const url = new URL(rawUrl);
  if (url.protocol !== "https:" && url.protocol !== "http:") throw new Error("文件预览地址无效");
  return url.toString();
}

export function getWorkbenchInlinePreviewPath(projectId: string, file: WorkbenchFile): string | null {
  const extension = file.name.split(".").at(-1)?.toLowerCase();
  const kind = extension && /^(pdf|txt|json|md|png|jpg|jpeg|webp|gif)$/.test(extension)
    ? extension : file.image ? "image" : null;
  if (!kind) return null;
  return `/api/workbench/files/${encodeURIComponent(file.id)}/preview?projectId=${encodeURIComponent(projectId)}&kind=${kind}`;
}

type Page<T> = { records?: T[] };
type SessionMessage = {
  id?: string | number | null;
  seq?: number | null;
  role?: string | null;
  type?: string | null;
  content?: string | null;
  rawContent?: string | null;
  status?: string | null;
  agentMessageGroupId?: string | null;
  attachments?: unknown;
};

function messageAttachments(raw: unknown): ConversationAttachment[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((item): ConversationAttachment[] => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return [];
    const record = item as Record<string, unknown>;
    const kind = record.kind;
    const name = typeof record.name === "string" ? record.name.trim().slice(0, 180) : "";
    if (!name || (kind !== "file" && kind !== "image" && kind !== "document")) return [];
    return [{ kind, name,
      ...(typeof record.fileId === "string" ? { fileId: record.fileId } : {}),
      ...(typeof record.documentId === "string" ? { documentId: record.documentId } : {}),
      ...(typeof record.cacheKey === "string" ? { cacheKey: record.cacheKey } : {}),
    }];
  }).slice(0, 15);
}

export async function listWorkbenchProjects(): Promise<WorkbenchProject[]> {
  const page = await apiFetchWithAuth<Page<WorkbenchProject>>("/projects/list", {
    method: "POST",
    body: { current: 1, pageSize: 100, sortField: "updatedAt", sortOrder: "desc", archived: false },
    requestPolicy: "query",
  });
  return page.records ?? [];
}

export async function createWorkbenchProject(name = "我的工作区"): Promise<WorkbenchProject> {
  return apiFetchWithAuth<WorkbenchProject>("/projects/create", {
    method: "POST",
    body: { name, description: "与 Agent 一起处理文档和任务" },
    requestPolicy: "mutation",
  });
}

export async function renameWorkbenchProject(projectId: string, name: string): Promise<WorkbenchProject> {
  return apiFetchWithAuth<WorkbenchProject>(`/projects/${projectId}`, {
    method: "PUT",
    body: { name },
    requestPolicy: "mutation",
  });
}

export async function deleteWorkbenchProject(projectId: string): Promise<void> {
  await apiFetchWithAuth<boolean>(`/projects/${projectId}`, {
    method: "DELETE",
    requestPolicy: "mutation",
  });
}

export async function listWorkbenchSessions(projectId: string): Promise<WorkbenchSession[]> {
  const page = await apiFetchWithAuth<Page<WorkbenchSession>>(`/projects/${projectId}/list`, {
    method: "POST",
    body: { current: 1, pageSize: 100, sortField: "lastActiveAt", sortOrder: "desc" },
    requestPolicy: "query",
  });
  return page.records ?? [];
}

export async function createWorkbenchSession(projectId: string, firstMessage: string): Promise<WorkbenchSession> {
  const title = firstMessage.trim().replace(/\s+/g, " ").slice(0, 48) || "新对话";
  return apiFetchWithAuth<WorkbenchSession>(`/projects/${projectId}/create`, {
    method: "POST",
    body: { title },
    requestPolicy: "mutation",
  });
}

function historyEntries(rows: SessionMessage[]): ConversationEntry[] {
  const entries: ConversationEntry[] = [];
  let assistant: Extract<ConversationEntry, { kind: "assistant" }> | null = null;
  let assistantGroupId: string | null = null;
  for (const [index, row] of rows.entries()) {
    const id = String(row.id ?? row.seq ?? index);
    const role = row.role?.toLowerCase();
    const type = row.type?.toLowerCase() ?? "text";
    if (role === "user" && type === "text") {
      const text = row.content ?? row.rawContent ?? "";
      const attachments = messageAttachments(row.attachments);
      if (text.trim() || attachments.length) entries.push({ id, kind: "user", text,
        ...(attachments.length ? { attachments } : {}) });
      assistant = null;
      assistantGroupId = null;
      continue;
    }
    if ((role !== "assistant" && role !== "tool") || (type !== "text" && type !== "tool_output")) continue;
    if (role === "tool" && type !== "tool_output") continue;
    const groupId = row.agentMessageGroupId ?? null;
    if (!assistant || (groupId && assistantGroupId && groupId !== assistantGroupId)) {
      assistant = { id: `assistant-${id}`, kind: "assistant", parts: [] };
      assistantGroupId = groupId;
      entries.push(assistant);
    }
    if (role === "assistant" && type === "text") {
      const text = row.content ?? row.rawContent ?? "";
      if (text.trim()) assistant.parts.push({ id, kind: "text", text });
      continue;
    }
    const event = parseWorkbenchToolEvent(row.content ?? "");
    if (!event) continue;
    const parts = assistant.parts;
    if (event.phase === "result") {
      const callIndex = event.callId
        ? parts.findLastIndex((part) => part.kind === "tool" && part.callId === event.callId)
        : parts.findLastIndex((part) => part.kind === "tool" && part.name === event.name && part.status === "running");
      if (callIndex >= 0) {
        const call = parts[callIndex] as Extract<ConversationPart, { kind: "tool" }>;
        parts[callIndex] = { ...call, resultRaw: event.resultRaw,
          status: toolResultStatus(event.resultRaw, row.status) };
        continue;
      }
    }
    parts.push({
      id, kind: "tool", name: event.name, callId: event.callId,
      argumentsRaw: event.argumentsRaw, resultRaw: event.resultRaw,
      status: event.phase === "result" ? toolResultStatus(event.resultRaw, row.status)
        : row.status?.toLowerCase() === "error" ? "error"
          : row.status?.toLowerCase() === "aborted" ? "stopped" : "running",
    });
  }
  return entries.flatMap<ConversationEntry>((entry) => {
    if (entry.kind === "user") return [entry];
    const parts = entry.parts.map((part) => part.kind === "tool" && part.status === "running"
      ? { ...part, status: "stopped" as const } : part);
    return parts.length ? [{ ...entry, parts }] : [];
  });
}

export async function listWorkbenchMessages(sessionId: string): Promise<ConversationEntry[]> {
  const rows: SessionMessage[] = [];
  let afterSeq = 0;
  while (true) {
    const page = await apiFetchWithAuth<SessionMessage[]>(
      `/sessions/${sessionId}/messages/after-seq?afterSeq=${afterSeq}&limit=500`,
      { requestPolicy: "query" },
    );
    if (!page?.length) break;
    rows.push(...page);
    if (page.length < 500) break;
    const lastSeq = page.at(-1)?.seq;
    if (typeof lastSeq !== "number" || lastSeq <= afterSeq) {
      throw new Error("历史消息分页未推进，请稍后重试");
    }
    afterSeq = lastSeq;
  }
  return historyEntries(rows);
}

export async function abortWorkbenchSession(sessionId: string): Promise<void> {
  await apiFetchWithAuth<boolean>(`/sessions/${sessionId}/abort`, {
    method: "POST",
    body: {},
    requestPolicy: "mutation",
  });
}
