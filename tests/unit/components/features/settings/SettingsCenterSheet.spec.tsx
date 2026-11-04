// @vitest-environment jsdom

import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  routerBack: vi.fn(),
  routerPush: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    back: mocks.routerBack,
    push: mocks.routerPush,
  }),
}));

vi.mock("@/src/components/features/settings/GeneralSettingsContent", () => ({
  GeneralSettingsContent: () => <div>General settings content</div>,
}));

vi.mock("@/src/components/features/settings/AccountSettingsContent", () => ({
  AccountSettingsContent: () => <div>Account settings content</div>,
}));

vi.mock("@/src/components/features/settings/ModelProviderSettingsContent", () => ({
  ModelProviderSettingsContent: () => <div>Model provider settings content</div>,
}));

vi.mock("@/src/components/features/settings/AgentPluginsSettingsContent", () => ({
  AgentPluginsSettingsContent: () => <div>Agent plugins settings content</div>,
}));

vi.mock("@/src/stores/auth", () => ({
  useAuthSnapshot: () => ({
    rawUser: {
      email: "demo@example.com",
      name: "Demo User",
    },
  }),
}));

import { SettingsCenterSheet } from "@/src/components/features/settings/SettingsCenterSheet";

describe("SettingsCenterSheet", () => {
  beforeEach(() => {
    mocks.routerBack.mockReset();
    mocks.routerPush.mockReset();
    window.history.replaceState({}, "", "/canvas?projectId=p1");
  });

  it("closes route-backed settings pages to the app home by default", () => {
    render(<SettingsCenterSheet open section="account" />);

    fireEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(mocks.routerPush).toHaveBeenCalledWith("/app");
  });

  it("keeps section switches on canonical settings URLs without route remounts", async () => {
    window.history.replaceState({}, "", "/app/settings/account");

    render(<SettingsCenterSheet open section="account" />);

    fireEvent.click(screen.getByRole("button", { name: "General" }));

    expect(await screen.findByText("General settings content")).toBeInTheDocument();
    expect(window.location.pathname).toBe("/app/settings/general");
  });

  it("keeps hash-backed section switches on the current page", async () => {
    window.history.replaceState({}, "", "/canvas?projectId=p1#settings/account");

    render(<SettingsCenterSheet open section="account" urlMode="hash" />);

    fireEvent.click(screen.getByRole("button", { name: "General" }));

    expect(await screen.findByText("General settings content")).toBeInTheDocument();
    expect(window.location.pathname).toBe("/canvas");
    expect(window.location.search).toBe("?projectId=p1");
    expect(window.location.hash).toBe("#settings/general");
  });

  it("opens model credentials in their own settings section", async () => {
    render(<SettingsCenterSheet open section="account" urlMode="hash" />);

    fireEvent.click(screen.getByRole("button", { name: "Models" }));

    expect(await screen.findByText("Model provider settings content")).toBeInTheDocument();
    expect(window.location.hash).toBe("#settings/models");
  });

  it("opens MCP plugins and Skills from one settings entry", async () => {
    render(<SettingsCenterSheet open section="account" urlMode="hash" />);
    fireEvent.click(screen.getByRole("button", { name: "Plugins" }));
    expect(await screen.findByText("Agent plugins settings content")).toBeInTheDocument();
    expect(window.location.hash).toBe("#settings/plugins");
  });
});
