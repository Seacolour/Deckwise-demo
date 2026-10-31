// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ComponentProps } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  openDesktopMenu: vi.fn(),
  setDesktopTitleBarTheme: vi.fn(),
}));

vi.mock("@/src/components/_shared/brand/BrandLogo", () => ({
  BrandLogo: (props: ComponentProps<"span">) => (
    <span data-testid="desktop-brand-logo" {...props} />
  ),
}));

import { DesktopWindowFrame } from "@/src/components/_shared/layouts/DesktopWindowFrame";

function installDesktopBridge() {
  Object.defineProperty(window, "deckwiseDesktop", {
    configurable: true,
    value: {
      isDesktop: true,
      platform: "win32",
      getRuntimeInfo: vi.fn().mockResolvedValue({
        appVersion: "0.1.0",
        deviceName: "Test PC",
        platform: "win32",
      }),
      openDesktopAuthUrl: vi.fn().mockResolvedValue(true),
      openDesktopMenu: mocks.openDesktopMenu,
      setDesktopTitleBarTheme: mocks.setDesktopTitleBarTheme,
    },
  });
}

describe("DesktopWindowFrame", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    installDesktopBridge();
    mocks.openDesktopMenu.mockResolvedValue(true);
    mocks.setDesktopTitleBarTheme.mockResolvedValue(true);
    document.documentElement.dataset.themeResolved = "light";
  });

  afterEach(() => {
    delete window.deckwiseDesktop;
    delete document.documentElement.dataset.deckwiseDesktop;
    delete document.documentElement.dataset.deckwisePlatform;
    delete document.documentElement.dataset.themeResolved;
  });

  it("renders the compact application chrome around page content", () => {
    render(<DesktopWindowFrame><main>Workspace</main></DesktopWindowFrame>);

    expect(screen.getByTestId("desktop-window-chrome")).toBeInTheDocument();
    expect(screen.getByTestId("desktop-brand-logo")).toBeInTheDocument();
    expect(screen.getByText("Workspace")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "File" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Help" })).toBeInTheDocument();
    expect(document.documentElement.dataset.deckwiseDesktop).toBe("true");
    expect(document.documentElement.dataset.deckwisePlatform).toBe("win32");
  });

  it("opens the native Electron menu beneath the selected label", () => {
    render(<DesktopWindowFrame><main>Workspace</main></DesktopWindowFrame>);
    const fileButton = screen.getByRole("button", { name: "File" });
    vi.spyOn(fileButton, "getBoundingClientRect").mockReturnValue({
      bottom: 36,
      height: 28,
      left: 104,
      right: 140,
      top: 8,
      width: 36,
      x: 104,
      y: 8,
      toJSON: () => ({}),
    });

    fireEvent.click(fileButton);

    expect(mocks.openDesktopMenu).toHaveBeenCalledWith("file", {
      x: 104,
      y: 36,
    });
  });

  it("keeps native window controls synchronized with the resolved theme", async () => {
    render(<DesktopWindowFrame><main>Workspace</main></DesktopWindowFrame>);

    expect(mocks.setDesktopTitleBarTheme).toHaveBeenCalledWith("light");
    document.documentElement.dataset.themeResolved = "dark";

    await waitFor(() => {
      expect(mocks.setDesktopTitleBarTheme).toHaveBeenLastCalledWith("dark");
    });
  });
});
