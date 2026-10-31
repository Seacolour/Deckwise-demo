// @vitest-environment jsdom

import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const AUTH_CHANGE_EVENT = "deckwise:auth-change";

const mockState = vi.hoisted(() => ({
  pathname: "/login",
  searchParams: new URLSearchParams(),
  routerReplace: vi.fn(),
  ensureAuthProbe: vi.fn(),
  triggerAuthFailureRedirect: vi.fn(),
  useAutoDailyCheckin: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mockState.pathname,
  useSearchParams: () => mockState.searchParams,
}));

vi.mock("@/src/lib/routing/navigation", () => ({
  useSafeAppRouter: () => ({
    replace: mockState.routerReplace,
  }),
}));

vi.mock("@/src/lib/auth/authProbe", () => ({
  ensureAuthProbe: (...args: unknown[]) => mockState.ensureAuthProbe(...args),
  triggerAuthFailureRedirect: (...args: unknown[]) =>
    mockState.triggerAuthFailureRedirect(...args),
}));

vi.mock("@/src/lib/checkin/useAutoDailyCheckin", () => ({
  useAutoDailyCheckin: (...args: unknown[]) => mockState.useAutoDailyCheckin(...args),
}));

vi.mock("@/src/stores/cacheCleanup", () => ({
  clearDerivedCachePartitionsForUsers: vi.fn(),
  clearLegacyDerivedCaches: vi.fn(),
}));

import AuthGate from "@/app/gate/AuthGate";
import { saveAuth, saveSessionAuth, writeEnvSignature } from "@/src/stores/auth";

describe("AuthGate", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    mockState.pathname = "/login";
    mockState.searchParams = new URLSearchParams();
    mockState.routerReplace.mockReset();
    mockState.useAutoDailyCheckin.mockReset();
    mockState.ensureAuthProbe.mockReset().mockResolvedValue(false);
    mockState.triggerAuthFailureRedirect.mockReset().mockImplementation(() => {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.dispatchEvent(new Event(AUTH_CHANGE_EVENT));
    });
  });

  it("shows the login page again after clearing an invalid stored token on /login", async () => {
    saveAuth({
      userToken: "stale-token",
      user: { id: "user-1" },
    });
    writeEnvSignature();

    render(
      <AuthGate>
        <div>Login UI</div>
      </AuthGate>,
    );

    await waitFor(() => {
      expect(mockState.ensureAuthProbe).toHaveBeenCalledTimes(1);
    });

    await waitFor(() => {
      expect(screen.getByText("Login UI")).toBeInTheDocument();
    });

    expect(mockState.triggerAuthFailureRedirect).toHaveBeenCalledWith({
      pathname: "/login",
    });
  });

  it("probes a cookie-only protected session before redirecting to login", async () => {
    mockState.pathname = "/app";
    mockState.ensureAuthProbe.mockImplementation(async () => {
      saveSessionAuth({ id: "user-1", email: "demo@example.com" });
      return true;
    });
    writeEnvSignature();

    render(
      <AuthGate>
        <div>App UI</div>
      </AuthGate>,
    );

    await waitFor(() => {
      expect(screen.getByText("App UI")).toBeInTheDocument();
    });

    expect(mockState.ensureAuthProbe).toHaveBeenCalled();
    expect(mockState.triggerAuthFailureRedirect).not.toHaveBeenCalled();
    expect(mockState.routerReplace).not.toHaveBeenCalledWith("/login");
  });

  it("preserves the protected URL when an environment switch sends the user to login", async () => {
    mockState.pathname = "/app";
    mockState.searchParams = new URLSearchParams("plan=pro");
    saveSessionAuth({ id: "user-1", email: "demo@example.com" });

    render(
      <AuthGate>
        <div>App UI</div>
      </AuthGate>,
    );

    await waitFor(() => {
      expect(mockState.routerReplace).toHaveBeenCalledWith(
        "/login?redirectUrl=%2Fapp%3Fplan%3Dpro",
      );
    });
  });

  it("sends authenticated root visitors into the app", async () => {
    mockState.pathname = "/";
    mockState.searchParams = new URLSearchParams("index=1");
    mockState.ensureAuthProbe.mockResolvedValue(true);
    saveSessionAuth({ id: "user-1", email: "demo@example.com" });
    writeEnvSignature();

    render(
      <AuthGate>
        <div>Entry UI</div>
      </AuthGate>,
    );

    await waitFor(() => {
      expect(mockState.routerReplace).toHaveBeenCalledWith("/app");
    });
  });
});
