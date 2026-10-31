// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from "vitest";
import { LocalStorageKeys } from "@/src/lib/config/constants/storage";
import {
  deriveUserKey,
  getUserKey,
  resolveCurrentUser,
} from "@/src/stores/authUserKey";

describe("authUserKey", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("derives cache partitions from the nested persisted login shape", () => {
    const persisted = { user: { id: " user-42 ", email: "ignored@example.com" } };
    expect(resolveCurrentUser(persisted)).toEqual(persisted.user);
    expect(deriveUserKey(persisted)).toBe("user-42");
  });

  it("uses a normalized email fallback without importing the auth lifecycle", () => {
    localStorage.setItem(LocalStorageKeys.USER, JSON.stringify({
      user: { email: " Demo@Example.COM " },
    }));

    expect(getUserKey()).toBe("demo@example.com");
  });
});
