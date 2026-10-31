"use client";

import { apiFetch } from "@/src/lib/api/fetcher";
import { clearAuth } from "@/src/stores/auth";

const AUTH_LOGOUT_PATH = "/user/logout";

export async function logoutCurrentSession(): Promise<void> {
  try {
    await apiFetch(AUTH_LOGOUT_PATH, {
      method: "POST",
      requestPolicy: "mutation",
      timeoutMs: 5_000,
      retries: 0,
    });
  } catch {
    // Local logout must still proceed if the network is unavailable. The server
    // endpoint is public so stale httpOnly cookies can be cleared on the next try.
  } finally {
    clearAuth();
  }
}
