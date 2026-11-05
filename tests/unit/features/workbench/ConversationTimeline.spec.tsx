// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ConversationTimeline, type ConversationEntry } from "@/src/features/workbench/ConversationTimeline";

const mocks = vi.hoisted(() => ({ copyTextToClipboard: vi.fn() }));

vi.mock("@/src/lib/clipboard/copyTextToClipboard", () => ({
  copyTextToClipboard: mocks.copyTextToClipboard,
}));

vi.mock("@/src/features/workbench/WorkbenchMarkdown", () => ({
  WorkbenchMarkdown: ({ text }: { text: string }) => <div data-testid="assistant-markdown">{text}</div>,
}));

const tool = (
  id: string,
  status: "running" | "completed" | "error" | "stopped",
): Extract<ConversationEntry, { kind: "assistant" }>["parts"][number] => ({
  id,
  kind: "tool",
  name: `工具 ${id}`,
  callId: `call-${id}`,
  argumentsRaw: null,
  resultRaw: null,
  status,
});

describe("ConversationTimeline", () => {
  beforeEach(() => {
    mocks.copyTextToClipboard.mockReset();
    mocks.copyTextToClipboard.mockResolvedValue(true);
  });

  it("renders assistant text through the existing Markdown renderer", () => {
    render(<ConversationTimeline entries={[
      { id: "u1", kind: "user", text: "请写一个大纲" },
      { id: "a1", kind: "assistant", parts: [{ id: "t1", kind: "text", text: "## 文章大纲\n\n- 背景" }] },
    ]} />);

    expect(screen.getByTestId("assistant-markdown")).toHaveTextContent("## 文章大纲");
    expect(screen.getByTestId("assistant-markdown")).toHaveTextContent("- 背景");
    expect(screen.getByText("请写一个大纲")).toBeInTheDocument();
    expect(screen.queryByText("Deckwise Demo")).not.toBeInTheDocument();
    expect(screen.queryByText("你")).not.toBeInTheDocument();
  });

  it("copies user text and visible assistant Markdown without tool payloads", async () => {
    render(<ConversationTimeline entries={[
      { id: "u1", kind: "user", text: "请总结这份材料" },
      { id: "a1", kind: "assistant", parts: [
        { id: "t1", kind: "text", text: "## 摘要" },
        { id: "tool", kind: "tool", name: "document_read", callId: "call-1", status: "completed",
          argumentsRaw: '{"api_key":"secret-argument"}', resultRaw: '{"content":"secret-result"}' },
        { id: "t2", kind: "text", text: "第一段内容" },
      ] },
    ]} />);

    fireEvent.click(screen.getByRole("button", { name: "复制你的消息" }));
    fireEvent.click(screen.getByRole("button", { name: "复制 AI 回复" }));
    await waitFor(() => expect(mocks.copyTextToClipboard).toHaveBeenCalledTimes(2));
    expect(mocks.copyTextToClipboard).toHaveBeenNthCalledWith(1, "请总结这份材料");
    expect(mocks.copyTextToClipboard).toHaveBeenNthCalledWith(2, "## 摘要\n\n第一段内容");
  });

  it("shows each tool lifecycle state without conflating it with assistant prose", () => {
    render(<ConversationTimeline entries={[
      { id: "a1", kind: "assistant", parts: [
        tool("one", "running"),
        tool("two", "completed"),
        tool("three", "error"),
        tool("four", "stopped"),
      ] },
    ]} />);

    expect(screen.getByText("执行中")).toBeInTheDocument();
    expect(screen.getByText("已完成")).toBeInTheDocument();
    expect(screen.getByText("执行失败")).toBeInTheDocument();
    expect(screen.getByText("已停止")).toBeInTheDocument();
    expect(screen.queryByText("正在处理请求…")).not.toBeInTheDocument();
  });

  it("shows a bounded Todo snapshot as task progress", () => {
    const { container } = render(<ConversationTimeline entries={[{
      id: "a1", kind: "assistant", parts: [{
        id: "old-plan", kind: "tool", name: "write_todos", callId: "plan-0", status: "completed",
        argumentsRaw: null,
        resultRaw: '{"success":true,"todos":[{"content":"读取资料","status":"pending"},{"content":"整理结论","status":"pending"}]}',
      }, {
        id: "plan", kind: "tool", name: "write_todos", callId: "plan-1", status: "completed",
        argumentsRaw: '{"todos":[{"content":"读取资料","status":"in_progress"},{"content":"整理结论","status":"pending"}]}',
        resultRaw: '{"success":true,"todos":[{"content":"读取资料","status":"completed"},{"content":"整理结论","status":"in_progress"}]}',
      }],
    }]} />);
    expect(screen.getByRole("region", { name: "任务计划" })).toBeInTheDocument();
    expect(screen.getByText("1/2 已完成")).toBeInTheDocument();
    expect(screen.getByText("读取资料")).toBeInTheDocument();
    expect(screen.getByText("整理结论")).toBeInTheDocument();
    expect(screen.getByText("进行中")).toBeInTheDocument();
    expect(screen.getAllByText("读取资料")).toHaveLength(1);
    expect(container.innerHTML).not.toContain("plan-1");
  });

  it("keeps tool details collapsed initially and lets the user expand them", () => {
    const { container } = render(<ConversationTimeline entries={[
      { id: "a1", kind: "assistant", parts: [tool("one", "completed")] },
    ]} />);
    const details = container.querySelector("details");
    expect(details).not.toHaveAttribute("open");

    fireEvent.click(screen.getByText("工具 one").closest("summary")!);
    expect(details).toHaveAttribute("open");
    expect(screen.getByText("执行状态：已完成。")).toBeInTheDocument();
  });

  it("shows bounded metadata only for known tools and never exposes raw secrets", () => {
    const { container } = render(<ConversationTimeline entries={[
      { id: "a1", kind: "assistant", parts: [{
        id: "sensitive",
        kind: "tool",
        name: "document_read",
        callId: "call-private-id-2468",
        argumentsRaw: '{"document_id":42,"api_key":"sk-private-argument-123"}',
        resultRaw: '{"success":true,"content":"private-result-567"}',
        status: "completed",
      }] },
    ]} />);

    fireEvent.click(screen.getByText("document_read").closest("summary")!);
    expect(screen.getByText("文档 ID")).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByText("执行结果")).toBeInTheDocument();
    expect(screen.getByText("成功")).toBeInTheDocument();
    expect(container.innerHTML).not.toContain("sk-private-argument-123");
    expect(container.innerHTML).not.toContain("private-result-567");
    expect(container.innerHTML).not.toContain("call-private-id-2468");
  });

  it("does not extract metadata from unknown tools or unbounded and string-shaped values", () => {
    const { container } = render(<ConversationTimeline entries={[
      { id: "a1", kind: "assistant", parts: [
        { id: "unknown", kind: "tool", name: "custom_tool", callId: null,
          argumentsRaw: '{"document_id":72,"api_key":"secret-unknown"}',
          resultRaw: '{"success":true}', status: "completed" },
        { id: "file", kind: "tool", name: "file_content_read", callId: null,
          argumentsRaw: '{"file_id":"secret-as-id","max_fragments":999999}',
          resultRaw: '{"success":false,"content":"secret-result"}', status: "error" },
      ] },
    ]} />);

    fireEvent.click(screen.getByText("custom_tool").closest("summary")!);
    fireEvent.click(screen.getByText("file_content_read").closest("summary")!);
    expect(screen.queryByText("文档 ID")).not.toBeInTheDocument();
    expect(screen.queryByText("文件 ID")).not.toBeInTheDocument();
    expect(screen.queryByText("请求切片")).not.toBeInTheDocument();
    expect(container.innerHTML).not.toContain("secret-unknown");
    expect(container.innerHTML).not.toContain("secret-as-id");
    expect(container.innerHTML).not.toContain("secret-result");
  });

  it("summarizes file reads with only bounded pagination counts", () => {
    const { container } = render(<ConversationTimeline entries={[
      { id: "a1", kind: "assistant", parts: [{ id: "read", kind: "tool", name: "file_content_read",
        callId: null, status: "completed",
        argumentsRaw: '{"file_id":18,"start_fragment":2,"max_fragments":10,"query":"private search text"}',
        resultRaw: '{"success":true,"returned_fragments":7,"total_fragments":24,"content":"private document body"}',
      }] },
    ]} />);

    fireEvent.click(screen.getByText("file_content_read").closest("summary")!);
    for (const label of ["文件 ID", "起始切片", "请求切片", "返回切片", "总切片"]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    for (const number of ["18", "2", "10", "7", "24"]) {
      expect(screen.getByText(number)).toBeInTheDocument();
    }
    expect(container.innerHTML).not.toContain("private search text");
    expect(container.innerHTML).not.toContain("private document body");
  });

  it("labels pending work only before visible text or while a tool is running", () => {
    const pending: ConversationEntry = { id: "a1", kind: "assistant", parts: [], pending: true };
    const { rerender } = render(<ConversationTimeline entries={[pending]} activity="正在生成回复…" />);
    expect(screen.getByText("正在生成回复…")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "复制 AI 回复" })).not.toBeInTheDocument();

    rerender(<ConversationTimeline entries={[
      { ...pending, parts: [{ id: "text", kind: "text", text: "已有可见回复" }] },
    ]} activity="正在生成回复…" />);
    expect(screen.queryByText("正在生成回复…")).not.toBeInTheDocument();

    rerender(<ConversationTimeline entries={[
      { ...pending, parts: [{ id: "text", kind: "text", text: "已有可见回复" }, tool("one", "running")] },
    ]} activity="正在生成回复…" />);
    expect(screen.getByText("工具执行中…")).toBeInTheDocument();
  });
});
