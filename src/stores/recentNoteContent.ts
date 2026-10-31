"use client";

import type { TextDocumentKind } from "@/src/lib/files/projectFileKinds";

export type RecentNoteContentRecord = {
  projectId: string;
  cacheKey: string;
  kind: TextDocumentKind;
  name: string | null;
  content: string;
  url: string | null;
  documentId: string | null;
  version: number | null;
  updatedAt: number;
};

const MAX_RECENT_NOTE_ENTRIES = 24;
const recentNoteContent = new Map<string, RecentNoteContentRecord>();

function buildRecentNoteKey(projectId: string, cacheKey: string) {
  return `${projectId}:${cacheKey}`;
}

export function getRecentNoteContent(projectId: string, cacheKey: string): RecentNoteContentRecord | null {
  const key = buildRecentNoteKey(projectId, cacheKey);
  const cached = recentNoteContent.get(key);
  if (!cached) {
    return null;
  }

  recentNoteContent.delete(key);
  recentNoteContent.set(key, cached);
  return cached;
}

export function removeRecentNoteContent(projectId: string, cacheKey: string) {
  recentNoteContent.delete(buildRecentNoteKey(projectId, cacheKey));
}

export function rememberRecentNoteContent(
  input: Omit<RecentNoteContentRecord, "updatedAt"> & { updatedAt?: number },
) {
  const key = buildRecentNoteKey(input.projectId, input.cacheKey);
  recentNoteContent.delete(key);
  recentNoteContent.set(key, {
    ...input,
    updatedAt: input.updatedAt ?? Date.now(),
  });

  while (recentNoteContent.size > MAX_RECENT_NOTE_ENTRIES) {
    const oldestKey = recentNoteContent.keys().next().value;
    if (!oldestKey) {
      break;
    }
    recentNoteContent.delete(oldestKey);
  }
}

export function clearRecentNoteContent() {
  recentNoteContent.clear();
}
