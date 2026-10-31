// @vitest-environment jsdom

import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockState = vi.hoisted(() => ({
  apiFetchWithAuth: vi.fn(),
  saveAuth: vi.fn(),
  writeEnvSignature: vi.fn(),
}));

vi.mock("@/src/lib/api/fetcherWithAuth", () => ({
  apiFetchWithAuth: (...args: unknown[]) => mockState.apiFetchWithAuth(...args),
}));
vi.mock("@/src/lib/auth/clientLoginContext", () => ({
  buildClientLoginContext: () => ({ locale: "zh-CN", timeZone: "Asia/Shanghai" }),
}));
vi.mock("@/src/stores/auth", () => ({
  saveAuth: (...args: unknown[]) => mockState.saveAuth(...args),
  writeEnvSignature: (...args: unknown[]) => mockState.writeEnvSignature(...args),
}));

import { useEmailCodeAuth } from "@/app/login/useEmailCodeAuth";

const messages = {
  invalidEmail: "invalid email",
  missingCode: "missing code",
  emailCodeFailed: "send failed",
  emailLoginFailed: "login failed",
};

describe("useEmailCodeAuth", () => {
  beforeEach(() => {
    mockState.apiFetchWithAuth.mockReset();
    mockState.saveAuth.mockReset();
    mockState.writeEnvSignature.mockReset();
  });

  it("sends a code and signs in through the current email-only backend routes", async () => {
    const onDone = vi.fn();
    const onError = vi.fn();
    mockState.apiFetchWithAuth.mockResolvedValueOnce(true).mockResolvedValueOnce({
      user: { id: 7, email: "me@example.com" },
    });
    const { result } = renderHook(() => useEmailCodeAuth({ onDone, onError, locale: "zh", messages }));

    act(() => result.current.setEmail("me@example.com"));
    await act(async () => result.current.sendCode());
    expect(result.current.codeSent).toBe(true);
    act(() => result.current.setCode("123456"));
    await act(async () => result.current.signIn());

    expect(mockState.apiFetchWithAuth).toHaveBeenNthCalledWith(1, "/user/login/email-code", {
      method: "POST",
      body: { email: "me@example.com" },
      disableAuthHeader: true,
      requestPolicy: "mutation",
    });
    expect(mockState.apiFetchWithAuth).toHaveBeenNthCalledWith(2, "/user/login", {
      method: "POST",
      body: {
        email: "me@example.com",
        code: "123456",
        locale: "zh-CN",
        timeZone: "Asia/Shanghai",
      },
      disableAuthHeader: true,
      requestPolicy: "mutation",
    });
    expect(mockState.saveAuth).toHaveBeenCalledWith({ user: { id: 7, email: "me@example.com" } });
    expect(mockState.writeEnvSignature).toHaveBeenCalledOnce();
    expect(onDone).toHaveBeenCalledOnce();
    expect(onError).not.toHaveBeenCalled();
  });

  it("rejects an invalid email without calling the backend", async () => {
    const onError = vi.fn();
    const { result } = renderHook(() => useEmailCodeAuth({ onDone: vi.fn(), onError, messages }));
    act(() => result.current.setEmail("invalid"));
    await act(async () => result.current.sendCode());

    expect(onError).toHaveBeenCalledWith("invalid email");
    expect(mockState.apiFetchWithAuth).not.toHaveBeenCalled();
  });

  it("clears the sent-code state when the email address changes", async () => {
    mockState.apiFetchWithAuth.mockResolvedValueOnce(true);
    const { result } = renderHook(() => useEmailCodeAuth({ onDone: vi.fn(), onError: vi.fn(), messages }));
    act(() => result.current.setEmail("first@example.com"));
    await act(async () => result.current.sendCode());
    expect(result.current.codeSent).toBe(true);

    act(() => result.current.setEmail("second@example.com"));
    expect(result.current.codeSent).toBe(false);
    expect(result.current.code).toBe("");
  });
});
