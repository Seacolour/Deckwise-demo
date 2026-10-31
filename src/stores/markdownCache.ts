"use client";

import { getUserKey } from "@/src/stores/authUserKey";
import {
  idbDelete,
  idbDeletePrefix,
  idbEnforceLimits,
  idbGet,
  idbPut,
  type DerivedAssetRecordBase,
} from "@/src/stores/derivedCache";

export type MarkdownCacheRecord = DerivedAssetRecordBase & {
  type: "markdownCache";
  userKey: string;
  projectId: string;
  cacheKey: string;
  url: string;
  content: string;
  documentId?: string | null;
  version?: number | null;
  fetchedAt: number;
};

const MARKDOWN_CACHE_MAX_ITEMS_PER_USER = 64;
const MARKDOWN_CACHE_MAX_BYTES_PER_USER = 8 * 1024 * 1024;

function legacyMarkdownCacheKey(projectId: string, cacheKey: string) {
  return `mdcache:${projectId}:${cacheKey}`;
}

export function markdownCacheKey(
  projectId: string,
  cacheKey: string,
  userKey: string = getUserKey(),
) {
  return `mdcache:${userKey}:${projectId}:${cacheKey}`;
}

export function markdownCachePrefix(userKey: string = getUserKey()) {
  return `mdcache:${userKey}:`;
}

export async function getMarkdownCache(projectId: string, cacheKey: string): Promise<MarkdownCacheRecord | null> {
  const userKey = getUserKey();
  const rec = await idbGet<MarkdownCacheRecord>(markdownCacheKey(projectId, cacheKey, userKey));
  if (!rec || rec.type !== "markdownCache") return null;
  if (rec.userKey !== userKey) return null;
  if (rec.projectId !== projectId || rec.cacheKey !== cacheKey) return null;
  return rec;
}

function estimateMarkdownCacheSizeBytes(rec: {
  projectId: string;
  cacheKey: string;
  url: string;
  content: string;
  documentId?: string | null;
  version?: number | null;
}) {
  return JSON.stringify({
    projectId: rec.projectId,
    cacheKey: rec.cacheKey,
    url: rec.url,
    content: rec.content,
    documentId: rec.documentId ?? null,
    version: rec.version ?? null,
  }).length;
}

export async function upsertMarkdownCache(input: {
  projectId: string;
  cacheKey: string;
  url: string;
  content: string;
  documentId?: string | null;
  version?: number | null;
  fetchedAt?: number;
}) {
  const userKey = getUserKey();
  const now = Date.now();
  const rec: MarkdownCacheRecord = {
    key: markdownCacheKey(input.projectId, input.cacheKey, userKey),
    type: "markdownCache",
    userKey,
    projectId: input.projectId,
    cacheKey: input.cacheKey,
    url: input.url,
    content: input.content,
    documentId: input.documentId ?? null,
    version: input.version ?? null,
    fetchedAt: input.fetchedAt ?? now,
    createdAt: now,
    updatedAt: now,
    sizeBytes: estimateMarkdownCacheSizeBytes(input),
  };
  await idbPut(rec);
  const legacyKey = legacyMarkdownCacheKey(input.projectId, input.cacheKey);
  if (legacyKey !== rec.key) {
    await idbDelete(legacyKey).catch(() => {
      // Best-effort cleanup for the old, unscoped cache layout.
    });
  }
  await enforceMarkdownCacheLimits({ userKey });
  return rec;
}

export async function clearMarkdownCacheForUser(userKey: string) {
  await idbDeletePrefix(markdownCachePrefix(userKey));
}

export async function clearLegacyMarkdownCache() {
  await idbDeletePrefix("mdcache:", {
    filter: (rec) => rec.type === "markdownCache" && typeof (rec as Partial<MarkdownCacheRecord>).userKey !== "string",
  });
}

export async function enforceMarkdownCacheLimits(opts?: {
  userKey?: string;
  maxItems?: number;
  maxBytes?: number;
}) {
  const userKey = opts?.userKey ?? getUserKey();
  await idbEnforceLimits({
    prefix: markdownCachePrefix(userKey),
    maxItems: opts?.maxItems ?? MARKDOWN_CACHE_MAX_ITEMS_PER_USER,
    maxBytes: opts?.maxBytes ?? MARKDOWN_CACHE_MAX_BYTES_PER_USER,
    getUpdatedAt: (rec) => Number(rec?.updatedAt ?? (rec as MarkdownCacheRecord | null)?.fetchedAt ?? 0),
    getSizeBytes: (rec) => Number(rec?.sizeBytes ?? 0),
  });
}

export function shouldRefetchMarkdownCache(rec: MarkdownCacheRecord | null, opts?: { ttlMs?: number }) {
  if (!rec) return true;
  const ttlMs = opts?.ttlMs ?? 10 * 60 * 1000; // 10min default
  return Date.now() - (rec.fetchedAt ?? 0) > ttlMs;
}
