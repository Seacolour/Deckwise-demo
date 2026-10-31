import { describe, expect, it } from "vitest";
import {
  isLogoutLandingPath,
  resolveExplicitLogoutRedirectPath,
  resolveLogoutRedirectPath,
} from "@/src/lib/auth/logoutRedirect";

describe("logoutRedirect", () => {
  it("returns the login page for web sessions", () => {
    expect(resolveLogoutRedirectPath()).toBe("/login");
  });

  it("accepts only the login landing path", () => {
    expect(resolveExplicitLogoutRedirectPath("/login")).toBe("/login");
    expect(resolveExplicitLogoutRedirectPath("/")).toBeNull();
    expect(resolveExplicitLogoutRedirectPath("/desktop")).toBeNull();
    expect(isLogoutLandingPath("/login")).toBe(true);
    expect(isLogoutLandingPath("/")).toBe(false);
  });
});
