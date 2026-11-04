// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ list: vi.fn(), update: vi.fn(), begin: vi.fn(), disconnect: vi.fn() }));
vi.mock("@/src/lib/api/agentPlugins", () => ({
  listAgentPlugins: mocks.list,
  updateAgentPlugin: mocks.update,
  beginNotionConnection: mocks.begin,
  disconnectNotionConnection: mocks.disconnect,
}));

import { AgentPluginsSettingsContent } from "@/src/components/features/settings/AgentPluginsSettingsContent";

describe("AgentPluginsSettingsContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.list.mockResolvedValue([
      { id: "docs-langchain", kind: "mcp", name: "LangChain 文档 MCP", description: "官方文档", enabled: false,
        sourceUrl: "https://docs.langchain.com/use-these-docs" },
      { id: "skill:document-review", kind: "skill", name: "document-review", description: "文档审阅", enabled: true,
        sourceUrl: null },
    ]);
  });

  it("separates MCP plugins and Skills and updates the selected user switch", async () => {
    mocks.update.mockResolvedValue({ id: "docs-langchain", kind: "mcp", name: "LangChain 文档 MCP",
      description: "官方文档", enabled: true, sourceUrl: "https://docs.langchain.com/use-these-docs" });
    render(<AgentPluginsSettingsContent />);
    expect(await screen.findByText("LangChain 文档 MCP")).toBeInTheDocument();
    expect(screen.queryByText("document-review")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /LangChain 文档 MCP/ }));
    await waitFor(() => expect(mocks.update).toHaveBeenCalledWith("docs-langchain", true));
    await waitFor(() => expect(screen.getByRole("button", { name: /LangChain 文档 MCP/ })).toHaveAttribute("aria-pressed", "true"));
    fireEvent.click(screen.getByRole("tab", { name: "Skills" }));
    expect(screen.getByText("document-review")).toBeInTheDocument();
    expect(screen.queryByText("LangChain 文档 MCP")).not.toBeInTheDocument();
  });

  it("requires a Notion connection before enabling and separates disable from disconnect", async () => {
    mocks.list.mockResolvedValueOnce([
      { id: "notion", kind: "mcp", name: "Notion", description: "Pages", enabled: false,
        sourceUrl: "https://developers.notion.com/guides/mcp/get-started-with-mcp",
        connectionStatus: "disconnected", workspaceName: null },
    ]);
    render(<AgentPluginsSettingsContent />);
    expect(await screen.findByText("Notion")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Connect Notion" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Notion Disabled" })).toBeDisabled();
  });
});
