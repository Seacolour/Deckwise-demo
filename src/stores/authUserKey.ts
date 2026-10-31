"use client";

import { LocalStorageKeys } from "@/src/lib/config/constants/storage";
import { getJSON } from "@/src/lib/storage/localStorageManager";
import { isObject } from "@/src/lib/types/guards";

/**
 * Minimal persisted-user model shared by auth and user-partitioned caches.
 * This module deliberately has no dependency on the auth store lifecycle, so
 * cache/storage adapters can derive their partition without importing auth.
 */
export type AuthUser = Record<string, unknown>;

export function resolveCurrentUser<T extends object = Record<string, unknown>>(
  raw: AuthUser | null,
): T | null {
  if (!isObject(raw)) return null;
  const nested = (raw as { user?: unknown }).user;
  if (isObject(nested)) return nested as T;
  return raw as T;
}

export function deriveUserKey(rawUser: AuthUser | null | undefined): string {
  const user = resolveCurrentUser<{ id?: unknown; email?: unknown }>(rawUser ?? null);
  const id = user?.id;
  if (id !== undefined && id !== null) {
    const normalizedId = String(id).trim();
    if (normalizedId) return normalizedId;
  }

  const email = user?.email;
  if (typeof email === "string") {
    const normalizedEmail = email.trim().toLowerCase();
    if (normalizedEmail) return normalizedEmail;
  }

  return "anon";
}

export function getUserKey(): string {
  return deriveUserKey(getJSON<AuthUser>(LocalStorageKeys.USER));
}
