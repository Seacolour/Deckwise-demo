import { describe, expect, it } from "vitest";
import {
  SETTINGS_CENTER_SECTIONS,
  isSettingsSheetSection,
  resolveSettingsSheetSectionFromHash,
  resolveSettingsSheetSectionFromPath,
} from "@/src/lib/routing/settingsSheetRoutes";

describe("settings routes", () => {
  it("keeps only the workbench settings sections", () => {
    expect(SETTINGS_CENTER_SECTIONS).toEqual(["account", "general", "models", "plugins", "personalization"]);
    expect(isSettingsSheetSection("integrations")).toBe(false);
    expect(resolveSettingsSheetSectionFromPath("/app/settings/integrations")).toBeNull();
    expect(resolveSettingsSheetSectionFromHash("#settings/integrations")).toBe("account");
  });
});
