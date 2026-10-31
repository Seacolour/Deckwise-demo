// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";
import { LocalStorageKeys } from "@/src/lib/config/constants/storage";
import {
  clearAuth,
  getAuthKey,
  getToken,
  saveAuth,
  TOKEN_STORAGE_KEY,
  USER_STORAGE_KEY,
} from "@/src/stores/auth";

describe("auth store cookie-mode persistence", () => {
  beforeEach(() => {
    localStorage.clear();
    delete window.deckwiseDesktop;
  });

  it("stores only user snapshot data and strips JS-readable auth tokens", () => {
    saveAuth({
      appAccountToken: "app-secret",
      userToken: "bearer-secret",
      user: {
        id: "user-1",
        email: "demo@example.com",
        appAccountToken: "nested-secret",
      },
    });

    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(getToken()).toBeNull();
    expect(getAuthKey()).toBe("cookie:user-1");

    const storedUser = localStorage.getItem(USER_STORAGE_KEY);
    expect(storedUser).toBeTruthy();
    expect(storedUser).not.toContain("bearer-secret");
    expect(storedUser).not.toContain("app-secret");
    expect(storedUser).not.toContain("nested-secret");
  });

  it("clears desktop recovery and browser draft mirrors on logout", () => {
    const clearRecovery = vi.fn().mockResolvedValue(true);
    window.deckwiseDesktop = {
      isDesktop: true,
      clearRecovery,
      getRuntimeInfo: vi.fn(),
      openDesktopAuthUrl: vi.fn(),
    };
    saveAuth({ user: { id: "user-1" } });
    localStorage.setItem(
      `${LocalStorageKeys.CHAT_INPUT_DRAFT_PREFIX}project:v1`,
      "private draft",
    );

    clearAuth();

    expect(clearRecovery).toHaveBeenCalledWith({ userKey: "user-1" });
    expect(clearRecovery).toHaveBeenCalledWith({ userKey: "anon" });
    expect(localStorage.getItem(USER_STORAGE_KEY)).toBeNull();
    expect(
      Array.from({ length: localStorage.length }, (_, index) => localStorage.key(index))
        .some((key) => key?.startsWith(LocalStorageKeys.CHAT_INPUT_DRAFT_PREFIX)),
    ).toBe(false);
  });
});
