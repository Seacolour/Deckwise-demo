"use client";

export type RecentFilePreviewRecord = {
  projectId: string;
  type: "note" | "slides";
  cacheKey: string | null;
  documentId: string | null;
  url: string;
  scrollTop: number;
  scrollLeft: number;
  updatedAt: number;
};

const MAX_RECENT_FILE_PREVIEW_ENTRIES = 24;
const recentFilePreview = new Map<string, RecentFilePreviewRecord>();
type RecentFilePreviewInput = Omit<RecentFilePreviewRecord, "updatedAt">;

function buildRecentFilePreviewKey(input: {
  projectId: string;
  type: RecentFilePreviewRecord["type"];
  cacheKey?: string | null;
  documentId?: string | null;
  url?: string | null;
}): string | null {
  const identity = input.cacheKey?.trim() || input.documentId?.trim() || input.url?.trim();
  if (!input.projectId || !identity) {
    return null;
  }

  return `${input.projectId}:${input.type}:${identity}`;
}

export function getRecentFilePreview(input: {
  projectId: string;
  type: RecentFilePreviewRecord["type"];
  cacheKey?: string | null;
  documentId?: string | null;
  url?: string | null;
}): RecentFilePreviewRecord | null {
  const key = buildRecentFilePreviewKey(input);
  if (!key) {
    return null;
  }

  const cached = recentFilePreview.get(key);
  if (!cached) {
    return null;
  }

  recentFilePreview.delete(key);
  recentFilePreview.set(key, cached);
  return cached;
}

export function rememberRecentFilePreview(
  input: RecentFilePreviewInput & { updatedAt?: number },
) {
  const key = buildRecentFilePreviewKey(input);
  if (!key) {
    return;
  }

  recentFilePreview.delete(key);
  const record = {
    ...input,
    updatedAt: input.updatedAt ?? Date.now(),
  };
  recentFilePreview.set(key, record);

  while (recentFilePreview.size > MAX_RECENT_FILE_PREVIEW_ENTRIES) {
    const oldestKey = recentFilePreview.keys().next().value;
    if (!oldestKey) {
      break;
    }
    recentFilePreview.delete(oldestKey);
  }
}

export function clearRecentFilePreview() {
  recentFilePreview.clear();
}
