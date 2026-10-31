"use client";

import { normalizeProjectFileLookupName } from "@/src/lib/files/projectFileName";
import { getUserKey } from "@/src/stores/auth";
import { idbEnforceLimits, idbGet, idbPut, idbScanPrefix, type DerivedAssetRecordBase } from "@/src/stores/derivedCache";

export type CachedUploadedFile = {
  type: "image" | "pdf" | "office" | "note" | "slides" | "sheet";
  cacheKey: string;
  url?: string;
  name: string;
  rawName?: string;
  previewImageUrl?: string | null;
  sourceId?: string;
  documentId?: string;
  sizeBytes?: number;
  width?: number;
  height?: number;
  aspectRatio?: number;
};

export type ProjectFilesCacheRecord = DerivedAssetRecordBase & {
  type: "projectFiles";
  // Mandatory tenant boundary inside one browser profile.
  // Without userKey, account A and B can share the same pfiles:<projectId> bucket.
  userKey: string;
  projectId: string;
  fetchedAt: number;
  files: CachedUploadedFile[];
};

export type CachedProjectFileMatch = {
  projectId: string;
  file: CachedUploadedFile;
};

export function projectFilesCacheKey(projectId: string, userKey: string = getUserKey()) {
  return `pfiles:${userKey}:${projectId}`;
}

export function projectFilesCachePrefix(userKey: string = getUserKey()) {
  return `pfiles:${userKey}:`;
}

export async function getProjectFilesCache(projectId: string): Promise<ProjectFilesCacheRecord | null> {
  const userKey = getUserKey();
  const rec = await idbGet<ProjectFilesCacheRecord>(projectFilesCacheKey(projectId, userKey));
  if (!rec || rec.type !== "projectFiles") return null;
  if (rec.projectId !== projectId) return null;
  if (rec.userKey !== userKey) return null;
  return rec;
}

export async function setProjectFilesCache(projectId: string, files: CachedUploadedFile[]) {
  const userKey = getUserKey();
  const now = Date.now();
  const rec: ProjectFilesCacheRecord = {
    key: projectFilesCacheKey(projectId, userKey),
    type: "projectFiles",
    userKey,
    projectId,
    fetchedAt: now,
    createdAt: now,
    updatedAt: now,
    sizeBytes: JSON.stringify(files).length,
    files,
  };
  await idbPut(rec);
  await enforceProjectFilesCacheLimits({ userKey });
}

export function isFresh(rec: ProjectFilesCacheRecord | null, ttlMs = 2 * 60 * 1000) {
  if (!rec) return false;
  return Date.now() - (rec.fetchedAt ?? 0) < ttlMs;
}

export async function enforceProjectFilesCacheLimits(opts?: {
  userKey?: string;
  maxItems?: number;
  maxBytes?: number;
}) {
  const userKey = opts?.userKey ?? getUserKey();
  await idbEnforceLimits({
    prefix: projectFilesCachePrefix(userKey),
    maxItems: opts?.maxItems ?? 40,
    maxBytes: opts?.maxBytes ?? 2 * 1024 * 1024,
    getUpdatedAt: (rec) => Number(rec?.updatedAt ?? (rec as ProjectFilesCacheRecord | null)?.fetchedAt ?? 0),
    getSizeBytes: (rec) => Number(rec?.sizeBytes ?? 0),
  });
}

function normalizeId(value?: string | null): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function normalizeName(value?: string | null): string | null {
  return normalizeProjectFileLookupName(value);
}

function findUniqueNameMatch(files: CachedUploadedFile[], fileName: string): CachedUploadedFile | null {
  const matches = files.filter((file) => normalizeName(file.name) === fileName);
  return matches.length === 1 ? matches[0] : null;
}

function findMatchInFiles(
  files: CachedUploadedFile[],
  opts: {
    sourceId: string | null;
    cacheKey: string | null;
    documentId: string | null;
    fileName: string | null;
    allowNameMatch: boolean;
  },
): CachedUploadedFile | null {
  const { sourceId, cacheKey, documentId, fileName, allowNameMatch } = opts;

  if (sourceId) {
    const match = files.find((file) => file.sourceId === sourceId);
    if (match) return match;
  }

  if (documentId) {
    const match = files.find((file) => file.documentId === documentId);
    if (match) return match;
  }

  if (cacheKey) {
    const match = files.find((file) => file.cacheKey === cacheKey);
    if (match) return match;
  }

  if (sourceId) {
    // Web briefly emitted cacheKey into the `fileId` field. Keep resolving those
    // legacy chips, but only after exhausting the real source/document identifiers.
    const legacyMatch = files.find((file) => file.cacheKey === sourceId);
    if (legacyMatch) return legacyMatch;
  }

  if (allowNameMatch && fileName) {
    const match = findUniqueNameMatch(files, fileName);
    if (match) return match;
  }

  return null;
}

export async function findCachedProjectFile(opts: {
  projectId?: string | null;
  sourceId?: string | null;
  cacheKey?: string | null;
  documentId?: string | null;
  fileName?: string | null;
}): Promise<CachedProjectFileMatch | null> {
  const userKey = getUserKey();
  const projectId = normalizeId(opts.projectId);
  const sourceId = normalizeId(opts.sourceId);
  const cacheKey = normalizeId(opts.cacheKey);
  const documentId = normalizeId(opts.documentId);
  const fileName = normalizeName(opts.fileName);

  if (!sourceId && !cacheKey && !documentId && !fileName) {
    return null;
  }

  if (projectId) {
    const projectCache = await getProjectFilesCache(projectId);
    const match = findMatchInFiles(projectCache?.files ?? [], {
      sourceId,
      cacheKey,
      documentId,
      fileName,
      allowNameMatch: true,
    });
    if (match) {
      return { projectId, file: match };
    }
  }

  if (!sourceId && !cacheKey && !documentId) {
    return null;
  }

  // Cross-project lookup is allowed, but it must stay inside the current user namespace.
  const caches = await idbScanPrefix<ProjectFilesCacheRecord>(`pfiles:${userKey}:`);
  for (const cache of caches) {
    if (!cache?.projectId || cache.projectId === projectId || cache.userKey !== userKey) {
      continue;
    }

    const match = findMatchInFiles(cache.files ?? [], {
      sourceId,
      cacheKey,
      documentId,
      fileName,
      allowNameMatch: false,
    });
    if (match) {
      return { projectId: cache.projectId, file: match };
    }
  }

  return null;
}
