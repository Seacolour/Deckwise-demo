"use client";

import { idbDeletePrefix, idbEnforceLimits, idbGet, idbPut, type DerivedAssetRecordBase } from "@/src/stores/derivedCache";
import { getWorkbenchInlinePreviewPath, type WorkbenchFile } from "./workbenchApi";

type FileIdentity = { id: string; cacheKey: string };
type CachedFile = DerivedAssetRecordBase & {
  type: "workbenchFile";
  userKey: string;
  projectId: string;
  fileId: string;
  cacheKey: string;
  blob: Blob;
};

const MAX_PERSISTED_FILE_BYTES = 8 * 1024 * 1024;
const MAX_PERSISTED_BYTES_PER_USER = 40 * 1024 * 1024;
const MAX_MEMORY_BYTES = 50 * 1024 * 1024;
const MAX_ITEMS_PER_USER = 24;
const memory = new Map<string, Blob>();

function prefix(userKey: string) {
  return `wbfile:${userKey}:`;
}

function key(userKey: string, projectId: string, file: FileIdentity) {
  return `${prefix(userKey)}${projectId}:${file.id}:${file.cacheKey}`;
}

function remember(storageKey: string, blob: Blob) {
  memory.delete(storageKey);
  memory.set(storageKey, blob);
  let total = [...memory.values()].reduce((sum, item) => sum + item.size, 0);
  while (total > MAX_MEMORY_BYTES && memory.size > 0) {
    const oldest = memory.keys().next().value;
    if (!oldest) break;
    total -= memory.get(oldest)?.size ?? 0;
    memory.delete(oldest);
  }
}

export async function getCachedWorkbenchFile(userKey: string, projectId: string, file: FileIdentity): Promise<Blob | null> {
  if (!userKey || userKey === "anon") return null;
  const storageKey = key(userKey, projectId, file);
  const inMemory = memory.get(storageKey);
  if (inMemory) {
    remember(storageKey, inMemory);
    return inMemory;
  }
  try {
    const record = await idbGet<CachedFile>(storageKey);
    if (!record || record.type !== "workbenchFile" || record.userKey !== userKey
      || record.projectId !== projectId || record.fileId !== file.id || record.cacheKey !== file.cacheKey
      || !(record.blob instanceof Blob)) return null;
    remember(storageKey, record.blob);
    return record.blob;
  } catch {
    return null;
  }
}

export async function cacheWorkbenchFile(userKey: string, projectId: string, file: FileIdentity, blob: Blob): Promise<void> {
  if (!userKey || userKey === "anon" || !blob.size) return;
  const storageKey = key(userKey, projectId, file);
  remember(storageKey, blob);
  if (blob.size > MAX_PERSISTED_FILE_BYTES) return;
  const now = Date.now();
  try {
    await idbPut<CachedFile>({
      key: storageKey,
      type: "workbenchFile",
      userKey,
      projectId,
      fileId: file.id,
      cacheKey: file.cacheKey,
      blob,
      sizeBytes: blob.size,
      createdAt: now,
      updatedAt: now,
    });
    await idbEnforceLimits({
      prefix: prefix(userKey),
      maxItems: MAX_ITEMS_PER_USER,
      maxBytes: MAX_PERSISTED_BYTES_PER_USER,
    });
  } catch {
    // Browser storage is optional. The uploaded file remains in R2.
  }
}

export async function loadWorkbenchPreviewFile(
  userKey: string, projectId: string, file: WorkbenchFile, signal?: AbortSignal,
): Promise<Blob> {
  const cached = await getCachedWorkbenchFile(userKey, projectId, file);
  if (cached) return cached;
  const path = getWorkbenchInlinePreviewPath(projectId, file);
  if (!path) throw new Error("浏览器暂不支持预览此格式");
  const response = await fetch(path, { credentials: "same-origin", signal, cache: "no-store" });
  if (!response.ok) throw new Error(`文件预览失败（${response.status}）`);
  const blob = await response.blob();
  if (!blob.size) throw new Error("文件内容为空");
  void cacheWorkbenchFile(userKey, projectId, file, blob);
  return blob;
}

export async function clearWorkbenchFileCacheForUser(userKey: string): Promise<void> {
  for (const storageKey of memory.keys()) {
    if (storageKey.startsWith(prefix(userKey))) memory.delete(storageKey);
  }
  await idbDeletePrefix(prefix(userKey));
}
