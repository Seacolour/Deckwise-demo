"use client";

import { clearLegacyMarkdownCache } from "@/src/stores/markdownCache";
import { idbDelete, idbDeletePrefix } from "@/src/stores/derivedCache";
import { clearWorkbenchFileCacheForUser } from "@/src/features/workbench/workbenchFileCache";

function normalizeUserKey(userKey: string): string | null {
  const normalized = userKey.trim();
  return normalized ? normalized : null;
}

export async function clearDerivedCachePartitionsForUser(userKey: string) {
  const normalizedUserKey = normalizeUserKey(userKey);
  if (!normalizedUserKey) {
    return;
  }

  await Promise.allSettled([
    idbDelete(`projects:${normalizedUserKey}`),
    idbDeletePrefix(`sessions:${normalizedUserKey}:`),
    idbDeletePrefix(`pfiles:${normalizedUserKey}:`),
    idbDeletePrefix(`mdcache:${normalizedUserKey}:`),
    clearWorkbenchFileCacheForUser(normalizedUserKey),
  ]);
}

export async function clearDerivedCachePartitionsForUsers(userKeys: Iterable<string>) {
  for (const userKey of userKeys) {
    await clearDerivedCachePartitionsForUser(userKey);
  }
}

export async function clearLegacyDerivedCaches() {
  await clearLegacyMarkdownCache();
}
