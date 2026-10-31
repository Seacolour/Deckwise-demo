"use client";

import { apiFetch } from "@/src/lib/api/fetcher";
import { HttpStatus } from "@/src/lib/config/constants/api";
import { shouldTreatAsAuthError } from "@/src/lib/api/fetchers/error-handler";
import { isRequestTimeoutError } from "@/src/lib/api/requestError";
import { abortAuthSensitiveRequests } from "@/src/lib/api/requestRuntime";
import { safeStorageOperation } from "@/src/lib/utils/errorHandler";
import {
  buildLoginRedirectPath,
  resolveSafeRedirectTarget,
  RETURN_TO_STORAGE_KEY,
} from "@/src/lib/auth/loginRedirect";
import { resolveExplicitLogoutRedirectPath } from "@/src/lib/auth/logoutRedirect";
import {
  clearAuth,
  getAuthKey,
  LOGOUT_REDIRECT_STORAGE_KEY,
  saveSessionAuth,
  type AuthUser,
} from "@/src/stores/auth";

export const AUTH_PROBE_PATH = "/user/get/login";
const AUTH_LOGOUT_PATH = "/user/logout";
const AUTH_PROBE_TIMEOUT_MS = 8_000;
const AUTH_PROBE_SUCCESS_TTL_MS = 15_000;
const AUTH_PROBE_TRANSIENT_TTL_MS = 60_000;
const COOKIE_SESSION_PROBE_KEY = "cookie-session";

let probePromise: Promise<boolean> | null = null;
let probeToken: string | null = null;
let successfulProbeToken: string | null = null;
let successfulProbeAt = 0;
let transientProbeToken: string | null = null;
let transientProbeAt = 0;
let authRedirectInProgress = false;
let invalidatedAuthKey: string | null = null;

function isApiErrorLike(
  value: unknown,
): value is { code?: number; message?: string; payload?: unknown } {
  return typeof value === "object" && value !== null;
}

export function isAuthProbePath(path: string): boolean {
  if (path === AUTH_PROBE_PATH || path.startsWith(`${AUTH_PROBE_PATH}?`)) {
    return true;
  }

  try {
    return new URL(path, "http://localhost").pathname === AUTH_PROBE_PATH;
  } catch {
    return false;
  }
}

export function shouldBypassAuthRedirect(pathname: string): boolean {
  if (pathname === "/") {
    return true;
  }

  if (pathname.startsWith("/login")) {
    return true;
  }

  return false;
}

function clearSuccessfulProbeCache() {
  successfulProbeToken = null;
  successfulProbeAt = 0;
}

function clearTransientProbeCache() {
  transientProbeToken = null;
  transientProbeAt = 0;
}

function hasFreshSuccessfulProbe(token: string): boolean {
  return (
    successfulProbeToken === token &&
    successfulProbeAt > 0 &&
    Date.now() - successfulProbeAt < AUTH_PROBE_SUCCESS_TTL_MS
  );
}

function hasFreshTransientProbe(token: string): boolean {
  return (
    transientProbeToken === token &&
    transientProbeAt > 0 &&
    Date.now() - transientProbeAt < AUTH_PROBE_TRANSIENT_TTL_MS
  );
}

function rememberTransientProbeAllowance(token: string) {
  transientProbeToken = token;
  transientProbeAt = Date.now();
}

function isTooManyRequestsError(
  value: unknown,
): value is { code?: number; message?: string; payload?: unknown } {
  return isApiErrorLike(value) && value.code === HttpStatus.TOO_MANY_REQUESTS;
}

function getCurrentProbeKey(): string {
  return getAuthKey() ?? COOKIE_SESSION_PROBE_KEY;
}

function resetTransitionForCurrentAuth(currentAuthKey: string | null) {
  if (
    authRedirectInProgress &&
    currentAuthKey &&
    (!invalidatedAuthKey || currentAuthKey !== invalidatedAuthKey)
  ) {
    authRedirectInProgress = false;
    invalidatedAuthKey = null;
    clearSuccessfulProbeCache();
    clearTransientProbeCache();
  }
}

export function isAuthTransitionInProgress(): boolean {
  resetTransitionForCurrentAuth(getAuthKey());
  return authRedirectInProgress;
}

function getExplicitLogoutRedirect(): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return resolveExplicitLogoutRedirectPath(
      sessionStorage.getItem(LOGOUT_REDIRECT_STORAGE_KEY),
    );
  } catch {
    return null;
  }
}

function replaceForAuthFailure(loginPath: string) {
  if (typeof window === "undefined") {
    return;
  }

  // Explicit sign-out owns navigation. Late auth failures from in-flight
  // requests must not overwrite the user-requested return to the public root.
  window.location.replace(getExplicitLogoutRedirect() ?? loginPath);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function rememberCookieSessionUser(value: unknown) {
  if (!isRecord(value)) {
    return;
  }
  saveSessionAuth(value as AuthUser);
}

function clearWebSessionCookie() {
  if (typeof window === "undefined") {
    return;
  }

  void Promise.resolve(apiFetch(AUTH_LOGOUT_PATH, {
    method: "POST",
    requestPolicy: "mutation",
    timeoutMs: 4_000,
    retries: 0,
    disableAuthHeader: true,
  })).catch(() => {
    // Best effort: an expired/invalid httpOnly cookie should be cleared by the
    // public logout endpoint, but auth recovery must not depend on this request.
  });
}

export async function ensureAuthProbe(): Promise<boolean> {
  const currentAuthKey = getAuthKey();
  const currentProbeKey = getCurrentProbeKey();
  resetTransitionForCurrentAuth(currentAuthKey);

  if (authRedirectInProgress) {
    return false;
  }

  if (hasFreshSuccessfulProbe(currentProbeKey)) {
    return true;
  }

  if (hasFreshTransientProbe(currentProbeKey)) {
    return true;
  }

  if (probePromise && probeToken === currentProbeKey) {
    return probePromise;
  }

  probeToken = currentProbeKey;
  const runningProbe = apiFetch<unknown>(AUTH_PROBE_PATH, {
    requestPolicy: "query",
    timeoutMs: AUTH_PROBE_TIMEOUT_MS,
    retries: 0,
  })
    .then((user) => {
      if (getAuthKey() === currentAuthKey) {
        rememberCookieSessionUser(user);
        successfulProbeToken = getCurrentProbeKey();
        successfulProbeAt = Date.now();
        clearTransientProbeCache();
      }
      return true;
    })
    .catch((error) => {
      if (
        isApiErrorLike(error) &&
        shouldTreatAsAuthError({
          code: error.code,
          message: error.message,
          payload: error.payload,
        })
      ) {
        clearSuccessfulProbeCache();
        clearTransientProbeCache();
        return false;
      }

      if (getAuthKey() === currentAuthKey) {
        rememberTransientProbeAllowance(currentProbeKey);
      }

      // Probe exists to block auth-error storms, not to add a new single point
      // of failure for unrelated transient issues. When the probe itself is
      // throttled (429), hold a short grace window so route changes do not
      // immediately hammer the same endpoint again.
      if (isTooManyRequestsError(error)) {
        console.warn(
          "[authProbe] Probe request was rate-limited, temporarily suppressing re-probes",
          error,
        );
      } else if (isRequestTimeoutError(error)) {
        // Pitfall record:
        // the auth probe should prevent auth-error storms, not manufacture
        // noisy false negatives when the backend is merely slow. Keep a longer
        // transient allowance and downgrade timeout logging so route changes do
        // not keep re-probing while the session is otherwise usable.
        console.warn("[authProbe] Probe timed out, temporarily trusting the existing session", error);
      } else {
        console.warn("[authProbe] Probe request failed, allowing app to continue", error);
      }
      return true;
    })
    .finally(() => {
      if (probePromise === runningProbe) {
        probePromise = null;
        probeToken = null;
      }
    });

  probePromise = runningProbe;
  return runningProbe;
}

export function triggerAuthFailureRedirect(opts?: {
  pathname?: string | null;
  returnTo?: string | null;
}) {
  const currentAuthKey = getAuthKey();
  resetTransitionForCurrentAuth(currentAuthKey);

  const pathname =
    opts?.pathname ??
    (typeof window !== "undefined" ? window.location.pathname : "");
  const shouldRedirect =
    typeof window !== "undefined" && !shouldBypassAuthRedirect(pathname);
  const returnTo =
    opts?.returnTo ??
    (typeof window !== "undefined" ? window.location.href : null);
  const safeReturnTo = shouldRedirect ? resolveSafeRedirectTarget(returnTo) : null;
  const loginPath = buildLoginRedirectPath(safeReturnTo);

  if (authRedirectInProgress && (!currentAuthKey || currentAuthKey === invalidatedAuthKey)) {
    if (shouldRedirect) {
      const logoutRedirect = getExplicitLogoutRedirect();
      if (safeReturnTo && !logoutRedirect) {
        safeStorageOperation(() => {
          sessionStorage.setItem(RETURN_TO_STORAGE_KEY, safeReturnTo);
        }, "sessionStorage", "setItem");
      }
      replaceForAuthFailure(loginPath);
    }
    return;
  }

  const logoutRedirect = getExplicitLogoutRedirect();
  authRedirectInProgress = true;
  invalidatedAuthKey = currentAuthKey;
  clearSuccessfulProbeCache();
  clearTransientProbeCache();
  abortAuthSensitiveRequests("Authentication became invalid.");

  clearWebSessionCookie();
  clearAuth();

  if (typeof window !== "undefined" && !logoutRedirect) {
    if (safeReturnTo) {
      safeStorageOperation(() => {
        sessionStorage.setItem(RETURN_TO_STORAGE_KEY, safeReturnTo);
      }, "sessionStorage", "setItem");
    }
  }

  if (shouldRedirect) {
    replaceForAuthFailure(loginPath);
  }
}
