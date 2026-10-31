// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PROFILE_MEMORY_TITLE, CUSTOM_INSTRUCTIONS_PREFIX } from "@/src/lib/api/userPersonalization";
import type { UserMemoryDTO, UserRuleDTO } from "@/src/lib/types/all";

const mocks = vi.hoisted(() => ({
  createUserMemory: vi.fn(),
  createUserRule: vi.fn(),
  deleteUserMemory: vi.fn(),
  deleteUserRule: vi.fn(),
  fetchUserPersonalization: vi.fn(),
  saveCurrentUserName: vi.fn(),
  saveCustomInstructionsRule: vi.fn(),
  saveProfileMemory: vi.fn(),
  updateRuleEnabled: vi.fn(),
  saveAuth: vi.fn(),
}));

vi.mock("@/src/stores/auth", () => ({
  saveAuth: mocks.saveAuth,
}));

vi.mock("@/src/lib/api/userPersonalization", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/src/lib/api/userPersonalization")>();

  return {
    ...actual,
    createUserMemory: mocks.createUserMemory,
    createUserRule: mocks.createUserRule,
    deleteUserMemory: mocks.deleteUserMemory,
    deleteUserRule: mocks.deleteUserRule,
    fetchUserPersonalization: mocks.fetchUserPersonalization,
    saveCurrentUserName: mocks.saveCurrentUserName,
    saveCustomInstructionsRule: mocks.saveCustomInstructionsRule,
    saveProfileMemory: mocks.saveProfileMemory,
    updateRuleEnabled: mocks.updateRuleEnabled,
  };
});

import { PersonalizationSettingsContent } from "@/src/components/features/settings/PersonalizationSettingsContent";

describe("PersonalizationSettingsContent", () => {
  const profileMemory: UserMemoryDTO = {
    id: "memory-profile",
    title: PROFILE_MEMORY_TITLE,
    content: "Occupation: Backend maintainer\n\nAbout:\nI work on PaperPuppy.",
    source: "user",
  };
  const customRule: UserRuleDTO = {
    id: "rule-custom",
    content: `${CUSTOM_INSTRUCTIONS_PREFIX}\nKeep answers concise.`,
    enabled: true,
    source: "user",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.fetchUserPersonalization.mockResolvedValue({
      memories: [
        profileMemory,
        { id: "memory-visible", title: "Deck style", content: "Prefer sober layouts.", source: "ai" },
      ],
      rules: [
        customRule,
        { id: "rule-visible", content: "Default to Chinese.", enabled: false, source: "user" },
      ],
    });
    mocks.saveCurrentUserName.mockResolvedValue({ id: "u1", name: "New Name" });
    mocks.saveProfileMemory.mockResolvedValue(profileMemory);
    mocks.saveCustomInstructionsRule.mockResolvedValue(customRule);
    mocks.createUserMemory.mockResolvedValue({ id: "memory-new", title: "Tone", content: "Direct", source: "user" });
    mocks.createUserRule.mockResolvedValue({ id: "rule-new", content: "Use Markdown.", enabled: true, source: "user" });
    mocks.deleteUserMemory.mockResolvedValue(true);
    mocks.deleteUserRule.mockResolvedValue(true);
    mocks.updateRuleEnabled.mockResolvedValue(true);
  });

  it("loads profile fields from user memories and rules, then saves through backend wrappers", async () => {
    render(
      <PersonalizationSettingsContent
        profileName="Demo User"
        rawUser={{ userToken: "token", user: { id: "u1", name: "Demo User" } }}
      />,
    );

    expect(await screen.findByDisplayValue("Backend maintainer")).toBeInTheDocument();
    expect(screen.getByDisplayValue("I work on PaperPuppy.")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Keep answers concise.")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Nickname"), { target: { value: "New Name" } });
    fireEvent.click(screen.getByRole("button", { name: /Save profile/i }));

    await waitFor(() => {
      expect(mocks.saveCurrentUserName).toHaveBeenCalledWith("New Name");
      expect(mocks.saveProfileMemory).toHaveBeenCalledWith(profileMemory, {
        about: "I work on PaperPuppy.",
        occupation: "Backend maintainer",
      });
      expect(mocks.saveCustomInstructionsRule).toHaveBeenCalledWith(customRule, "Keep answers concise.");
    });
    expect(mocks.saveAuth).toHaveBeenCalledWith({
      userToken: "token",
      user: { id: "u1", name: "New Name" },
    });
  });

  it("keeps reserved profile items out of the knowledge lists and can add visible items", async () => {
    render(<PersonalizationSettingsContent profileName="Demo User" rawUser={{}} />);

    fireEvent.click(await screen.findByRole("button", { name: "Knowledge" }));

    expect(screen.queryByText(PROFILE_MEMORY_TITLE)).not.toBeInTheDocument();
    expect(screen.queryByText(`${CUSTOM_INSTRUCTIONS_PREFIX}\nKeep answers concise.`)).not.toBeInTheDocument();
    expect(screen.getByText("Deck style")).toBeInTheDocument();
    expect(screen.getByText("Default to Chinese.")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Memory title"), { target: { value: "Tone" } });
    fireEvent.change(screen.getByPlaceholderText("Something Deckwise should remember"), { target: { value: "Direct" } });
    fireEvent.click(screen.getByRole("button", { name: "Add memory" }));

    await waitFor(() => expect(mocks.createUserMemory).toHaveBeenCalledWith("Tone", "Direct"));
    expect(await screen.findByText("Tone")).toBeInTheDocument();
  });
});
