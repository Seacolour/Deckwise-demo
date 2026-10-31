// @vitest-environment jsdom

import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockState = vi.hoisted(() => ({
  routerReplace: vi.fn(),
}));

vi.mock("@/src/lib/routing/navigation", () => ({
  useSafeAppRouter: () => ({
    replace: mockState.routerReplace,
  }),
}));

import { useRedirectAfterLogin } from "@/app/login/useRedirectAfterLogin";

describe("useRedirectAfterLogin", () => {
  beforeEach(() => {
    sessionStorage.clear();
    mockState.routerReplace.mockReset();
    window.history.replaceState({}, "", "/login");
  });

  it("prefers redirectUrl from the login URL", async () => {
    sessionStorage.setItem("returnTo", "/workspace/project/project-1");
    window.history.replaceState(
      {},
      "",
      `/login?redirectUrl=${encodeURIComponent("/app?plan=pro")}`,
    );

    const { result } = renderHook(() => useRedirectAfterLogin());
    await act(async () => {
      await result.current();
    });

    expect(mockState.routerReplace).toHaveBeenCalledWith("/app?plan=pro");
    expect(sessionStorage.getItem("returnTo")).toBeNull();
  });

  it("uses stored returnTo when redirectUrl is unsafe", async () => {
    sessionStorage.setItem("returnTo", "/workspace/project/project-1");
    window.history.replaceState(
      {},
      "",
      `/login?redirectUrl=${encodeURIComponent("https://www.google.com")}`,
    );

    const { result } = renderHook(() => useRedirectAfterLogin());
    await act(async () => {
      await result.current();
    });

    expect(mockState.routerReplace).toHaveBeenCalledWith("/workspace/project/project-1");
    expect(sessionStorage.getItem("returnTo")).toBeNull();
  });

  it("normalizes same-origin absolute redirectUrl values", async () => {
    window.history.replaceState(
      {},
      "",
      `/login?redirectUrl=${encodeURIComponent(`${window.location.origin}/workspace/project/project-1`)}`,
    );

    const { result } = renderHook(() => useRedirectAfterLogin());
    await act(async () => {
      await result.current();
    });

    expect(mockState.routerReplace).toHaveBeenCalledWith("/workspace/project/project-1");
  });
});
