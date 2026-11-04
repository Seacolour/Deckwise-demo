// @vitest-environment jsdom

import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  listModels: vi.fn(),
  listProjects: vi.fn(),
  createProject: vi.fn(),
  listSessions: vi.fn(),
  createSession: vi.fn(),
  listMessages: vi.fn(),
  abort: vi.fn(),
  stream: vi.fn(),
  listDocuments: vi.fn(),
  listFiles: vi.fn(),
  uploadFile: vi.fn(),
  getFilePreviewUrl: vi.fn(),
  loadPreviewBlob: vi.fn(),
  cacheFile: vi.fn(),
  getDocument: vi.fn(),
  createDocument: vi.fn(),
  updateDraft: vi.fn(),
}));

vi.mock("@/src/lib/api/modelProviders", () => ({
  listWorkbenchModels: mocks.listModels,
  MODEL_CREDENTIALS_CHANGED_EVENT: "agentworkbench:model-credentials-changed",
}));
vi.mock("@/src/features/workbench/workbenchApi", () => ({
  listWorkbenchProjects: mocks.listProjects,
  createWorkbenchProject: mocks.createProject,
  listWorkbenchSessions: mocks.listSessions,
  createWorkbenchSession: mocks.createSession,
  listWorkbenchMessages: mocks.listMessages,
  listWorkbenchFiles: mocks.listFiles,
  uploadWorkbenchFile: mocks.uploadFile,
  getWorkbenchFilePreviewUrl: mocks.getFilePreviewUrl,
  getWorkbenchInlinePreviewPath: (projectId: string, file: { id: string; name: string }) =>
    `/api/workbench/files/${file.id}/preview?projectId=${projectId}&kind=${file.name.split(".").at(-1)}`,
  abortWorkbenchSession: mocks.abort,
}));
vi.mock("@/src/features/workbench/workbenchFileCache", () => ({
  loadWorkbenchPreviewFile: mocks.loadPreviewBlob,
  cacheWorkbenchFile: mocks.cacheFile,
}));
vi.mock("@/src/lib/api/sessionStream", () => ({ streamSendMessageRPC: mocks.stream }));
vi.mock("@/src/lib/api/documents", () => ({
  listDocuments: mocks.listDocuments,
  getDocument: mocks.getDocument,
  createDocument: mocks.createDocument,
  updateDraft: mocks.updateDraft,
}));
vi.mock("@/src/features/workbench/WorkbenchMarkdown", () => ({
  WorkbenchMarkdown: ({ text }: { text: string }) => <p>{text}</p>,
}));
vi.mock("@/src/stores/auth", () => ({
  useAuthSnapshot: () => ({ rawUser: { email: "demo@example.com", name: "Demo" } }),
}));

import ChatWorkbenchClient from "@/src/features/workbench/ChatWorkbenchClient";

function dragTransfer(): DataTransfer {
  const values = new Map<string, string>();
  return {
    effectAllowed: "all",
    dropEffect: "none",
    get types() { return [...values.keys()]; },
    setData(type: string, value: string) { values.set(type, value); },
    getData(type: string) { return values.get(type) ?? ""; },
  } as unknown as DataTransfer;
}

describe("workbench model selection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, "", "/app");
    Element.prototype.scrollIntoView = vi.fn();
    mocks.listProjects.mockResolvedValue([]);
    mocks.createProject.mockResolvedValue({ id: 7, name: "我的工作区" });
    mocks.listSessions.mockResolvedValue([]);
    mocks.createSession.mockResolvedValue({ id: 9, projectId: 7, title: "测试" });
    mocks.listMessages.mockResolvedValue([]);
    mocks.stream.mockResolvedValue(undefined);
    mocks.listDocuments.mockResolvedValue([]);
    mocks.listFiles.mockResolvedValue([]);
    mocks.uploadFile.mockResolvedValue(undefined);
    mocks.getFilePreviewUrl.mockResolvedValue("https://example.com/preview.txt");
    mocks.loadPreviewBlob.mockResolvedValue(new Blob(["测试"], { type: "text/plain" }));
    URL.createObjectURL = vi.fn(() => "blob:preview-test");
    URL.revokeObjectURL = vi.fn();
  });

  it("sends the selected canonical model ID in an ASK message", async () => {
    mocks.listModels.mockResolvedValue([
      { id: "deepseek/deepseek-chat", providerId: "deepseek", displayName: "DeepSeek Chat", askSupported: true, agentSupported: false },
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    const modelSelect = await screen.findByRole("combobox", { name: "对话模型" });
    await waitFor(() => expect(modelSelect).toBeEnabled());
    fireEvent.change(modelSelect, { target: { value: "openai/gpt-4.1" } });
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "你好" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));

    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ message: "你好", mode: "ASK", model: "openai/gpt-4.1" }),
      expect.any(Object), expect.any(AbortSignal),
    ));
  });

  it("shows setup guidance and blocks sending when no model is configured", async () => {
    mocks.listModels.mockResolvedValue([]);
    render(<ChatWorkbenchClient />);
    expect(await screen.findByText(/还没有可用模型/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "管理密钥" })).toHaveAttribute("href", "/app/settings/models");
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "你好" } });
    expect(screen.getByRole("button", { name: "发送消息" })).toBeDisabled();
    expect(mocks.stream).not.toHaveBeenCalled();
  });

  it("keeps ASK-only models out of Agent mode", async () => {
    mocks.listModels.mockResolvedValue([
      { id: "deepseek/deepseek-chat", providerId: "deepseek", displayName: "DeepSeek Chat", askSupported: true, agentSupported: false },
    ]);
    render(<ChatWorkbenchClient />);
    await waitFor(() => expect(screen.getByRole("combobox", { name: "对话模型" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Agent 任务" }));
    expect(screen.getByText(/当前没有可用于 Agent 任务的模型/)).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "对话模型" })).toBeDisabled();
  });

  it("offers DeepSeek in Agent mode when the backend advertises tool support", async () => {
    mocks.listModels.mockResolvedValue([
      { id: "deepseek/deepseek-flash", providerId: "deepseek", displayName: "DeepSeek Flash", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);
    await waitFor(() => expect(screen.getByRole("combobox", { name: "对话模型" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Agent 任务" }));
    expect(screen.getByRole("combobox", { name: "对话模型" })).toHaveValue("deepseek/deepseek-flash");
  });

  it("renders streamed Agent tool steps in the conversation without replacing the reply", async () => {
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    let onMessage: ((chunk: string) => void) | undefined;
    let finishStream: (() => void) | undefined;
    const streaming = new Promise<void>((resolve) => { finishStream = resolve; });
    mocks.stream.mockImplementation((_sessionId, _payload, callbacks: { onMessage: (chunk: string) => void }) => {
      onMessage = callbacks.onMessage;
      return streaming;
    });
    render(<ChatWorkbenchClient />);
    await waitFor(() => expect(screen.getByRole("combobox", { name: "对话模型" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Agent 任务" }));
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "整理材料" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(onMessage).toBeDefined());

    await act(async () => {
      onMessage?.(JSON.stringify({ type: "text", payload: "我先查看文件。" }));
      onMessage?.(JSON.stringify({ type: "tool", payload: {
        phase: "call", call_id: "c1", name: "file_list", arguments_json: '{"secret":"sk-test-value"}',
      } }));
    });
    expect(screen.getByText("我先查看文件。")).toBeInTheDocument();
    expect(screen.getByText("file_list")).toBeInTheDocument();
    expect(screen.getByText("执行中")).toBeInTheDocument();
    expect(screen.queryByText("sk-test-value")).not.toBeInTheDocument();

    await act(async () => {
      onMessage?.(JSON.stringify({ type: "tool", payload: {
        phase: "result", call_id: "c1", name: "file_list", result_json: '{"files":[]}',
      } }));
    });
    expect(screen.getByText("已完成")).toBeInTheDocument();
    await act(async () => { finishStream?.(); });
  });

  it("creates and opens a Markdown document before the first conversation", async () => {
    const document = { id: "51", projectId: "7", title: "第一篇文章", documentType: "MARKDOWN", draftContent: "# 第一篇文章\n\n", version: 1 };
    mocks.listModels.mockResolvedValue([]);
    mocks.createDocument.mockResolvedValue(document);
    mocks.listDocuments.mockResolvedValue([document]);
    mocks.getDocument.mockResolvedValue(document);
    render(<ChatWorkbenchClient />);

    await waitFor(() => expect(screen.getByRole("button", { name: "新建 Markdown 文档" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "新建 Markdown 文档" }));
    fireEvent.change(screen.getByRole("textbox", { name: "新文档标题" }), { target: { value: "第一篇文章" } });
    await waitFor(() => expect(screen.getByRole("button", { name: "创建文档" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "创建文档" }));

    await waitFor(() => expect(mocks.createDocument).toHaveBeenCalledWith({
      projectId: "7", title: "第一篇文章", draftContent: "# 第一篇文章\n\n",
    }));
    expect(await screen.findByRole("textbox", { name: "Markdown 内容" })).toHaveValue("# 第一篇文章\n\n");
  });

  it("saves a local draft before giving the active document to Agent and reloads its result", async () => {
    const initial = { id: "51", projectId: "7", title: "文章", documentType: "MARKDOWN", draftContent: "# 原文", version: 1 };
    const saved = { ...initial, draftContent: "# 用户修改", version: 2 };
    const agentEdited = { ...saved, draftContent: "# Agent 修改", version: 3 };
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    mocks.listDocuments.mockResolvedValue([initial]);
    mocks.getDocument.mockResolvedValueOnce(initial).mockResolvedValueOnce(agentEdited);
    mocks.updateDraft.mockResolvedValue(saved);
    render(<ChatWorkbenchClient />);

    fireEvent.click(await screen.findByRole("button", { name: "编辑 文章.md" }));
    const editor = await screen.findByRole("textbox", { name: "Markdown 内容" });
    fireEvent.change(editor, { target: { value: "# 用户修改" } });
    fireEvent.click(screen.getByRole("button", { name: "Agent 任务" }));
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "继续润色当前文档" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));

    await waitFor(() => expect(mocks.updateDraft).toHaveBeenCalledWith("51", { version: 1, draftContent: "# 用户修改" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ mode: "AGENT", clientContext: {
        activeDocument: { documentId: "51", type: "markdown", fileName: "文章" },
      } }), expect.any(Object), expect.any(AbortSignal),
    ));
    await waitFor(() => expect(screen.getByRole("textbox", { name: "Markdown 内容" })).toHaveValue("# Agent 修改"));
  });

  it("selects a Markdown file on click, previews on double click, and edits only from its action", async () => {
    const document = { id: "51", projectId: "7", title: "文章", documentType: "MARKDOWN", draftContent: "# 正文", version: 1 };
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listDocuments.mockResolvedValue([document]);
    mocks.getDocument.mockResolvedValue(document);
    mocks.listModels.mockResolvedValue([]);
    render(<ChatWorkbenchClient />);

    const row = await screen.findByRole("button", { name: "文件 文章.md" });
    fireEvent.click(row);
    expect(screen.queryByLabelText("本轮引用的文件")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Markdown 预览")).not.toBeInTheDocument();
    fireEvent.doubleClick(row);
    expect(await screen.findByLabelText("Markdown 预览")).toHaveTextContent("# 正文");
    expect(screen.queryByRole("textbox", { name: "Markdown 内容" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "编辑当前文档" }));
    expect(screen.getByRole("textbox", { name: "Markdown 内容" })).toHaveValue("# 正文");
    fireEvent.click(screen.getByRole("button", { name: "返回文件列表" }));
    expect(screen.getByRole("button", { name: "文件 文章.md" })).toBeInTheDocument();
    expect(screen.queryByLabelText("本轮引用的文件")).not.toBeInTheDocument();
  });

  it("previews a physical file without adding it to the message", async () => {
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listFiles.mockResolvedValue([{ id: "21", cacheKey: "a".repeat(64), name: "notes.txt", sizeBytes: 8, documentId: null, image: false }]);
    mocks.listModels.mockResolvedValue([]);
    render(<ChatWorkbenchClient />);

    const row = await screen.findByRole("button", { name: "文件 notes.txt" });
    fireEvent.doubleClick(row);
    await waitFor(() => expect(mocks.loadPreviewBlob).toHaveBeenCalledWith(
      "demo@example.com", "7", expect.objectContaining({ id: "21", name: "notes.txt" }), expect.any(AbortSignal),
    ));
    expect(await screen.findByLabelText("文本预览")).toHaveTextContent("测试");
    expect(screen.queryByLabelText("本轮引用的文件")).not.toBeInTheDocument();
  });

  it("keeps the message and local draft when saving before Agent fails", async () => {
    const document = { id: "51", projectId: "7", title: "文章", documentType: "MARKDOWN", draftContent: "# 原文", version: 1 };
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    mocks.listDocuments.mockResolvedValue([document]);
    mocks.getDocument.mockResolvedValue(document);
    mocks.updateDraft.mockRejectedValue(new Error("版本冲突"));
    render(<ChatWorkbenchClient />);

    fireEvent.click(await screen.findByRole("button", { name: "编辑 文章.md" }));
    fireEvent.change(await screen.findByRole("textbox", { name: "Markdown 内容" }), { target: { value: "# 本地修改" } });
    fireEvent.click(screen.getByRole("button", { name: "Agent 任务" }));
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "继续修改" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));

    await waitFor(() => expect(mocks.updateDraft).toHaveBeenCalled());
    await waitFor(() => expect(screen.getAllByText("版本冲突").length).toBeGreaterThan(0));
    expect(screen.getByRole("textbox", { name: "消息内容" })).toHaveValue("继续修改");
    expect(screen.getByRole("textbox", { name: "Markdown 内容" })).toHaveValue("# 本地修改");
    expect(mocks.stream).not.toHaveBeenCalled();
  });

  it("opens a document created by Agent after its task finishes", async () => {
    const created = { id: "81", projectId: "7", title: "Agent 初稿", documentType: "MARKDOWN", draftContent: "# Agent 初稿", version: 1 };
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    mocks.listDocuments.mockResolvedValueOnce([]).mockResolvedValueOnce([created]);
    mocks.getDocument.mockResolvedValue(created);
    mocks.stream.mockImplementation(async (_id, _payload, callbacks: { onMessage: (chunk: string) => void }) => {
      callbacks.onMessage(JSON.stringify({ type: "tool", payload: {
        phase: "result", name: "document_create", result_json: JSON.stringify({ success: true, document_id: 81 }),
      } }));
    });
    render(<ChatWorkbenchClient />);

    await waitFor(() => expect(screen.getByRole("combobox", { name: "对话模型" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Agent 任务" }));
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "起草一篇文档" } });
    await waitFor(() => expect(screen.getByRole("button", { name: "发送消息" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));

    expect(await screen.findByRole("textbox", { name: "Markdown 内容" })).toHaveValue("# Agent 初稿");
    expect(screen.getByRole("button", { name: "查看文档" })).toBeInTheDocument();
  });

  it("drags a project file into the composer and sends its cache key", async () => {
    const cacheKey = "a".repeat(64);
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listFiles.mockResolvedValue([{ id: "21", cacheKey, name: "notes.txt", sizeBytes: 8, documentId: null, image: false }]);
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    const row = await screen.findByRole("button", { name: "文件 notes.txt" });
    const transfer = dragTransfer();
    fireEvent.dragStart(row, { dataTransfer: transfer });
    const composer = screen.getByRole("textbox", { name: "消息内容" });
    fireEvent.dragOver(composer, { dataTransfer: transfer });
    fireEvent.drop(composer, { dataTransfer: transfer });
    expect(screen.getByLabelText("本轮引用的文件")).toHaveTextContent("notes.txt");

    fireEvent.change(composer, { target: { value: "请总结这个文件" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ fileCacheKeys: [cacheKey], message: "请总结这个文件" }),
      expect.any(Object), expect.any(AbortSignal),
    ));
  });

  it("shows an attached DOCX on the outgoing user message and after history reload", async () => {
    const cacheKey = "f".repeat(64);
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listFiles.mockResolvedValue([{ id: "42", cacheKey, name: "agent-capabilities-roadmap.docx",
      sizeBytes: 1024, documentId: null, image: false }]);
    mocks.listModels.mockResolvedValue([
      { id: "deepseek-chat", providerId: "deepseek", displayName: "DeepSeek", askSupported: true, agentSupported: true },
    ]);
    let finishStream: (() => void) | undefined;
    mocks.stream.mockImplementation(() => new Promise<void>((resolve) => { finishStream = resolve; }));
    render(<ChatWorkbenchClient />);

    const row = await screen.findByRole("button", { name: "文件 agent-capabilities-roadmap.docx" });
    const transfer = dragTransfer();
    fireEvent.dragStart(row, { dataTransfer: transfer });
    fireEvent.drop(screen.getByRole("textbox", { name: "消息内容" }), { dataTransfer: transfer });
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "你能看到这个文档吗？" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalled());
    expect(screen.getByLabelText("这条消息的附件")).toHaveTextContent("agent-capabilities-roadmap.docxDOCX");

    mocks.listMessages.mockResolvedValue([{ id: "1", kind: "user", text: "你能看到这个文档吗？",
      attachments: [{ kind: "file", name: "agent-capabilities-roadmap.docx", fileId: "42", cacheKey }] }]);
    await act(async () => { finishStream?.(); });
    await waitFor(() => expect(screen.getByLabelText("这条消息的附件")).toHaveTextContent("agent-capabilities-roadmap.docxDOCX"));
  });

  it("uploads a local file from the composer and attaches it to the next message", async () => {
    const cacheKey = "a".repeat(64);
    const uploadedFile = { id: "31", cacheKey, name: "research.txt", sizeBytes: 8, documentId: null, image: false };
    mocks.listFiles.mockResolvedValue([uploadedFile]);
    mocks.uploadFile.mockResolvedValue({ files: [{ sourceFileId: "31", cacheKey }] });
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    await waitFor(() => expect(screen.getByRole("button", { name: "从电脑上传文件" })).toBeEnabled());
    const file = new File(["资料内容"], "research.txt", { type: "text/plain" });
    fireEvent.change(screen.getByLabelText("选择要上传到工作区的文件"), { target: { files: [file] } });

    await waitFor(() => expect(mocks.createProject).toHaveBeenCalled());
    await waitFor(() => expect(mocks.uploadFile).toHaveBeenCalledWith("7", file, "openai/gpt-4.1"));
    await waitFor(() => expect(mocks.cacheFile).toHaveBeenCalledWith(
      "demo@example.com", "7", { id: "31", cacheKey }, file,
    ));
    const row = await screen.findByRole("button", { name: "文件 research.txt" });
    await waitFor(() => expect(row).toBeEnabled());
    await waitFor(() => expect(screen.getByLabelText("本轮引用的文件")).toHaveTextContent("research.txt"));

    fireEvent.click(screen.getByRole("button", { name: "Agent 任务" }));
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "阅读这份资料" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ fileCacheKeys: [cacheKey], message: "阅读这份资料" }),
      expect.any(Object), expect.any(AbortSignal),
    ));
  });

  it("keeps a file-panel upload in the workspace without attaching it to the message", async () => {
    const cacheKey = "b".repeat(64);
    const uploadedFile = { id: "32", cacheKey, name: "notes.txt", sizeBytes: 8, documentId: null, image: false };
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listFiles.mockResolvedValue([uploadedFile]);
    mocks.uploadFile.mockResolvedValue({ files: [{ sourceFileId: "32", cacheKey, fileName: "notes.txt" }] });
    mocks.listModels.mockResolvedValue([
      { id: "deepseek-chat", providerId: "deepseek", displayName: "DeepSeek", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    const file = new File(["资料内容"], "notes.txt", { type: "text/plain" });
    await waitFor(() => expect(screen.getByLabelText("选择上传文件")).toBeInTheDocument());
    fireEvent.change(screen.getByLabelText("选择上传文件"), { target: { files: [file] } });
    await waitFor(() => expect(mocks.uploadFile).toHaveBeenCalledWith("7", file, "deepseek-chat"));
    await screen.findByRole("button", { name: "文件 notes.txt" });
    expect(screen.queryByLabelText("本轮引用的文件")).not.toBeInTheDocument();
  });

  it("attaches a local image from the composer for the selected vision model", async () => {
    const cacheKey = "c".repeat(64);
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listFiles.mockResolvedValue([{ id: "33", cacheKey, name: "photo.png", sizeBytes: 128,
      documentId: null, image: true }]);
    mocks.uploadFile.mockResolvedValue({ files: [{ sourceFileId: "33", cacheKey, fileName: "photo.png",
      mimeType: "image/png" }] });
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-6-luna", providerId: "openrouter", displayName: "GPT Vision",
        askSupported: true, agentSupported: true, imageSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    const image = new File(["png"], "photo.png", { type: "image/png" });
    fireEvent.change(await screen.findByLabelText("选择要上传到工作区的文件"), { target: { files: [image] } });
    await waitFor(() => expect(screen.getByLabelText("本轮引用的文件")).toHaveTextContent("photo.png"));
    fireEvent.change(screen.getByRole("textbox", { name: "消息内容" }), { target: { value: "描述图片" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ imageCacheKeys: [cacheKey], message: "描述图片" }),
      expect.any(Object), expect.any(AbortSignal),
    ));
  });

  it("routes a dragged image through imageCacheKeys", async () => {
    const cacheKey = "c".repeat(64);
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listFiles.mockResolvedValue([{ id: "22", cacheKey, name: "photo.png", sizeBytes: 1024, documentId: null, image: true }]);
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-6-luna", providerId: "openrouter", displayName: "GPT Vision", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    const row = await screen.findByRole("button", { name: "文件 photo.png" });
    const transfer = dragTransfer();
    fireEvent.dragStart(row, { dataTransfer: transfer });
    const composer = screen.getByRole("textbox", { name: "消息内容" });
    fireEvent.drop(composer, { dataTransfer: transfer });
    fireEvent.change(composer, { target: { value: "描述图片" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ imageCacheKeys: [cacheKey], message: "描述图片" }),
      expect.any(Object), expect.any(AbortSignal),
    ));
    expect(mocks.stream.mock.calls[0][1]).not.toHaveProperty("fileCacheKeys");
  });

  it("blocks image upload and attachment while DeepSeek is selected", async () => {
    const cacheKey = "d".repeat(64);
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listFiles.mockResolvedValue([{ id: "23", cacheKey, name: "photo.png", sizeBytes: 1024, documentId: null, image: true }]);
    mocks.listModels.mockResolvedValue([
      { id: "deepseek-chat", providerId: "deepseek", displayName: "DeepSeek", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    await screen.findByRole("button", { name: "文件 photo.png" });
    const composerInput = screen.getByLabelText("选择要上传到工作区的文件");
    expect((composerInput as HTMLInputElement).accept).not.toContain(".png");
    const image = new File(["png"], "photo.png", { type: "image/png" });
    fireEvent.change(composerInput, { target: { files: [image] } });
    expect((await screen.findAllByText(/当前模型不支持图片，请先切换/)).length).toBeGreaterThan(0);
    expect(mocks.uploadFile).not.toHaveBeenCalled();

    const row = screen.getByRole("button", { name: "文件 photo.png" });
    const transfer = dragTransfer();
    fireEvent.dragStart(row, { dataTransfer: transfer });
    fireEvent.drop(screen.getByRole("textbox", { name: "消息内容" }), { dataTransfer: transfer });
    expect(screen.queryByLabelText("本轮引用的文件")).not.toBeInTheDocument();
  });

  it("drags a Markdown draft into the composer as Agent document context", async () => {
    const document = { id: "51", projectId: "7", title: "文章", documentType: "MARKDOWN", draftContent: "# 文章", version: 1 };
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listDocuments.mockResolvedValue([document]);
    mocks.getDocument.mockResolvedValue(document);
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    const row = await screen.findByRole("button", { name: "文件 文章.md" });
    const transfer = dragTransfer();
    fireEvent.dragStart(row, { dataTransfer: transfer });
    const composer = screen.getByRole("textbox", { name: "消息内容" });
    fireEvent.drop(composer, { dataTransfer: transfer });
    expect(screen.getByLabelText("本轮引用的文件")).toHaveTextContent("文章.md");
    fireEvent.change(composer, { target: { value: "修改这篇文档" } });
    await waitFor(() => expect(screen.getByRole("button", { name: "发送消息" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ mode: "AGENT", clientContext: {
        activeDocument: { documentId: "51", type: "markdown", fileName: "文章" },
        referencedDocumentIds: ["51"],
      } }), expect.any(Object), expect.any(AbortSignal),
    ));
  });

  it("keeps multiple dragged Markdown documents visible and sends only selected references", async () => {
    const first = { id: "51", projectId: "7", title: "文章", documentType: "MARKDOWN", draftContent: "# 文章", version: 1 };
    const second = { id: "52", projectId: "7", title: "素材", documentType: "MARKDOWN", draftContent: "# 素材", version: 1 };
    mocks.listProjects.mockResolvedValue([{ id: 7, name: "我的工作区" }]);
    mocks.listDocuments.mockResolvedValue([first, second]);
    mocks.getDocument.mockResolvedValue(first);
    mocks.listModels.mockResolvedValue([
      { id: "openai/gpt-4.1", providerId: "openai", displayName: "GPT-4.1", askSupported: true, agentSupported: true },
    ]);
    render(<ChatWorkbenchClient />);

    const composer = screen.getByRole("textbox", { name: "消息内容" });
    for (const name of ["文章.md", "素材.md"]) {
      const row = await screen.findByRole("button", { name: `文件 ${name}` });
      const transfer = dragTransfer();
      fireEvent.dragStart(row, { dataTransfer: transfer });
      fireEvent.drop(composer, { dataTransfer: transfer });
    }
    expect(screen.getByLabelText("本轮引用的文件")).toHaveTextContent("文章.md素材.md");
    fireEvent.click(screen.getByRole("button", { name: "移除文档 文章.md" }));
    expect(screen.getByLabelText("本轮引用的文件")).not.toHaveTextContent("文章.md");

    fireEvent.change(composer, { target: { value: "整理素材" } });
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }));
    await waitFor(() => expect(mocks.stream).toHaveBeenCalledWith(
      "9", expect.objectContaining({ clientContext: expect.objectContaining({ referencedDocumentIds: ["52"] }) }),
      expect.any(Object), expect.any(AbortSignal),
    ));
  });
});
