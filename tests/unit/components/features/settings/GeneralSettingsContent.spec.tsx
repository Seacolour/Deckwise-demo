// @vitest-environment jsdom

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  activeLocale: "en",
  isLocalePending: false,
  selectLocale: vi.fn(),
  setThemePreference: vi.fn(),
  themePreference: "system" as const,
}));

vi.mock("@/src/components/_shared/theme/useThemePreference", () => ({
  useThemePreference: () => ({
    resolvedTheme: "light",
    setThemePreference: mocks.setThemePreference,
    themePreference: mocks.themePreference,
  }),
}));

vi.mock("@/src/lib/i18n/useLocaleSwitcher", () => ({
  useLocaleSwitcher: () => ({
    activeLocale: mocks.activeLocale,
    isLocalePending: mocks.isLocalePending,
    languageLabel: "Language",
    localeOptions: [
      { label: "English", value: "en" },
      { label: "简体中文", value: "zh" },
    ],
    selectLocale: mocks.selectLocale,
  }),
}));

import { GeneralSettingsContent } from "@/src/components/features/settings/GeneralSettingsContent";

describe("GeneralSettingsContent", () => {
  beforeEach(() => {
    localStorage.clear();
    mocks.activeLocale = "en";
    mocks.isLocalePending = false;
    mocks.selectLocale.mockReset();
    mocks.setThemePreference.mockReset();
  });

  it("shows supported appearance controls and updates the theme preference", async () => {
    const user = userEvent.setup();

    render(<GeneralSettingsContent />);

    expect(screen.getByText("Appearance")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Language: English" })).toBeInTheDocument();
    expect(screen.getByText("Theme")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Light" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Dark" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "System" })).toHaveAttribute("aria-pressed", "true");

    await user.click(screen.getByRole("button", { name: "Dark" }));

    expect(mocks.setThemePreference).toHaveBeenCalledWith("dark");

    await user.click(screen.getByRole("button", { name: "Language: English" }));
    await user.click(await screen.findByRole("menuitem", { name: "简体中文" }));

    expect(mocks.selectLocale).toHaveBeenCalledWith("zh");
  });

  it("does not render the removed outline review generation preference", () => {
    render(<GeneralSettingsContent />);

    expect(screen.queryByRole("switch", {
      name: "Review outline before generating slides",
    })).not.toBeInTheDocument();
  });
});
