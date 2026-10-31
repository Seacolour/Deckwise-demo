import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

function source(path: string) {
  return readFileSync(join(process.cwd(), path), "utf8");
}

describe("conversation workbench routes", () => {
  it("keeps one product root and routes home, canvas, and settings to the workbench", () => {
    expect(source("app/(product)/layout.tsx")).toContain("RuntimeRootDocument");
    expect(source("app/(product)/app/page.tsx")).toContain("ChatWorkbenchClient");
    expect(source("app/(product)/canvas/page.tsx")).toContain("ChatWorkbenchClient");
    expect(source("app/(product)/app/settings/[section]/page.tsx")).toContain("ChatWorkbenchClient");
  });

  it("does not load slide editor chrome in the active workspace shell", () => {
    const shell = source("src/components/_shared/layouts/AppShell.tsx");
    const canvasLayout = source("app/(product)/canvas/layout.tsx");
    expect(shell).not.toContain("SharedProjectComposerHost");
    expect(shell).not.toContain("AppHeaderOutlet");
    expect(canvasLayout).not.toContain("deckwise-editor.css");
    expect(canvasLayout).not.toContain("glide-data-grid");
  });

  it("provides a sidebar, conversation, and responsive composer", () => {
    const page = source("src/features/workbench/ChatWorkbenchClient.tsx");
    const sidebar = source("src/features/workbench/WorkbenchSidebar.tsx");
    const css = source("src/features/workbench/workbench.module.css");
    expect(page).toContain("streamSendMessageRPC");
    expect(page).toContain("listWorkbenchMessages");
    expect(page).toContain("WorkbenchSidebar");
    expect(sidebar).toContain("新建对话");
    expect(css).toContain(".sidebar");
    expect(css).toContain(".composerDock");
    expect(css).toContain("@media (max-width: 760px)");
  });
});
