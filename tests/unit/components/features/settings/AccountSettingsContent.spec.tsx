// @vitest-environment jsdom

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  clearAuth: vi.fn(),
  deleteCurrentUser: vi.fn(),
  saveAuth: vi.fn(),
  saveCurrentUserName: vi.fn(),
}));

vi.mock("@/src/lib/api/generated/user-account", () => ({
  deleteCurrentUser: mocks.deleteCurrentUser,
}));

vi.mock("@/src/lib/api/userPersonalization", () => ({
  saveCurrentUserName: mocks.saveCurrentUserName,
}));

vi.mock("@/src/stores/auth", () => ({
  clearAuth: mocks.clearAuth,
  LOGOUT_REDIRECT_STORAGE_KEY: "auth:logout-redirect",
  saveAuth: mocks.saveAuth,
}));

import { AccountSettingsContent } from "@/src/components/features/settings/AccountSettingsContent";

describe("AccountSettingsContent", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.saveCurrentUserName.mockResolvedValue({ id: "u1", name: "New Name" });
  });

  it("renders the account profile without payment controls", async () => {
    render(
      <AccountSettingsContent
        profileName="colour Sea"
        rawUser={{ userToken: "token", user: { email: "demo@example.com", id: "u1", name: "colour Sea", avatarUrl: "https://legacy.example/avatar.png" } }}
      />,
    );

    expect(screen.getByDisplayValue("colour Sea")).toBeInTheDocument();
    expect(screen.getByText("demo@example.com")).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Upgrade" })).not.toBeInTheDocument();
    expect(screen.queryByText("978")).not.toBeInTheDocument();
    expect(screen.queryByText("User ID")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Copy" })).not.toBeInTheDocument();
  });

  it("saves the full name on blur", async () => {
    render(
      <AccountSettingsContent
        profileName="colour Sea"
        rawUser={{ userToken: "token", user: { id: "u1", name: "colour Sea" } }}
      />,
    );

    fireEvent.change(screen.getByLabelText("Full name"), { target: { value: "New Name" } });
    fireEvent.blur(screen.getByLabelText("Full name"));

    await waitFor(() => expect(mocks.saveCurrentUserName).toHaveBeenCalledWith("New Name"));
    expect(mocks.saveAuth).toHaveBeenCalledWith({
      userToken: "token",
      user: { id: "u1", name: "New Name" },
    });

  });
});
