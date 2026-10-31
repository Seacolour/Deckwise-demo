"use client";

import { toHttpUrlOrNull } from "@/src/lib/utils/httpUrl";
import type {
  FileUploadFailedDetail,
  FileUploadStartedDetail,
  FileUploadSucceededDetail,
} from "@/src/lib/events/fileUploadProgress";
import type { ImageFile } from "./projectImageTypes";

type ProjectImageUploadEntry = ImageFile & {
  ownedObjectUrl?: string | null;
};

type ProjectImageUploadState = {
  entries: Map<string, ProjectImageUploadEntry>;
  snapshot: readonly ImageFile[];
};

type ResolvedSucceededImageUrls = {
  displayUrl: string | null;
  fullUrl: string | null;
  ownedObjectUrl: string | null;
  releasePendingObjectUrl: boolean;
};

const EMPTY_PROJECT_IMAGE_UPLOADS: readonly ImageFile[] = Object.freeze([]);
const projectImageUploadStates = new Map<string, ProjectImageUploadState>();
const projectImageUploadListeners = new Set<() => void>();

function emitProjectImageUploadChange() {
  projectImageUploadListeners.forEach((listener) => listener());
}

function getProjectImageUploadState(
  projectId: string,
  createIfMissing = false,
): ProjectImageUploadState | null {
  const existing = projectImageUploadStates.get(projectId);
  if (existing) {
    return existing;
  }

  if (!createIfMissing) {
    return null;
  }

  const nextState: ProjectImageUploadState = {
    entries: new Map(),
    snapshot: EMPTY_PROJECT_IMAGE_UPLOADS,
  };
  projectImageUploadStates.set(projectId, nextState);
  return nextState;
}

function revokeObjectUrl(url?: string | null) {
  if (!url || typeof URL === "undefined" || typeof URL.revokeObjectURL !== "function") {
    return;
  }

  URL.revokeObjectURL(url);
}

function createObjectUrl(file: File): string | null {
  if (typeof URL === "undefined" || typeof URL.createObjectURL !== "function") {
    return null;
  }

  return URL.createObjectURL(file);
}

function releaseEntryObjectUrl(entry: ProjectImageUploadEntry | undefined | null) {
  revokeObjectUrl(entry?.ownedObjectUrl ?? null);
}

function deleteEntry(
  state: ProjectImageUploadState,
  key: string,
  opts?: { releaseObjectUrl?: boolean },
): ProjectImageUploadEntry | null {
  const existing = state.entries.get(key);
  if (!existing) {
    return null;
  }

  state.entries.delete(key);
  if (opts?.releaseObjectUrl ?? true) {
    releaseEntryObjectUrl(existing);
  }
  return existing;
}

function buildSnapshotEntry(entry: ProjectImageUploadEntry): ImageFile {
  return {
    url: entry.url,
    fullUrl: entry.fullUrl,
    name: entry.name,
    cacheKey: entry.cacheKey,
    sourceId: entry.sourceId,
    documentId: entry.documentId,
    uploadedAt: entry.uploadedAt,
    width: entry.width,
    height: entry.height,
    aspectRatio: entry.aspectRatio,
    uploading: entry.uploading,
  };
}

function sortEntries(entries: readonly ProjectImageUploadEntry[]): ProjectImageUploadEntry[] {
  return [...entries].sort((a, b) => {
    const timeDelta = (b.uploadedAt ?? 0) - (a.uploadedAt ?? 0);
    if (timeDelta !== 0) {
      return timeDelta;
    }
    return a.cacheKey.localeCompare(b.cacheKey);
  });
}

function resolveSucceededImageUrls(
  pendingEntry: ProjectImageUploadEntry | null,
  detail: FileUploadSucceededDetail,
): ResolvedSucceededImageUrls {
  const remoteUrl = toHttpUrlOrNull(detail.file.url ?? null);
  const previewUrl = detail.file.previewUrl ?? null;
  const carriedObjectUrl = pendingEntry?.ownedObjectUrl ?? null;
  const fallbackObjectUrl =
    !remoteUrl && !carriedObjectUrl && detail.file.file
      ? createObjectUrl(detail.file.file)
      : null;

  return {
    // Prefer the canonical remote URL when it exists. Otherwise keep rendering
    // through the already-mounted blob URL so the wall tile does not visually
    // reload while the upload transitions from temporary to persisted.
    displayUrl: remoteUrl ?? carriedObjectUrl ?? fallbackObjectUrl ?? previewUrl,
    // `fullUrl` is the best non-stale source we know about for consumers that
    // may want the final asset or the freshest preview outside the wall tile.
    fullUrl: remoteUrl ?? previewUrl ?? carriedObjectUrl ?? fallbackObjectUrl,
    ownedObjectUrl: remoteUrl ? null : carriedObjectUrl ?? fallbackObjectUrl,
    releasePendingObjectUrl: Boolean(remoteUrl),
  };
}

function syncProjectImageUploadState(projectId: string) {
  const state = projectImageUploadStates.get(projectId);
  if (!state || state.entries.size === 0) {
    projectImageUploadStates.delete(projectId);
    emitProjectImageUploadChange();
    return;
  }

  state.snapshot = Object.freeze(
    sortEntries([...state.entries.values()]).map(buildSnapshotEntry),
  );
  emitProjectImageUploadChange();
}

export function subscribeProjectImageUploads(listener: () => void) {
  projectImageUploadListeners.add(listener);
  return () => {
    projectImageUploadListeners.delete(listener);
  };
}

export function readProjectImageUploads(projectId: string | null): readonly ImageFile[] {
  if (!projectId) {
    return EMPTY_PROJECT_IMAGE_UPLOADS;
  }

  return getProjectImageUploadState(projectId)?.snapshot ?? EMPTY_PROJECT_IMAGE_UPLOADS;
}

export function trackProjectImageUploadStarted(detail: FileUploadStartedDetail) {
  if (detail.type !== "image" || !detail.projectId || !detail.file) {
    return;
  }

  const objectUrl = createObjectUrl(detail.file);
  if (!objectUrl) {
    return;
  }

  const state = getProjectImageUploadState(detail.projectId, true);
  if (!state) {
    revokeObjectUrl(objectUrl);
    return;
  }

  deleteEntry(state, detail.tempCacheKey);
  state.entries.set(detail.tempCacheKey, {
    url: objectUrl,
    fullUrl: objectUrl,
    name: detail.name,
    cacheKey: detail.tempCacheKey,
    uploadedAt: Date.now(),
    uploading: true,
    ownedObjectUrl: objectUrl,
  });
  syncProjectImageUploadState(detail.projectId);
}

export function trackProjectImageUploadSucceeded(detail: FileUploadSucceededDetail) {
  if (detail.file.type !== "image" || !detail.projectId) {
    return;
  }

  const state = getProjectImageUploadState(detail.projectId, true);
  if (!state) {
    return;
  }

  const pendingEntry = deleteEntry(state, detail.tempCacheKey, { releaseObjectUrl: false });
  const {
    displayUrl,
    fullUrl,
    ownedObjectUrl,
    releasePendingObjectUrl,
  } = resolveSucceededImageUrls(pendingEntry, detail);

  if (releasePendingObjectUrl) {
    releaseEntryObjectUrl(pendingEntry);
  }

  if (!displayUrl) {
    syncProjectImageUploadState(detail.projectId);
    return;
  }

  deleteEntry(state, detail.file.cacheKey);
  state.entries.set(detail.file.cacheKey, {
    url: displayUrl,
    fullUrl: fullUrl ?? displayUrl,
    name: detail.file.name,
    cacheKey: detail.file.cacheKey,
    documentId: detail.file.documentId,
    uploadedAt: pendingEntry?.uploadedAt ?? Date.now(),
    uploading: false,
    ownedObjectUrl,
  });
  syncProjectImageUploadState(detail.projectId);
}

export function trackProjectImageUploadFailed(detail: FileUploadFailedDetail) {
  if (!detail.projectId) {
    return;
  }

  const state = getProjectImageUploadState(detail.projectId);
  if (!state) {
    return;
  }

  const removed = deleteEntry(state, detail.tempCacheKey);
  if (!removed) {
    return;
  }

  syncProjectImageUploadState(detail.projectId);
}

export function pruneCompletedProjectImageUploads(
  projectId: string | null,
  cacheKeys: Iterable<string>,
) {
  if (!projectId) {
    return;
  }

  const state = getProjectImageUploadState(projectId);
  if (!state) {
    return;
  }

  let changed = false;
  for (const cacheKey of cacheKeys) {
    const entry = state.entries.get(cacheKey);
    if (!entry || entry.uploading) {
      continue;
    }

    deleteEntry(state, cacheKey);
    changed = true;
  }

  if (changed) {
    syncProjectImageUploadState(projectId);
  }
}

export function clearProjectImageUploads() {
  for (const state of projectImageUploadStates.values()) {
    for (const entry of state.entries.values()) {
      releaseEntryObjectUrl(entry);
    }
  }

  projectImageUploadStates.clear();
  emitProjectImageUploadChange();
}
