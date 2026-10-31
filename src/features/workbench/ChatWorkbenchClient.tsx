"use client";

import { useCallback, useEffect, useRef, useState, type DragEvent, type FormEvent, type KeyboardEvent } from "react";
import Link from "next/link";
import { FileText, Menu, Paperclip, Send, Sparkles, Square, X } from "lucide-react";
import { createDocument, getDocument, listDocuments, updateDraft, type DocumentDTO } from "@/src/lib/api/documents";
import type { UserMessageClientContext } from "@/src/lib/types/all/userMessageClientContext";
import {
  listWorkbenchModels,
  MODEL_CREDENTIALS_CHANGED_EVENT,
  type AvailableWorkbenchModel,
} from "@/src/lib/api/modelProviders";
import { streamSendMessageRPC } from "@/src/lib/api/sessionStream";
import { StreamEventParser } from "@/src/lib/stream";
import { useAuthSnapshot } from "@/src/stores/auth";
import { appendAssistantText, appendAssistantToolEvent, parseWorkbenchToolEvent, settleAssistant, type WorkbenchToolEvent } from "./conversationEvents";
import { ConversationTimeline } from "./ConversationTimeline";
import type { ConversationEntry } from "./conversationTypes";
import { MarkdownDocumentPanel } from "./MarkdownDocumentPanel";
import {
  abortWorkbenchSession,
  createWorkbenchProject,
  createWorkbenchSession,
  deleteWorkbenchProject,
  listWorkbenchMessages,
  listWorkbenchFiles,
  listWorkbenchProjects,
  listWorkbenchSessions,
  renameWorkbenchProject,
  uploadWorkbenchFile,
  type WorkbenchFile,
  type WorkbenchProject,
  type WorkbenchSession,
} from "./workbenchApi";
import styles from "./workbench.module.css";
import { WorkbenchSidebar } from "./WorkbenchSidebar";
import { cacheWorkbenchFile } from "./workbenchFileCache";
import { deriveUserKey } from "@/src/stores/authUserKey";
import { readWorkspaceFileDrag, WORKSPACE_FILE_DRAG_TYPE } from "./workspaceFileDrag";

type ChatMode = "ASK" | "AGENT";

const STARTERS = [
  "帮我整理一篇文章的大纲",
  "总结这份材料的关键观点",
  "一起构思我的下一篇博客",
];

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}

function changedDocumentId(event: WorkbenchToolEvent): string | null {
  if (event.phase !== "result" || !["document_create", "document_save"].includes(event.name) || !event.resultRaw) return null;
  try {
    const result: unknown = JSON.parse(event.resultRaw);
    if (!result || typeof result !== "object" || Array.isArray(result)) return null;
    const record = result as Record<string, unknown>;
    if (record.success === false) return null;
    const id = record.document_id ?? record.documentId;
    return (typeof id === "number" && Number.isSafeInteger(id) && id > 0)
      || (typeof id === "string" && /^[1-9]\d*$/.test(id)) ? String(id) : null;
  } catch { return null; }
}

function setWorkspaceUrl(projectId: string | null, sessionId: string | null) {
  const url = new URL(window.location.href);
  url.searchParams.delete("sourceExampleId");
  if (projectId) url.searchParams.set("projectId", projectId);
  else url.searchParams.delete("projectId");
  if (sessionId) url.searchParams.set("sessionId", sessionId);
  else url.searchParams.delete("sessionId");
  window.history.replaceState(window.history.state, "", `${url.pathname}${url.search}${url.hash}`);
}

export default function ChatWorkbenchClient() {
  const auth = useAuthSnapshot();
  const [projects, setProjects] = useState<WorkbenchProject[]>([]);
  const [sessions, setSessions] = useState<WorkbenchSession[]>([]);
  const [projectId, setProjectId] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ConversationEntry[]>([]);
  const [draft, setDraft] = useState("");
  const [mode, setMode] = useState<ChatMode>("ASK");
  const [models, setModels] = useState<AvailableWorkbenchModel[]>([]);
  const [selectedModelId, setSelectedModelId] = useState("");
  const [modelsLoading, setModelsLoading] = useState(true);
  const [modelsError, setModelsError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [activity, setActivity] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [documentPanelOpen, setDocumentPanelOpen] = useState(false);
  const [documentPanelView, setDocumentPanelView] = useState<"files" | "preview" | "editor">("files");
  const [documents, setDocuments] = useState<DocumentDTO[]>([]);
  const [activeDocumentId, setActiveDocumentId] = useState<string | null>(null);
  const [activeDocument, setActiveDocument] = useState<DocumentDTO | null>(null);
  const [documentDraft, setDocumentDraft] = useState("");
  const [documentLoading, setDocumentLoading] = useState(false);
  const [documentSaving, setDocumentSaving] = useState(false);
  const [documentError, setDocumentError] = useState<string | null>(null);
  const [workspaceFiles, setWorkspaceFiles] = useState<WorkbenchFile[]>([]);
  const [filesLoading, setFilesLoading] = useState(false);
  const [fileUploading, setFileUploading] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const [attachedFiles, setAttachedFiles] = useState<WorkbenchFile[]>([]);
  const [attachedDocumentIds, setAttachedDocumentIds] = useState<string[]>([]);
  const [composerDragActive, setComposerDragActive] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const sidebarManuallySetRef = useRef(false);
  const modelsRequestRef = useRef(0);
  const messagesVersionRef = useRef(0);
  const documentListVersionRef = useRef(0);
  const documentDetailVersionRef = useRef(0);
  const filesVersionRef = useRef(0);
  const currentProjectIdRef = useRef<string | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const composerRef = useRef<HTMLTextAreaElement | null>(null);
  const composerUploadRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setDocumentPanelOpen(typeof window.matchMedia === "function"
      ? window.matchMedia("(min-width: 1101px)").matches : true);
  }, []);

  useEffect(() => {
    const breakpoint = typeof window.matchMedia === "function"
      ? window.matchMedia("(max-width: 1024px)") : null;
    let saved: string | null = null;
    try {
      saved = window.localStorage.getItem("agentworkbench:sidebar-collapsed");
    } catch { /* optional UI preference */ }
    if (saved !== null) {
      sidebarManuallySetRef.current = true;
      setSidebarCollapsed(saved === "true");
      return;
    }
    setSidebarCollapsed(breakpoint?.matches ?? false);
    const onBreakpointChange = (event: MediaQueryListEvent) => {
      if (!sidebarManuallySetRef.current) setSidebarCollapsed(event.matches);
    };
    breakpoint?.addEventListener("change", onBreakpointChange);
    return () => breakpoint?.removeEventListener("change", onBreakpointChange);
  }, []);

  const toggleSidebarCollapsed = () => {
    sidebarManuallySetRef.current = true;
    setSidebarCollapsed((previous) => {
      const next = !previous;
      try { window.localStorage.setItem("agentworkbench:sidebar-collapsed", String(next)); } catch { /* optional UI preference */ }
      return next;
    });
  };

  const refreshModels = useCallback(async () => {
    const requestId = ++modelsRequestRef.current;
    setModelsLoading(true);
    try {
      const next = await listWorkbenchModels();
      if (requestId !== modelsRequestRef.current) return;
      setModels(next);
      setSelectedModelId((previous) => next.some((model) => model.id === previous)
        ? previous
        : next.find((model) => model.askSupported)?.id ?? next[0]?.id ?? "");
      setModelsError(false);
    } catch {
      if (requestId !== modelsRequestRef.current) return;
      setModels([]);
      setModelsError(true);
    } finally {
      if (requestId === modelsRequestRef.current) setModelsLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshModels();
    window.addEventListener(MODEL_CREDENTIALS_CHANGED_EVENT, refreshModels);
    window.addEventListener("focus", refreshModels);
    return () => {
      modelsRequestRef.current += 1;
      window.removeEventListener(MODEL_CREDENTIALS_CHANGED_EVENT, refreshModels);
      window.removeEventListener("focus", refreshModels);
    };
  }, [refreshModels]);

  const refreshSessions = useCallback(async (id: string) => {
    const next = await listWorkbenchSessions(id);
    setSessions(next);
    return next;
  }, []);

  const refreshWorkspaceFiles = useCallback(async (id: string) => {
    const version = ++filesVersionRef.current;
    setFilesLoading(true);
    setFileError(null);
    try {
      const next = await listWorkbenchFiles(id);
      if (filesVersionRef.current === version) setWorkspaceFiles(next);
    } catch (cause) {
      if (filesVersionRef.current === version) setFileError(messageOf(cause));
    } finally {
      if (filesVersionRef.current === version) setFilesLoading(false);
    }
  }, []);

  useEffect(() => {
    filesVersionRef.current += 1;
    setWorkspaceFiles([]);
    setAttachedFiles([]);
    setAttachedDocumentIds([]);
    setFileError(null);
    if (projectId) void refreshWorkspaceFiles(projectId);
    return () => { filesVersionRef.current += 1; };
  }, [projectId, refreshWorkspaceFiles]);

  const loadDocument = useCallback(async (id: string) => {
    const version = ++documentDetailVersionRef.current;
    setDocumentLoading(true);
    setDocumentError(null);
    try {
      const next = await getDocument(id);
      if (documentDetailVersionRef.current !== version) return;
      setActiveDocument(next);
      setDocumentDraft(next.draftContent ?? "");
    } catch (cause) {
      if (documentDetailVersionRef.current === version) setDocumentError(messageOf(cause));
    } finally {
      if (documentDetailVersionRef.current === version) setDocumentLoading(false);
    }
  }, []);

  useEffect(() => {
    currentProjectIdRef.current = projectId;
    const version = ++documentListVersionRef.current;
    setDocuments([]);
    setActiveDocumentId(null);
    setActiveDocument(null);
    setDocumentDraft("");
    setDocumentError(null);
    if (!projectId) return;
    setDocumentLoading(true);
    void listDocuments(projectId, { limit: 100 })
      .then((next) => {
        if (documentListVersionRef.current !== version) return;
        setDocuments(next);
        const first = next.find((document) => document.id != null);
        if (first) setActiveDocumentId(String(first.id));
      })
      .catch((cause) => {
        if (documentListVersionRef.current === version) setDocumentError(messageOf(cause));
      })
      .finally(() => {
        if (documentListVersionRef.current === version) setDocumentLoading(false);
      });
    return () => { documentListVersionRef.current += 1; };
  }, [projectId]);

  useEffect(() => {
    if (!activeDocumentId) return;
    void loadDocument(activeDocumentId);
    return () => { documentDetailVersionRef.current += 1; };
  }, [activeDocumentId, loadDocument]);

  const refreshDocuments = async (id: string, preferredId: string | null) => {
    const next = await listDocuments(id, { limit: 100 });
    if (currentProjectIdRef.current !== id) return;
    setDocuments(next);
    const selected = next.find((document) => document.id != null && String(document.id) === preferredId)
      ?? next.find((document) => document.id != null);
    const selectedId = selected?.id == null ? null : String(selected.id);
    if (selectedId === activeDocumentId && selectedId) await loadDocument(selectedId);
    else setActiveDocumentId(selectedId);
  };

  const uploadFile = async (file: File) => {
    if (sending || documentSaving || fileUploading || loading) return;
    if (file.size > 50 * 1024 * 1024) {
      const cause = new Error("文件不能超过 50 MB");
      setFileError(cause.message);
      setError(cause.message);
      throw cause;
    }
    setFileUploading(true);
    setFileError(null);
    setError(null);
    try {
      let targetProjectId = projectId;
      if (!targetProjectId) {
        const createdProject = await createWorkbenchProject();
        targetProjectId = String(createdProject.id);
        setProjects((previous) => [createdProject, ...previous]);
        setProjectId(targetProjectId);
        currentProjectIdRef.current = targetProjectId;
        setWorkspaceUrl(targetProjectId, null);
      }
      const uploaded = await uploadWorkbenchFile(targetProjectId, file);
      const stored = uploaded?.files?.[0];
      if (stored?.sourceFileId && stored.cacheKey) {
        void cacheWorkbenchFile(deriveUserKey(auth.rawUser), targetProjectId,
          { id: String(stored.sourceFileId), cacheKey: stored.cacheKey }, file);
      }
      await Promise.all([refreshWorkspaceFiles(targetProjectId), refreshDocuments(targetProjectId, activeDocumentId)]);
      setDocumentPanelOpen(true);
      setDocumentPanelView("files");
    } catch (cause) {
      setFileError(messageOf(cause));
      setError(messageOf(cause));
      throw cause;
    } finally {
      setFileUploading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    const bootstrap = async () => {
      try {
        const nextProjects = await listWorkbenchProjects();
        if (cancelled) return;
        setProjects(nextProjects);
        const params = new URLSearchParams(window.location.search);
        const requestedProject = params.get("projectId");
        const chosen = nextProjects.find((project) => String(project.id) === requestedProject) ?? nextProjects[0];
        if (!chosen) return;
        const id = String(chosen.id);
        setProjectId(id);
        const nextSessions = await listWorkbenchSessions(id);
        if (cancelled) return;
        setSessions(nextSessions);
        const requestedSession = params.get("sessionId");
        const selected = nextSessions.find((session) => String(session.id) === requestedSession);
        if (selected) setSessionId(String(selected.id));
      } catch (cause) {
        if (!cancelled) setError(messageOf(cause));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void bootstrap();
    return () => {
      cancelled = true;
      abortRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    const version = ++messagesVersionRef.current;
    if (sending) {
      // Creating a session must not let an empty history response replace the
      // optimistic user turn while its stream is already in progress.
      setHistoryLoading(false);
      return;
    }
    if (!sessionId) {
      setMessages([]);
      setHistoryLoading(false);
      return;
    }
    setHistoryLoading(true);
    void listWorkbenchMessages(sessionId)
      .then((next) => {
        if (messagesVersionRef.current === version) setMessages(next);
      })
      .catch((cause) => {
        if (messagesVersionRef.current === version) setError(messageOf(cause));
      })
      .finally(() => {
        if (messagesVersionRef.current === version) setHistoryLoading(false);
      });
  }, [sessionId, sending]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: sending ? "instant" : "smooth", block: "end" });
  }, [messages, sending]);

  const documentDirty = Boolean(activeDocument && documentDraft !== (activeDocument.draftContent ?? ""));

  const saveDocument = async (): Promise<DocumentDTO | null> => {
    if (!activeDocument?.id) return null;
    if (!documentDirty) return activeDocument;
    if (activeDocument.version == null) {
      const cause = new Error("文档版本未知，请刷新后重试");
      setDocumentError(cause.message);
      throw cause;
    }
    setDocumentSaving(true);
    setDocumentError(null);
    try {
      const saved = await updateDraft(activeDocument.id, {
        version: activeDocument.version,
        draftContent: documentDraft,
      });
      setActiveDocument(saved);
      setDocumentDraft(saved.draftContent ?? "");
      setDocuments((previous) => previous.map((document) => String(document.id) === String(saved.id) ? saved : document));
      return saved;
    } catch (cause) {
      setDocumentError(messageOf(cause));
      throw cause;
    } finally {
      setDocumentSaving(false);
    }
  };

  const createMarkdownDocument = async (title: string): Promise<void> => {
    if (sending || documentSaving) return;
    if (documentDirty) {
      const cause = new Error("请先保存当前文档，再创建新文档");
      setDocumentError(cause.message);
      throw cause;
    }
    setDocumentSaving(true);
    setDocumentError(null);
    let createdProject: WorkbenchProject | null = null;
    try {
      if (!projectId) createdProject = await createWorkbenchProject();
      const targetProjectId = projectId ?? String(createdProject?.id);
      const created = await createDocument({ projectId: targetProjectId, title, draftContent: `# ${title}\n\n` });
      if (createdProject) {
        setProjects((previous) => [createdProject!, ...previous]);
        setProjectId(targetProjectId);
      } else {
        setDocuments((previous) => [created, ...previous]);
        setActiveDocumentId(String(created.id));
      }
      setDocumentPanelOpen(true);
      setDocumentPanelView("editor");
    } catch (cause) {
      if (createdProject) {
        setProjects((previous) => [createdProject!, ...previous]);
        setProjectId(String(createdProject.id));
      }
      setDocumentError(messageOf(cause));
      throw cause;
    } finally {
      setDocumentSaving(false);
    }
  };

  const selectDocument = (id: string) => {
    if (sending || documentSaving || fileUploading) return false;
    if (id === activeDocumentId) return true;
    if (documentDirty) {
      setDocumentError("请先保存当前文档，再切换文档");
      return false;
    }
    setActiveDocumentId(id);
    return true;
  };

  const attachFile = (file: WorkbenchFile) => {
    if (sending || fileUploading || documentSaving) return;
    if (attachedFiles.some((attached) => attached.id === file.id)) {
      composerRef.current?.focus();
      return;
    }
    if (attachedFiles.length + attachedDocumentIds.length >= 5) {
      setError("每条消息最多引用 5 个文件");
      return;
    }
    setAttachedFiles((previous) => [...previous, file]);
    setError(null);
    composerRef.current?.focus();
  };

  const onComposerDragOver = (event: DragEvent<HTMLFormElement>) => {
    if (sending || fileUploading || documentSaving) return;
    if (!Array.from(event.dataTransfer.types).includes(WORKSPACE_FILE_DRAG_TYPE)) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
    setComposerDragActive(true);
  };

  const onComposerDrop = (event: DragEvent<HTMLFormElement>) => {
    if (sending || fileUploading || documentSaving) return;
    const reference = readWorkspaceFileDrag(event.dataTransfer);
    if (!reference) return;
    event.preventDefault();
    setComposerDragActive(false);
    if (!projectId || reference.projectId !== projectId) {
      setError("只能引用当前工作区的文件");
      return;
    }
    if (reference.kind === "document") {
      if (!documents.some((document) => String(document.id) === reference.documentId)) {
        setError("文档已不在当前工作区，请刷新文件列表");
        return;
      }
      if (attachedDocumentIds.includes(reference.documentId)) {
        composerRef.current?.focus();
        return;
      }
      if (attachedFiles.length + attachedDocumentIds.length >= 5) {
        setError("每条消息最多引用 5 个文件");
        return;
      }
      setAttachedDocumentIds((previous) => [...previous, reference.documentId]);
      setMode("AGENT");
      setError(null);
    } else {
      const file = workspaceFiles.find((item) => item.id === reference.fileId && item.cacheKey === reference.cacheKey);
      if (!file) {
        setError("文件已不在当前工作区，请刷新文件列表");
        return;
      }
      attachFile(file);
    }
    composerRef.current?.focus();
  };

  const selectProject = async (id: string) => {
    if (sending || documentSaving || fileUploading || id === projectId) return;
    if (documentDirty) {
      setDocumentPanelOpen(true);
      setDocumentError("请先保存当前文档，再切换工作区");
      return;
    }
    setError(null);
    setProjectId(id);
    setDocumentPanelView("files");
    setSessionId(null);
    setMessages([]);
    setSessions([]);
    setWorkspaceUrl(id, null);
    setSidebarOpen(false);
    try {
      await refreshSessions(id);
    } catch (cause) {
      setError(messageOf(cause));
    }
  };

  const createWorkspace = async (name: string) => {
    if (sending || documentSaving || fileUploading) throw new Error("请等待当前任务完成");
    if (documentDirty) {
      setDocumentPanelOpen(true);
      throw new Error("请先保存当前文档，再创建工作区");
    }
    const created = await createWorkbenchProject(name);
    setProjects((previous) => [created, ...previous]);
    await selectProject(String(created.id));
  };

  const renameWorkspace = async (id: string, name: string) => {
    if (sending || documentSaving || fileUploading) throw new Error("请等待当前任务完成");
    const updated = await renameWorkbenchProject(id, name);
    setProjects((previous) => previous.map((project) => String(project.id) === id ? updated : project));
  };

  const deleteWorkspace = async (id: string) => {
    if (sending || documentSaving || fileUploading) throw new Error("请等待当前任务完成");
    if (id === projectId && documentDirty) {
      setDocumentPanelOpen(true);
      throw new Error("请先保存当前文档，再删除工作区");
    }
    await deleteWorkbenchProject(id);
    setProjects((previous) => previous.filter((project) => String(project.id) !== id));
    if (id !== projectId) return;
    const fallbackId = projects.find((project) => String(project.id) !== id)?.id;
    const nextProjectId = fallbackId == null ? null : String(fallbackId);
    setProjectId(nextProjectId);
    setDocumentPanelView("files");
    setSessionId(null);
    setSessions([]);
    setMessages([]);
    setDraft("");
    setWorkspaceUrl(nextProjectId, null);
    if (nextProjectId) {
      try { await refreshSessions(nextProjectId); }
      catch (cause) { setError(messageOf(cause)); }
    }
  };

  const selectSession = (id: string) => {
    if (sending || fileUploading) return;
    setError(null);
    setSessionId(id);
    setAttachedFiles([]);
    setAttachedDocumentIds([]);
    setWorkspaceUrl(projectId, id);
    setSidebarOpen(false);
  };

  const newConversation = () => {
    if (sending || fileUploading) return;
    setError(null);
    setSessionId(null);
    setMessages([]);
    setDraft("");
    setAttachedFiles([]);
    setAttachedDocumentIds([]);
    setWorkspaceUrl(projectId, null);
    setSidebarOpen(false);
  };

  const newConversationInProject = async (id: string) => {
    if (sending || documentSaving || fileUploading) return;
    if (id === projectId) {
      newConversation();
      return;
    }
    await selectProject(id);
    if (!documentDirty) setDraft("");
  };

  const send = async (event?: FormEvent) => {
    event?.preventDefault();
    const text = draft.trim();
    const selectedModel = models.find((model) => model.id === selectedModelId
      && (mode === "ASK" ? model.askSupported : model.agentSupported))
      ?? models.find((model) => mode === "ASK" ? model.askSupported : model.agentSupported);
    if (!text || !selectedModel || sending || loading || modelsLoading || documentSaving || fileUploading
      || (activeDocumentId !== null && documentLoading)) return;
    setSending(true);
    setError(null);
    setActivity("正在准备对话…");

    let currentProjectId = projectId;
    let currentSessionId = sessionId;
    let workingDocument = activeDocument;
    let touchedDocumentId: string | null = null;
    let streamStarted = false;
    const optimisticId = `pending-${Date.now()}`;
    try {
      if (documentDirty) workingDocument = await saveDocument();
      if (!currentProjectId) {
        const createdProject = await createWorkbenchProject();
        currentProjectId = String(createdProject.id);
        setProjects((previous) => [createdProject, ...previous]);
        setProjectId(currentProjectId);
        currentProjectIdRef.current = currentProjectId;
      }
      if (!currentSessionId) {
        const createdSession = await createWorkbenchSession(currentProjectId, text);
        currentSessionId = String(createdSession.id);
        setSessions((previous) => [createdSession, ...previous]);
        setSessionId(currentSessionId);
      }
      setWorkspaceUrl(currentProjectId, currentSessionId);
      setDraft("");
      setMessages((previous) => [
        ...previous,
        { id: `${optimisticId}-user`, kind: "user", text },
        { id: `${optimisticId}-assistant`, kind: "assistant", parts: [], pending: true },
      ]);
      setActivity(mode === "AGENT" ? "Agent 正在处理任务…" : "正在生成回复…");
      const parser = new StreamEventParser();
      const controller = new AbortController();
      abortRef.current = controller;
      streamStarted = true;
      const contextDocument = attachedDocumentIds.length > 0
        ? documents.find((document) => String(document.id) === attachedDocumentIds[0])
        : workingDocument;
      const clientContext: UserMessageClientContext = {
        ...(contextDocument?.id != null ? { activeDocument: {
          documentId: String(contextDocument.id), type: "markdown",
          fileName: contextDocument.title?.trim() || undefined,
        } } : {}),
        ...(attachedDocumentIds.length > 0 ? { referencedDocumentIds: attachedDocumentIds } : {}),
      };
      await streamSendMessageRPC(currentSessionId, {
        message: text, mode, model: selectedModel.id,
        ...(attachedFiles.some((file) => !file.image)
          ? { fileCacheKeys: attachedFiles.filter((file) => !file.image).map((file) => file.cacheKey) } : {}),
        ...(attachedFiles.some((file) => file.image)
          ? { imageCacheKeys: attachedFiles.filter((file) => file.image).map((file) => file.cacheKey) } : {}),
        ...(clientContext.activeDocument || clientContext.referencedDocumentIds ? { clientContext } : {}),
      }, {
        onMessage: (chunk) => {
          const frame = parser.parse(chunk);
          if (frame.type === "textDelta" && frame.payload) {
            setMessages((previous) => appendAssistantText(previous, `${optimisticId}-assistant`, frame.payload));
            setActivity("");
          } else if (frame.type === "toolJSON") {
            const toolEvent = parseWorkbenchToolEvent(frame.payload);
            if (toolEvent) {
              touchedDocumentId = changedDocumentId(toolEvent) ?? touchedDocumentId;
              setMessages((previous) => appendAssistantToolEvent(previous, `${optimisticId}-assistant`, toolEvent));
              setActivity(toolEvent.phase === "call" ? "Agent 正在使用工具…" : "Agent 正在继续处理…");
            }
          } else if (frame.type === "error") {
            throw new Error(frame.error.message);
          }
        },
      }, controller.signal);
      parser.clear();
      const persisted = await listWorkbenchMessages(currentSessionId);
      setMessages(persisted);
      await refreshSessions(currentProjectId);
      setAttachedFiles([]);
      setAttachedDocumentIds([]);
    } catch (cause) {
      if (!streamStarted) setDraft(text);
      if (!(cause instanceof Error && cause.name === "AbortError")) {
        setError(messageOf(cause));
      }
      if (currentSessionId) {
        try {
          const persisted = await listWorkbenchMessages(currentSessionId);
          if (persisted.length) setMessages(persisted);
        } catch {
          // Preserve optimistic content when history cannot be reloaded.
        }
      }
    } finally {
      abortRef.current = null;
      if (streamStarted && currentProjectId && mode === "AGENT") {
        try {
          await refreshDocuments(currentProjectId, touchedDocumentId ?? (workingDocument?.id == null ? null : String(workingDocument.id)));
          await refreshWorkspaceFiles(currentProjectId);
          if (touchedDocumentId) { setDocumentPanelOpen(true); setDocumentPanelView("editor"); }
        } catch (cause) {
          setDocumentError(messageOf(cause));
        }
      }
      setMessages((previous) => settleAssistant(previous, `${optimisticId}-assistant`));
      setSending(false);
      setActivity("");
    }
  };

  const stop = async () => {
    if (!sending || !sessionId) return;
    try {
      await abortWorkbenchSession(sessionId);
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      abortRef.current?.abort();
    }
  };

  const onComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      void send();
    }
  };

  const currentProject = projects.find((project) => String(project.id) === projectId);
  const currentSession = sessions.find((session) => String(session.id) === sessionId);
  const account = auth.rawUser as { name?: string; email?: string } | null;
  const selectableModels = models.filter((model) => mode === "ASK" ? model.askSupported : model.agentSupported);
  const selectedModel = selectableModels.find((model) => model.id === selectedModelId) ?? selectableModels[0];

  return (
    <div className={styles.shell}>
      <WorkbenchSidebar
        projects={projects}
        sessions={sessions}
        activeProjectId={projectId}
        activeSessionId={sessionId}
        accountName={account?.name || account?.email || "账户"}
        collapsed={sidebarCollapsed}
        mobileOpen={sidebarOpen}
        busy={sending || documentSaving || fileUploading}
        onToggleCollapsed={toggleSidebarCollapsed}
        onCloseMobile={() => setSidebarOpen(false)}
        onNewConversation={newConversation}
        onCreateWorkspace={createWorkspace}
        onRenameWorkspace={renameWorkspace}
        onDeleteWorkspace={deleteWorkspace}
        onNewConversationInProject={(id) => void newConversationInProject(id)}
        onSelectProject={(id) => void selectProject(id)}
        onSelectSession={selectSession}
      />

      <main className={styles.main}>
        <header className={styles.topbar}>
          <button type="button" className={styles.menuButton} aria-label="打开导航" onClick={() => setSidebarOpen(true)}><Menu size={20} /></button>
          <div className={styles.breadcrumb}>{currentProject?.name || "工作区"}<span>/</span>{currentSession?.title || "新对话"}</div>
          <span className={styles.topbarLabel}>AI 文档工作台</span>
          <button type="button" className={styles.documentToggle} onClick={() => setDocumentPanelOpen((previous) => !previous)}
            aria-label={documentPanelOpen ? "关闭文件面板" : "打开文件面板"}
            aria-expanded={documentPanelOpen} title={documentPanelOpen ? "关闭文件面板" : "打开文件面板"}>
            <FileText size={16} /><span>文件</span>
          </button>
        </header>

        <div className={styles.conversation}>
          {loading ? <div className={styles.centerNote}>正在载入工作区…</div> :
            messages.length === 0 && !historyLoading ? (
              <div className={styles.welcome}>
                <div className={styles.welcomeIcon}><Sparkles size={27} /></div>
                <p className={styles.eyebrow}>AGENTWORKBENCH</p>
                <h1>从想法开始，一起完成作品。</h1>
                <p className={styles.welcomeLead}>聊聊你的目标、整理材料，或让 Agent 协助起草和修改文档。</p>
                <div className={styles.starters}>
                  {STARTERS.map((starter) => <button key={starter} type="button" onClick={() => setDraft(starter)}>{starter}<span>↗</span></button>)}
                </div>
              </div>
            ) : (
              <ConversationTimeline entries={messages}
                activity={activity} historyLoading={historyLoading} bottomRef={bottomRef} />
            )}
        </div>

        <div className={styles.composerDock}>
          {error && <div className={styles.error} role="alert">{error}<button type="button" onClick={() => setError(null)} aria-label="关闭错误"><X size={14} /></button></div>}
          {activeDocument && (
            <div className={styles.documentContext}>
              <FileText size={14} aria-hidden="true" />
              <span>编辑器当前文档：{activeDocument.title?.trim() || `文档 ${activeDocument.id}`}</span>
              <button type="button" onClick={() => { setDocumentPanelOpen(true); setDocumentPanelView("editor"); }}>查看文档</button>
            </div>
          )}
          {!modelsLoading && (modelsError || selectableModels.length === 0) && (
            <div className={styles.modelNotice} role="status">
              <span>{modelsError ? "暂时无法读取模型列表。" : mode === "AGENT" && models.length > 0
                ? "当前没有可用于 Agent 任务的模型，请切换到快速问答或配置其他提供商。"
                : "还没有可用模型，请先添加模型提供商 API Key。"}</span>
              {modelsError && <button type="button" onClick={() => void refreshModels()}>重试</button>}
              <Link href="/app/settings/models">管理密钥</Link>
            </div>
          )}
          {activity && <div className={styles.activity}><span className={styles.activityDot} />{activity}</div>}
          <form className={`${styles.composer} ${composerDragActive ? styles.composerDragActive : ""}`}
            onSubmit={(event) => void send(event)} onDragOver={onComposerDragOver}
            onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setComposerDragActive(false); }}
            onDrop={onComposerDrop}>
            {(attachedDocumentIds.length > 0 || attachedFiles.length > 0) &&
              <div className={styles.attachedFiles} aria-label="本轮引用的文件">
                {attachedDocumentIds.map((id) => {
                  const document = documents.find((item) => String(item.id) === id);
                  const name = document?.title?.trim() || `文档 ${id}`;
                  const displayName = name.toLowerCase().endsWith(".md") ? name : `${name}.md`;
                  return <span key={`document-${id}`} className={styles.attachedFile}>
                    <FileText size={13} aria-hidden="true" /><span>{displayName}</span>
                    <button type="button" aria-label={`移除文档 ${displayName}`} disabled={sending}
                      onClick={() => setAttachedDocumentIds((previous) => previous.filter((item) => item !== id))}><X size={13} /></button>
                  </span>;
                })}
                {attachedFiles.map((file) => <span key={`file-${file.id}`} className={styles.attachedFile}>
                  <Paperclip size={13} aria-hidden="true" /><span>{file.name}</span>
                  <button type="button" aria-label={`移除文件 ${file.name}`} disabled={sending}
                    onClick={() => setAttachedFiles((previous) => previous.filter((item) => item.id !== file.id))}><X size={13} /></button>
                </span>)}
              </div>}
            <textarea ref={composerRef} value={draft} onChange={(event) => setDraft(event.target.value)} onKeyDown={onComposerKeyDown}
              placeholder="描述你的想法，或交给 Agent 一个任务…" aria-label="消息内容" rows={3} disabled={loading || sending || fileUploading} />
            <input ref={composerUploadRef} className={styles.hiddenUploadInput} type="file" aria-label="选择要上传到工作区的文件"
              accept=".md,.txt,.json,.pdf,.docx,.png,.jpg,.jpeg,.webp,.gif"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) void uploadFile(file).catch(() => undefined);
                event.target.value = "";
              }} />
            <div className={styles.composerToolbar}>
              <div className={styles.composerOptions}>
                <button type="button" className={styles.uploadButton} aria-label="从电脑上传文件" title="上传文件到工作区"
                  onClick={() => composerUploadRef.current?.click()} disabled={loading || sending || documentSaving || fileUploading}>
                  <Paperclip size={15} /><span>{fileUploading ? "上传中…" : "上传文件"}</span>
                </button>
                <div className={styles.modeGroup} role="group" aria-label="对话模式">
                  <button type="button" className={mode === "ASK" ? styles.modeActive : ""} onClick={() => setMode("ASK")} disabled={sending}>快速问答</button>
                  <button type="button" className={mode === "AGENT" ? styles.modeActive : ""} onClick={() => setMode("AGENT")} disabled={sending}>Agent 任务</button>
                </div>
                <select className={styles.modelSelect} aria-label="对话模型" value={selectedModel?.id ?? ""}
                  onChange={(event) => setSelectedModelId(event.target.value)} disabled={sending || modelsLoading || selectableModels.length === 0}>
                  {selectableModels.length === 0 ? <option value="">{modelsLoading ? "载入模型…" : "无可用模型"}</option> :
                    selectableModels.map((model) => <option key={model.id} value={model.id}>{model.displayName}</option>)}
                </select>
              </div>
              <button type={sending ? "button" : "submit"} className={styles.sendButton}
                onClick={sending ? () => void stop() : undefined} disabled={loading || (!sending && (!draft.trim() || !selectedModel || modelsLoading || documentSaving || fileUploading || (activeDocumentId !== null && documentLoading)))}
                aria-label={sending ? "停止回复" : "发送消息"} title={sending ? "停止回复" : "发送消息"}>
                {sending ? <Square size={16} fill="currentColor" /> : <Send size={17} />}
              </button>
            </div>
          </form>
          <p className={styles.composerHint}>Agent 可能出错，请检查重要内容。按 Enter 发送，Shift + Enter 换行。</p>
        </div>
      </main>
      <MarkdownDocumentPanel key={projectId ?? "new"} open={documentPanelOpen} projectId={projectId} projectName={currentProject?.name ?? null}
        userKey={deriveUserKey(auth.rawUser)}
        view={documentPanelView} onViewChange={setDocumentPanelView}
        documents={documents} files={workspaceFiles} filesLoading={filesLoading} fileUploading={fileUploading} fileError={fileError}
        activeDocument={activeDocument} activeDocumentId={activeDocumentId} draft={documentDraft} loading={documentLoading}
        busy={sending || documentSaving || loading || fileUploading} error={documentError}
        onClose={() => setDocumentPanelOpen(false)} onCreate={createMarkdownDocument}
        onUploadFile={uploadFile} onRefreshFiles={() => { if (projectId) void refreshWorkspaceFiles(projectId); }}
        onSelect={selectDocument} onChange={setDocumentDraft}
        onSave={() => saveDocument().catch(() => null)}
        onRefresh={() => { if (activeDocumentId) void loadDocument(activeDocumentId); }} />
      {sidebarOpen && <button type="button" className={styles.backdrop} aria-label="关闭导航" onClick={() => setSidebarOpen(false)} />}
    </div>
  );
}
