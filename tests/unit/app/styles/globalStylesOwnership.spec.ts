import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

function readWorkspaceFile(path: string): string {
  return readFileSync(join(process.cwd(), path), "utf8").replace(/\r\n/g, "\n");
}

describe("global styles ownership", () => {
  it("keeps app/globals.css as the stylesheet entrypoint", () => {
    const globalsCss = readWorkspaceFile("app/globals.css");

    expect(globalsCss).toBe([
      "/* Tailwind v4 global stylesheet entrypoint, including Electron window chrome. */",
      '@import "tailwindcss";',
      '@import "./styles/tokens.css";',
      '@import "./styles/desktop-window-chrome.css";',
      '@import "./styles/app-shell.css";',
      '@import "./styles/base.css";',
      '@import "./styles/component-utilities.css";',
      '@import "./styles/syntax.css";',
      '@import "./styles/theme-utilities.css";',
      '@import "./styles/utilities.css";',
      '@import "./styles/motion.css";',
      '@import "./styles/scrollbars.css";',
      '@import "./styles/menus.css";',
      '@import "./styles/toasts.css";',
      "",
    ].join("\n"));
  });

  it("keeps base styles limited to reset and selection ownership", () => {
    const baseCss = readWorkspaceFile("app/styles/base.css");

    expect(baseCss).toContain("::selection");
    expect(baseCss).toContain("@layer base");
    expect(baseCss).not.toContain(".hljs-");
    expect(baseCss).not.toContain(".deckwise-menu");
    expect(baseCss).not.toContain(".chat-bubble-enter");
    expect(baseCss).not.toContain(".glass");
  });

  it("keeps shared menu switch classes generic and centralized", () => {
    const menusCss = readWorkspaceFile("app/styles/menus.css");
    const menuStyles = readWorkspaceFile("src/lib/styles/menu-styles.ts");
    const legacyCssClass = ["deckwise", "slides", "menu", "switch"].join("-");
    const legacyConstantPrefix = ["SLIDES", "MENU", "SWITCH"].join("_");

    expect(menusCss).toContain(".deckwise-menu-switch");
    expect(menusCss).toContain(".deckwise-menu-switch-track");
    expect(menusCss).toContain(".deckwise-menu-switch-thumb");
    expect(menuStyles).toContain("deckwise-menu-switch");
    expect(`${menusCss}\n${menuStyles}`).not.toContain(legacyCssClass);
    expect(menuStyles).not.toContain(legacyConstantPrefix);
  });
});
