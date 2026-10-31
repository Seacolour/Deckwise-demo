// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WorkbenchSidebar } from "@/src/features/workbench/WorkbenchSidebar";

function renderSidebar(overrides: Partial<Parameters<typeof WorkbenchSidebar>[0]> = {}) {
  const callbacks = {
    onToggleCollapsed: vi.fn(),
    onCloseMobile: vi.fn(),
    onNewConversation: vi.fn(),
    onCreateWorkspace: vi.fn(async () => undefined),
    onRenameWorkspace: vi.fn(async () => undefined),
    onDeleteWorkspace: vi.fn(async () => undefined),
    onNewConversationInProject: vi.fn(),
    onSelectProject: vi.fn(),
    onSelectSession: vi.fn(),
  };
  render(<WorkbenchSidebar
    projects={[{ id: 7, name: "个人写作" }, { id: 8, name: "研究资料" }]}
    sessions={[{ id: 91, projectId: 7, title: "博客大纲" }]}
    activeProjectId="7"
    activeSessionId="91"
    accountName="Demo"
    collapsed={false}
    mobileOpen={false}
    busy={false}
    {...callbacks}
    {...overrides}
  />);
  return callbacks;
}

describe("WorkbenchSidebar", () => {
  it("groups the active project's conversations and keeps navigation callbacks", () => {
    const callbacks = renderSidebar();
    expect(screen.getByText("Deckwise Demo")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "个人写作" })).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("button", { name: "博客大纲" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "账户菜单" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "新建对话" }));
    fireEvent.click(screen.getByRole("button", { name: "博客大纲" }));
    fireEvent.click(screen.getByRole("button", { name: "研究资料" }));
    expect(callbacks.onNewConversation).toHaveBeenCalledOnce();
    expect(callbacks.onSelectProject).toHaveBeenCalledWith("8");
    expect(callbacks.onSelectSession).toHaveBeenCalledWith("91");
  });

  it("collapses and expands the active workspace's conversations", () => {
    renderSidebar();
    fireEvent.click(screen.getByRole("button", { name: "个人写作" }));
    expect(screen.queryByRole("button", { name: "博客大纲" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "个人写作" }));
    expect(screen.getByRole("button", { name: "博客大纲" })).toBeInTheDocument();
  });

  it("provides a keyboard-accessible icon rail without exposing hidden session rows", () => {
    const callbacks = renderSidebar({ collapsed: true });
    expect(screen.getByRole("button", { name: "展开侧栏" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "博客大纲" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "新建对话" })).toHaveAttribute("title", "新建对话");
    fireEvent.click(screen.getByRole("button", { name: "展开侧栏" }));
    expect(callbacks.onToggleCollapsed).toHaveBeenCalledOnce();
  });

  it("shows full navigation in the mobile overlay even when desktop is collapsed", () => {
    const callbacks = renderSidebar({ collapsed: true, mobileOpen: true });
    expect(screen.getByRole("button", { name: "博客大纲" })).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("关闭导航"));
    expect(callbacks.onCloseMobile).toHaveBeenCalledOnce();
  });

  it("filters workspaces and creates one from the sidebar", async () => {
    const callbacks = renderSidebar();
    fireEvent.click(screen.getByRole("button", { name: "搜索工作区" }));
    fireEvent.change(screen.getByRole("textbox", { name: "搜索工作区和当前对话" }), { target: { value: "研究" } });
    expect(screen.getByRole("button", { name: "研究资料" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "个人写作" })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "关闭搜索" }));
    fireEvent.click(screen.getByRole("button", { name: "新建工作区" }));
    fireEvent.change(screen.getByRole("textbox", { name: "新工作区名称" }), { target: { value: "新博客" } });
    fireEvent.click(screen.getByRole("button", { name: "创建工作区" }));
    await waitFor(() => expect(callbacks.onCreateWorkspace).toHaveBeenCalledWith("新博客"));
  });

  it("opens account actions from the account icon", async () => {
    renderSidebar();
    fireEvent.pointerDown(screen.getByRole("button", { name: "账户菜单" }), { button: 0, ctrlKey: false });
    const settings = await screen.findByRole("menuitem", { name: "设置" });
    expect(settings).toHaveAttribute("href", "/app/settings/account");
    expect(screen.getByRole("menuitem", { name: "意见反馈" })).toHaveAttribute("href", "https://github.com/Seacolour/Deckwise-demo/issues/new");
    expect(screen.getByRole("menuitem", { name: "退出登录" })).toBeInTheDocument();
  });

  it("starts a new conversation in the chosen workspace", () => {
    const callbacks = renderSidebar();
    fireEvent.click(screen.getByRole("button", { name: "在个人写作中创建对话" }));
    expect(callbacks.onNewConversationInProject).toHaveBeenCalledWith("7");
  });

  it("renames a workspace from its action menu", async () => {
    const callbacks = renderSidebar();
    fireEvent.pointerDown(screen.getByRole("button", { name: "工作区“个人写作”的操作" }), { button: 0, ctrlKey: false });
    fireEvent.click(await screen.findByRole("menuitem", { name: "重命名" }));
    fireEvent.change(screen.getByRole("textbox", { name: "工作区名称" }), { target: { value: "写作资料" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(callbacks.onRenameWorkspace).toHaveBeenCalledWith("7", "写作资料"));
  });

  it("requires confirmation before deleting a workspace", async () => {
    const callbacks = renderSidebar();
    fireEvent.pointerDown(screen.getByRole("button", { name: "工作区“个人写作”的操作" }), { button: 0, ctrlKey: false });
    fireEvent.click(await screen.findByRole("menuitem", { name: "删除工作区" }));
    expect(callbacks.onDeleteWorkspace).not.toHaveBeenCalled();
    expect(screen.getByText(/文档和文件关联会被清理/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(callbacks.onDeleteWorkspace).toHaveBeenCalledWith("7"));
  });
});
