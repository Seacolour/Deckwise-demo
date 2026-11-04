import { beforeEach, describe, expect, it, vi } from "vitest";

const fetchMock = vi.hoisted(() => vi.fn());
vi.mock("@/src/lib/api/fetcherWithAuth", () => ({ apiFetchWithAuth: fetchMock }));

import {
  createWorkbenchSession,
  getWorkbenchFilePreviewUrl,
  getWorkbenchInlinePreviewPath,
  listWorkbenchFiles,
  listWorkbenchMessages,
  listWorkbenchProjects,
  uploadWorkbenchFile,
} from "@/src/features/workbench/workbenchApi";

describe("workbench API", () => {
  beforeEach(() => fetchMock.mockReset());

  it("loads non-archived projects", async () => {
    fetchMock.mockResolvedValue({ records: [{ id: 7, name: "个人写作" }] });
    await expect(listWorkbenchProjects()).resolves.toEqual([{ id: 7, name: "个人写作" }]);
    expect(fetchMock).toHaveBeenCalledWith("/projects/list", expect.objectContaining({
      method: "POST",
      body: expect.objectContaining({ archived: false }),
    }));
  });

  it("normalizes linked project files for the workspace list", async () => {
    const cacheKey = "b".repeat(64);
    fetchMock.mockResolvedValue([
      { id: "21", fileName: "photo.png", cacheKey, sizeBytes: 1024 },
      { id: "22", fileName: "incomplete.txt" },
    ]);
    await expect(listWorkbenchFiles("7")).resolves.toEqual([
      { id: "21", name: "photo.png", cacheKey, sizeBytes: 1024, documentId: null, image: true },
    ]);
    expect(fetchMock).toHaveBeenCalledWith("/projects/7/files", { requestPolicy: "query" });
  });

  it("uploads a file through the existing project batch endpoint", async () => {
    fetchMock.mockResolvedValue({ files: [] });
    const file = new File(["# Hello"], "hello.md", { type: "text/markdown" });
    await uploadWorkbenchFile("7", file);
    expect(fetchMock).toHaveBeenCalledWith("/files/upload/batch", expect.objectContaining({
      method: "POST", params: { projectId: "7", uploadSource: "user_upload" }, requestPolicy: "upload",
      body: expect.any(FormData),
    }));
    const body = fetchMock.mock.calls[0][1].body as FormData;
    expect(body.get("files")).toBe(file);
  });

  it("passes the selected vision model for image uploads", async () => {
    fetchMock.mockResolvedValue({ files: [] });
    const image = new File(["png"], "photo.png", { type: "image/png" });
    await uploadWorkbenchFile("7", image, "openai/gpt-6-luna");
    expect(fetchMock).toHaveBeenCalledWith("/files/upload/batch", expect.objectContaining({
      params: { projectId: "7", uploadSource: "user_upload", model: "openai/gpt-6-luna" },
    }));
  });

  it("requests an authorized temporary URL for physical file preview", async () => {
    fetchMock.mockResolvedValue({ accessUrl: "https://example.com/file.txt?token=test" });
    await expect(getWorkbenchFilePreviewUrl("7", "21")).resolves.toBe("https://example.com/file.txt?token=test");
    expect(fetchMock).toHaveBeenCalledWith("/files/21/access-url", {
      params: { projectId: "7" }, requestPolicy: "query",
    });
  });

  it("builds same-origin previews only for browser-supported file types", () => {
    const base = { id: "21", cacheKey: "a".repeat(64), sizeBytes: 8, documentId: null, image: false };
    expect(getWorkbenchInlinePreviewPath("7", { ...base, name: "notes.txt" }))
      .toBe("/api/workbench/files/21/preview?projectId=7&kind=txt");
    expect(getWorkbenchInlinePreviewPath("7", { ...base, name: "report.docx" })).toBeNull();
  });

  it("creates a session with a concise title", async () => {
    fetchMock.mockResolvedValue({ id: 99, projectId: 7, title: "文章大纲" });
    await createWorkbenchSession("7", "  文章  大纲  ");
    expect(fetchMock).toHaveBeenCalledWith("/projects/7/create", expect.objectContaining({
      body: { title: "文章 大纲" },
    }));
  });

  it("keeps only visible user and assistant history", async () => {
    fetchMock.mockResolvedValue([
      { id: 1, role: "user", content: "你好" },
      { id: 2, role: "tool", content: "internal tool result" },
      { id: 3, role: "assistant", content: "你好，我来帮你。" },
      { id: 4, role: "system", content: "internal context" },
    ]);
    await expect(listWorkbenchMessages("99")).resolves.toEqual([
      { id: "1", kind: "user", text: "你好" },
      { id: "assistant-3", kind: "assistant", parts: [{ id: "3", kind: "text", text: "你好，我来帮你。" }] },
    ]);
  });

  it("restores attachments on their user message after refreshing history", async () => {
    fetchMock.mockResolvedValue([
      { id: 1, seq: 1, role: "user", type: "text", content: "你能看到这个文档吗？",
        attachments: [{ kind: "file", name: "agent-capabilities-roadmap.docx", fileId: "42", cacheKey: "a".repeat(64) }] },
      { id: 2, seq: 2, role: "assistant", type: "text", content: "可以。" },
    ]);
    await expect(listWorkbenchMessages("9")).resolves.toEqual([
      { id: "1", kind: "user", text: "你能看到这个文档吗？", attachments: [
        { kind: "file", name: "agent-capabilities-roadmap.docx", fileId: "42", cacheKey: "a".repeat(64) },
      ] },
      { id: "assistant-2", kind: "assistant", parts: [{ id: "2", kind: "text", text: "可以。" }] },
    ]);
  });

  it("replays interleaved assistant text and paired tool calls without exposing metadata", async () => {
    fetchMock.mockResolvedValue([
      { id: 1, seq: 1, role: "user", type: "text", content: "整理材料" },
      { id: 2, seq: 2, role: "assistant", type: "text", content: "我先查看文件。" },
      { id: 3, seq: 3, role: "assistant", type: "tool_output", content: JSON.stringify({ phase: "call", call_id: "c1", name: "file_list", arguments_json: "{}" }) },
      { id: 4, seq: 4, role: "tool", type: "tool_output", content: JSON.stringify({ phase: "result", call_id: "c1", name: "file_list", result_json: '{"files":[]}' }) },
      { id: 5, seq: 5, role: "assistant", type: "text", content: "目前没有文件。" },
      { id: 6, seq: 6, role: "assistant", type: "metadata", content: "internal" },
    ]);
    await expect(listWorkbenchMessages("9")).resolves.toEqual([
      { id: "1", kind: "user", text: "整理材料" },
      { id: "assistant-2", kind: "assistant", parts: [
        { id: "2", kind: "text", text: "我先查看文件。" },
        { id: "3", kind: "tool", name: "file_list", callId: "c1", argumentsRaw: "{}", resultRaw: '{"files":[]}', status: "completed" },
        { id: "5", kind: "text", text: "目前没有文件。" },
      ] },
    ]);
  });

  it("loads history after the first 500 rows", async () => {
    const firstPage = Array.from({ length: 500 }, (_, index) => ({
      id: index + 1, seq: index + 1, role: "user", type: "text", content: `Message ${index + 1}`,
    }));
    fetchMock.mockResolvedValueOnce(firstPage).mockResolvedValueOnce([
      { id: 501, seq: 501, role: "assistant", type: "text", content: "Done" },
    ]);
    const entries = await listWorkbenchMessages("9");
    expect(entries).toHaveLength(501);
    expect(entries.at(-1)).toEqual({ id: "assistant-501", kind: "assistant", parts: [{ id: "501", kind: "text", text: "Done" }] });
    expect(fetchMock).toHaveBeenLastCalledWith("/sessions/9/messages/after-seq?afterSeq=500&limit=500", { requestPolicy: "query" });
  });
});
