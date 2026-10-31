"use client";

import { isRecord } from "@/src/lib/types/guards";

export type PersistedAuthUser = Record<string, unknown> & {
  user?: Record<string, unknown> | null;
  name?: string | null;
  userName?: string | null;
  username?: string | null;
  userAccount?: string | null;
  phone?: string | null;
  email?: string | null;
};

function readString(value: unknown): string | undefined {
  if (typeof value !== "string") {
    return undefined;
  }

  const normalized = value.trim();
  return normalized ? normalized : undefined;
}

function readFirstString(
  record: Record<string, unknown>,
  keys: string[],
): string | undefined {
  for (const key of keys) {
    const value = readString(record[key]);
    if (value) {
      return value;
    }
  }

  return undefined;
}

export function extractPersistedAuthUser(rawUser: unknown): PersistedAuthUser | null {
  if (!isRecord(rawUser)) {
    return null;
  }

  if (isRecord(rawUser.user)) {
    return rawUser.user as PersistedAuthUser;
  }

  return rawUser as PersistedAuthUser;
}

export function resolveUserDisplayName(
  rawUser: unknown,
  fallback: string,
): string {
  const user = extractPersistedAuthUser(rawUser);
  if (!user) {
    return fallback;
  }

  return (
    readFirstString(user, ["name", "userName", "username", "userAccount", "phone", "email"]) ??
    fallback
  );
}
