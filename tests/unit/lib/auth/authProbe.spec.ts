import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiFetchMock = vi.fn();
const abortAuthSensitiveRequestsMock = vi.fn();
const clearAuthMock = vi.fn();
const saveSessionAuthMock = vi.fn();
let currentAuthKey: string | null = null;

vi.mock("@/src/lib/api/fetcher", () => ({
  apiFetch: (...args: unknown[]) => apiFetchMock(...args),
}));

vi.mock("@/src/lib/api/requestRuntime", () => ({
  abortAuthSensitiveRequests: (...args: unknown[]) =>
    abortAuthSensitiveRequestsMock(...args),
}));

vi.mock("@/src/lib/utils/errorHandler", () => ({
  safeStorageOperation: (fn: () => void) => fn(),
}));

vi.mock("@/src/stores/auth", () => ({
  clearAuth: () => clearAuthMock(),
  getAuthKey: () => currentAuthKey,
  LOGOUT_REDIRECT_STORAGE_KEY: "auth:logout-redirect",
  saveSessionAuth: (user: unknown) => {
    saveSessionAuthMock(user);
    currentAuthKey = "cookie:user-1";
  },
}));

async function loadAuthProbeModule() {
  vi.resetModules();
  return import("@/src/lib/auth/authProbe");
}

function installBrowserRedirectHarness(pathname = "/workspace/project/project-1") {
  const replaceMock = vi.fn();
  const storage = new Map<string, string>();

  vi.stubGlobal("window", {
    location: {
      href: `http://localhost:3000${pathname}`,
      origin: "http://localhost:3000",
      pathname,
      replace: replaceMock,
    },
  });
  vi.stubGlobal("sessionStorage", {
    getItem: (key: string) => storage.get(key) ?? null,
    removeItem: (key: string) => {
      storage.delete(key);
    },
    setItem: (key: string, value: string) => {
      storage.set(key, String(value));
    },
  });

  return { replaceMock, storage };
}

describe("ensureAuthProbe", () => {
  let warnSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-04-03T00:00:00.000Z"));
    apiFetchMock.mockReset();
    abortAuthSensitiveRequestsMock.mockReset();
    clearAuthMock.mockReset();
    saveSessionAuthMock.mockReset();
    currentAuthKey = "cookie:user-1";
    warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    warnSpy.mockRestore();
    vi.unstubAllGlobals();
    vi.useRealTimers();
    currentAuthKey = null;
  });

  it("coalesces concurrent probes for the same session", async () => {
    const { ensureAuthProbe } = await loadAuthProbeModule();
    let resolveProbe!: () => void;

    apiFetchMock.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        resolveProbe = resolve;
      }),
    );

    const firstProbe = ensureAuthProbe();
    const secondProbe = ensureAuthProbe();

    expect(apiFetchMock).toHaveBeenCalledTimes(1);

    resolveProbe();
    await expect(Promise.all([firstProbe, secondProbe])).resolves.toEqual([
      true,
      true,
    ]);
    expect(apiFetchMock).toHaveBeenCalledTimes(1);
  });

  it("temporarily suppresses repeated probes after a 429 response", async () => {
    const { ensureAuthProbe } = await loadAuthProbeModule();

    apiFetchMock.mockRejectedValueOnce({
      code: 429,
      message: "rate limited",
    });

    await expect(ensureAuthProbe()).resolves.toBe(true);
    expect(apiFetchMock).toHaveBeenCalledTimes(1);

    await expect(ensureAuthProbe()).resolves.toBe(true);
    expect(apiFetchMock).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(60_001);
    apiFetchMock.mockResolvedValueOnce({});

    await expect(ensureAuthProbe()).resolves.toBe(true);
    expect(apiFetchMock).toHaveBeenCalledTimes(2);
  });

  it("still returns false for auth failures and does not enter transient grace mode", async () => {
    const { ensureAuthProbe } = await loadAuthProbeModule();

    apiFetchMock.mockRejectedValue({
      code: 401,
      message: "unauthorized",
    });

    await expect(ensureAuthProbe()).resolves.toBe(false);
    await expect(ensureAuthProbe()).resolves.toBe(false);
    expect(apiFetchMock).toHaveBeenCalledTimes(2);
  });

  it("probes and stores a cookie-only session when no local user snapshot exists", async () => {
    currentAuthKey = null;
    const { ensureAuthProbe } = await loadAuthProbeModule();

    apiFetchMock.mockResolvedValueOnce({ id: "user-1", email: "demo@example.com" });

    await expect(ensureAuthProbe()).resolves.toBe(true);

    expect(apiFetchMock).toHaveBeenCalledWith("/user/get/login", {
      requestPolicy: "query",
      timeoutMs: 8_000,
      retries: 0,
    });
    expect(saveSessionAuthMock).toHaveBeenCalledWith({ id: "user-1", email: "demo@example.com" });
  });
});

describe("triggerAuthFailureRedirect", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
    abortAuthSensitiveRequestsMock.mockReset();
    clearAuthMock.mockReset();
    saveSessionAuthMock.mockReset();
    currentAuthKey = "cookie:user-1";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    currentAuthKey = null;
  });

  it("does not override explicit sign-out navigation with the login redirect", async () => {
    const { triggerAuthFailureRedirect } = await loadAuthProbeModule();
    const { replaceMock, storage } = installBrowserRedirectHarness();
    storage.set("auth:logout-redirect", "/login");

    triggerAuthFailureRedirect({
      pathname: "/workspace/project/project-1",
      returnTo: "/workspace/project/project-1",
    });

    expect(clearAuthMock).toHaveBeenCalledTimes(1);
    expect(abortAuthSensitiveRequestsMock).toHaveBeenCalledWith("Authentication became invalid.");
    expect(storage.get("returnTo")).toBeUndefined();
    expect(replaceMock).toHaveBeenCalledWith("/login");
  });

  it("stores returnTo and redirects to login for ordinary auth failures", async () => {
    const { triggerAuthFailureRedirect } = await loadAuthProbeModule();
    const { replaceMock, storage } = installBrowserRedirectHarness();

    triggerAuthFailureRedirect({
      pathname: "/workspace/project/project-1",
      returnTo: "/workspace/project/project-1",
    });

    expect(storage.get("returnTo")).toBe("/workspace/project/project-1");
    expect(replaceMock).toHaveBeenCalledWith(
      "/login?redirectUrl=%2Fworkspace%2Fproject%2Fproject-1",
    );
  });

  it("does not store the login page itself as a future return target", async () => {
    const { triggerAuthFailureRedirect } = await loadAuthProbeModule();
    const { replaceMock, storage } = installBrowserRedirectHarness("/login");

    triggerAuthFailureRedirect({
      pathname: "/login",
      returnTo: "/login?redirectUrl=%2Fapp",
    });

    expect(storage.get("returnTo")).toBeUndefined();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("redirects removed marketing routes to login", async () => {
    const { shouldBypassAuthRedirect, triggerAuthFailureRedirect } = await loadAuthProbeModule();
    const { replaceMock, storage } = installBrowserRedirectHarness("/zh/about");

    expect(shouldBypassAuthRedirect("/zh/about")).toBe(false);

    triggerAuthFailureRedirect({
      pathname: "/zh/about",
      returnTo: "/zh/about",
    });

    expect(storage.get("returnTo")).toBe("/zh/about");
    expect(replaceMock).toHaveBeenCalled();
  });

  it("normalizes same-origin absolute returnTo values before redirecting", async () => {
    const { triggerAuthFailureRedirect } = await loadAuthProbeModule();
    const { replaceMock, storage } = installBrowserRedirectHarness();

    triggerAuthFailureRedirect({
      pathname: "/workspace/project/project-1",
      returnTo: "http://localhost:3000/workspace/project/project-1?tab=chat#message-2",
    });

    expect(storage.get("returnTo")).toBe("/workspace/project/project-1?tab=chat#message-2");
    expect(replaceMock).toHaveBeenCalledWith(
      "/login?redirectUrl=%2Fworkspace%2Fproject%2Fproject-1%3Ftab%3Dchat%23message-2",
    );
  });
});
