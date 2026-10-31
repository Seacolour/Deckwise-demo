import { expect, test, vi } from "vitest";
import assert from "node:assert/strict";
import { ApiError, type ApiResp } from "@/src/lib/types/types";
import { BusinessErrorCodes, HttpStatus } from "@/src/lib/config/constants/api";
import { handleBusinessError, shouldTreatAsAuthError } from "@/src/lib/api/fetchers/error-handler";

test("shouldTreatAsAuthError keeps standard auth codes and user-not-found messages in the auth path", () => {
  assert.equal(shouldTreatAsAuthError({ code: HttpStatus.UNAUTHORIZED }), true);
  assert.equal(shouldTreatAsAuthError({ message: "用户不存在" }), true);
  assert.equal(shouldTreatAsAuthError({ payload: { error: { message: "user not found" } } }), true);
});

test("shouldTreatAsAuthError does not treat project-not-found as a logout case", () => {
  assert.equal(shouldTreatAsAuthError({ message: "项目不存在" }), false);
  assert.equal(shouldTreatAsAuthError({ payload: { message: "project not found" } }), false);
});

test("handleBusinessError normalizes user-not-found business errors to auth-failed", () => {
  const consoleWarnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);
  const payload: ApiResp<null> = {
    code: 90001,
    message: "用户不存在",
    data: null,
    timestamp: "2026-03-25T00:00:00.000Z",
  };

  assert.throws(
    () => handleBusinessError(payload, "/projects/329"),
    (error: unknown) =>
      error instanceof ApiError &&
      error.code === BusinessErrorCodes.AUTH_FAILED &&
      error.message === "用户不存在",
  );
  expect(consoleWarnSpy).toHaveBeenCalledWith(
    "[API AUTH REQUIRED]",
    expect.objectContaining({
      code: 90001,
      message: "用户不存在",
      url: "/projects/329",
    }),
  );
  consoleWarnSpy.mockRestore();
});
