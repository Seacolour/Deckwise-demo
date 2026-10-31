// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";

const mockState = vi.hoisted(() => ({
  apiFetch: vi.fn(),
  ensureAuthProbe: vi.fn(),
  isAuthProbePath: vi.fn(),
  isAuthTransitionInProgress: vi.fn(),
  shouldBypassAuthRedirect: vi.fn(),
  triggerAuthFailureRedirect: vi.fn(),
}));

vi.mock("@/src/lib/api/fetcher", () => ({
  apiFetch: (...args: unknown[]) => mockState.apiFetch(...args),
}));

vi.mock("@/src/lib/auth/authProbe", () => ({
  ensureAuthProbe: (...args: unknown[]) =>
    mockState.ensureAuthProbe(...args),
  isAuthProbePath: (...args: unknown[]) =>
    mockState.isAuthProbePath(...args),
  isAuthTransitionInProgress: (...args: unknown[]) =>
    mockState.isAuthTransitionInProgress(...args),
  shouldBypassAuthRedirect: (...args: unknown[]) =>
    mockState.shouldBypassAuthRedirect(...args),
  triggerAuthFailureRedirect: (...args: unknown[]) =>
    mockState.triggerAuthFailureRedirect(...args),
}));

import { apiFetchWithAuth } from "@/src/lib/api/fetcherWithAuth";

describe("apiFetchWithAuth", () => {
  beforeEach(() => {
    mockState.apiFetch.mockReset();
    mockState.ensureAuthProbe.mockReset().mockResolvedValue(true);
    mockState.isAuthProbePath.mockReset().mockImplementation((path: string) => (
      path === "/user/get/login" || path.startsWith("/user/get/login?")
    ));
    mockState.isAuthTransitionInProgress.mockReset().mockReturnValue(false);
    mockState.shouldBypassAuthRedirect.mockReset().mockReturnValue(false);
    mockState.triggerAuthFailureRedirect.mockReset();
    window.history.replaceState(
      {},
      "",
      "/workspace/project/project-1?tab=chat#message-2",
    );
  });

  it("runs the shared auth probe before protected requests", async () => {
    const order: string[] = [];
    mockState.ensureAuthProbe.mockImplementation(async () => {
      order.push("probe");
      return true;
    });
    mockState.apiFetch.mockImplementation(async () => {
      order.push("fetch");
      return "ok";
    });

    await expect(apiFetchWithAuth("/projects/list")).resolves.toBe("ok");

    expect(order).toEqual(["probe", "fetch"]);
    expect(mockState.ensureAuthProbe).toHaveBeenCalledTimes(1);
    expect(mockState.apiFetch).toHaveBeenCalledWith("/projects/list", undefined);
  });

  it("redirects without sending the target request when the auth probe fails", async () => {
    mockState.ensureAuthProbe.mockResolvedValue(false);

    void apiFetchWithAuth("/projects/list");
    await Promise.resolve();
    await Promise.resolve();

    expect(mockState.apiFetch).not.toHaveBeenCalled();
    expect(mockState.triggerAuthFailureRedirect).toHaveBeenCalledWith({
      pathname: "/workspace/project/project-1",
      returnTo: "/workspace/project/project-1?tab=chat#message-2",
    });
  });

  it("does not probe or fetch while an auth transition is already in progress", async () => {
    mockState.isAuthTransitionInProgress.mockReturnValue(true);

    void apiFetchWithAuth("/projects/list");
    await Promise.resolve();

    expect(mockState.ensureAuthProbe).not.toHaveBeenCalled();
    expect(mockState.apiFetch).not.toHaveBeenCalled();
  });

  it("skips the preflight probe for bypassed routes", async () => {
    mockState.shouldBypassAuthRedirect.mockReturnValue(true);
    mockState.apiFetch.mockResolvedValue("ok");

    await expect(apiFetchWithAuth("/projects/list")).resolves.toBe("ok");

    expect(mockState.ensureAuthProbe).not.toHaveBeenCalled();
    expect(mockState.apiFetch).toHaveBeenCalledTimes(1);
  });

  it("skips the preflight probe for auth-disabled requests", async () => {
    mockState.apiFetch.mockResolvedValue("ok");

    await expect(
      apiFetchWithAuth("/user/google_login", {
        disableAuthHeader: true,
        method: "POST",
      }),
    ).resolves.toBe("ok");

    expect(mockState.ensureAuthProbe).not.toHaveBeenCalled();
    expect(mockState.apiFetch).toHaveBeenCalledTimes(1);
  });

  it("skips the preflight probe for the probe endpoint itself", async () => {
    mockState.apiFetch.mockResolvedValue("ok");

    await expect(apiFetchWithAuth("/user/get/login")).resolves.toBe("ok");

    expect(mockState.ensureAuthProbe).not.toHaveBeenCalled();
    expect(mockState.apiFetch).toHaveBeenCalledTimes(1);
  });

  it("preserves the current relative location when redirecting after an auth failure", async () => {
    mockState.apiFetch.mockRejectedValue({
      code: 401,
      message: "unauthorized",
    });

    void apiFetchWithAuth("/projects/list");
    await Promise.resolve();
    await Promise.resolve();

    expect(mockState.triggerAuthFailureRedirect).toHaveBeenCalledWith({
      pathname: "/workspace/project/project-1",
      returnTo: "/workspace/project/project-1?tab=chat#message-2",
    });
  });
});
