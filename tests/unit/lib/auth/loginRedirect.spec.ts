import { describe, expect, it } from "vitest";
import {
  buildLoginRedirectPath,
  resolveLoginRedirectTarget,
  resolveSafeRedirectTarget,
} from "@/src/lib/auth/loginRedirect";

describe("login redirect helpers", () => {
  it("accepts relative app paths", () => {
    expect(resolveSafeRedirectTarget("/app?plan=pro#checkout")).toBe(
      "/app?plan=pro#checkout",
    );
  });

  it("normalizes same-origin absolute URLs to relative targets", () => {
    expect(
      resolveSafeRedirectTarget("https://example.com/app?plan=pro#checkout", {
        currentOrigin: "https://example.com",
      }),
    ).toBe("/app?plan=pro#checkout");
  });

  it("rejects cross-origin and protocol-relative targets", () => {
    expect(
      resolveSafeRedirectTarget("https://www.google.com", {
        currentOrigin: "https://example.com",
      }),
    ).toBeNull();
    expect(
      resolveSafeRedirectTarget("//www.google.com", {
        currentOrigin: "https://example.com",
      }),
    ).toBeNull();
  });

  it("does not recursively trust redirectUrl nested inside a safe app URL", () => {
    expect(
      resolveSafeRedirectTarget(
        "https://example.com/app?redirectUrl=https%3A%2F%2Fwww.google.com",
        { currentOrigin: "https://example.com" },
      ),
    ).toBe("/app?redirectUrl=https%3A%2F%2Fwww.google.com");
  });

  it("builds login URLs with a sanitized redirectUrl parameter", () => {
    expect(buildLoginRedirectPath("/workspace/project/project-1?tab=chat#message-2")).toBe(
      "/login?redirectUrl=%2Fworkspace%2Fproject%2Fproject-1%3Ftab%3Dchat%23message-2",
    );
    expect(
      buildLoginRedirectPath("https://www.google.com", {
        currentOrigin: "https://example.com",
      }),
    ).toBe("/login");
  });

  it("prefers URL redirectUrl over stored returnTo and falls back safely", () => {
    expect(
      resolveLoginRedirectTarget({
        search: `?redirectUrl=${encodeURIComponent("https://example.com/app?plan=pro")}`,
        storedReturnTo: "/workspace/project/project-1",
        currentOrigin: "https://example.com",
      }),
    ).toBe("/app?plan=pro");

    expect(
      resolveLoginRedirectTarget({
        search: `?redirectUrl=${encodeURIComponent("https://www.google.com")}`,
        storedReturnTo: "/workspace/project/project-1",
        currentOrigin: "https://example.com",
      }),
    ).toBe("/workspace/project/project-1");

    expect(
      resolveLoginRedirectTarget({
        search: `?redirectUrl=${encodeURIComponent("//www.google.com")}`,
        currentOrigin: "https://example.com",
      }),
    ).toBe("/app");
  });
});
