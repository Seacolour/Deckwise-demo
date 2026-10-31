"use client";

import { makeTextFileName } from "@/src/lib/files/projectFileKinds";
import {
  deleteDesktopRecovery,
  listDesktopRecovery,
  saveDesktopRecovery,
} from "@/src/lib/desktop/desktopStorage";
import { getUserKey } from "./authUserKey";
import { idbDelete } from "./derivedCache";

export type LocalNoteProjectFileRecord = {
  key: string;
  type: "note";
  projectId: string;
  cacheKey: string;
  fileName: string;
  content: string;
  createdAt: number;
  updatedAt: number;
};

export type LocalTextProjectFileRecord = LocalNoteProjectFileRecord;

export type LocalProjectFileRecord = LocalTextProjectFileRecord;
export type LocalProjectFileUploadBinding = {
  cacheKey: string;
  documentId: string | null;
  url: string | null;
};

function localFileKeyPrefix(projectId: string) {
  return `local-file:${projectId}:`;
}

function buildLocalFileKey(projectId: string, cacheKey: string) {
  return `${localFileKeyPrefix(projectId)}${cacheKey}`;
}

function randomId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function cloneLocalProjectFileRecord<T extends LocalProjectFileRecord>(record: T): T {
  return { ...record };
}

const localProjectFilesMemory = new Map<string, LocalProjectFileRecord>();
const localProjectFileUploadBindingsMemory = new Map<string, LocalProjectFileUploadBinding>();
const desktopRecoveryHydrationPromises = new Map<string, Promise<void>>();
let activeLocalProjectFilesUserKey: string | null = null;

function cacheLocalProjectFileRecord(record: LocalProjectFileRecord) {
  localProjectFilesMemory.delete(record.key);
  localProjectFilesMemory.set(record.key, record);
}

function getLocalProjectFilesUserKey() {
  const userKey = getUserKey();
  if (
    activeLocalProjectFilesUserKey !== null
    && activeLocalProjectFilesUserKey !== userKey
  ) {
    localProjectFilesMemory.clear();
    localProjectFileUploadBindingsMemory.clear();
    desktopRecoveryHydrationPromises.clear();
  }
  activeLocalProjectFilesUserKey = userKey;
  return userKey;
}

function isRecoveredLocalProjectFileRecord(value: unknown): value is LocalProjectFileRecord {
  if (!value || typeof value !== "object") {
    return false;
  }
  const record = value as Partial<LocalProjectFileRecord>;
  return (
    record.type === "note"
    && typeof record.key === "string"
    && typeof record.projectId === "string"
    && typeof record.cacheKey === "string"
    && record.key === buildLocalFileKey(record.projectId, record.cacheKey)
    && typeof record.fileName === "string"
    && typeof record.content === "string"
    && typeof record.createdAt === "number"
    && Number.isFinite(record.createdAt)
    && typeof record.updatedAt === "number"
    && Number.isFinite(record.updatedAt)
  );
}

async function hydrateDesktopLocalProjectFiles() {
  const userKey = getLocalProjectFilesUserKey();
  const existing = desktopRecoveryHydrationPromises.get(userKey);
  if (existing) {
    await existing;
    return;
  }

  const hydration = (async () => {
    const records = await listDesktopRecovery<LocalProjectFileRecord>({
      kind: "notes",
      userKey,
    });
    for (const record of records) {
      if (isRecoveredLocalProjectFileRecord(record.payload)) {
        cacheLocalProjectFileRecord(record.payload);
      }
    }
  })();
  desktopRecoveryHydrationPromises.set(userKey, hydration);
  await hydration;
}

async function persistDesktopLocalProjectFile(record: LocalProjectFileRecord) {
  await saveDesktopRecovery({
    key: record.key,
    kind: "notes",
    payload: record,
    userKey: getLocalProjectFilesUserKey(),
  });
}

async function deleteDesktopLocalProjectFile(key: string) {
  await deleteDesktopRecovery({
    key,
    kind: "notes",
    userKey: getLocalProjectFilesUserKey(),
  });
}

export function __resetLocalProjectFilesForTests() {
  localProjectFilesMemory.clear();
  localProjectFileUploadBindingsMemory.clear();
  desktopRecoveryHydrationPromises.clear();
  activeLocalProjectFilesUserKey = null;
}

export function clearLocalProjectFilesMemory() {
  localProjectFilesMemory.clear();
  localProjectFileUploadBindingsMemory.clear();
  desktopRecoveryHydrationPromises.clear();
  activeLocalProjectFilesUserKey = null;
}

function buildLocalNoteTextFileRecord(
  projectId: string,
  key: string,
  cacheKey: string,
  fileName: string,
  createdAt: number,
  content?: string,
): LocalNoteProjectFileRecord {
  return {
    key,
    type: "note",
    projectId,
    cacheKey,
    fileName,
    content: content ?? "",
    createdAt,
    updatedAt: createdAt,
  };
}

export async function createLocalTextFile(
  projectId: string,
  opts: {
    kind: "note";
    content?: string;
    fileName?: string;
  },
): Promise<LocalNoteProjectFileRecord>;
export async function createLocalTextFile(
  projectId: string,
  opts: {
    kind: "note";
    content?: string;
    fileName?: string;
  },
): Promise<LocalNoteProjectFileRecord> {
  getLocalProjectFilesUserKey();
  const cacheKey = `local-${opts.kind}-${randomId()}`;
  const createdAt = Date.now();
  const key = buildLocalFileKey(projectId, cacheKey);
  const fileName = opts.fileName ?? makeTextFileName(opts.kind);
  const record = buildLocalNoteTextFileRecord(
    projectId,
    key,
    cacheKey,
    fileName,
    createdAt,
    opts.content,
  );

  cacheLocalProjectFileRecord(record);
  await persistDesktopLocalProjectFile(record);
  return record;
}

export async function getLocalProjectFile(projectId: string, cacheKey: string): Promise<LocalProjectFileRecord | null> {
  await hydrateDesktopLocalProjectFiles();
  const key = buildLocalFileKey(projectId, cacheKey);
  const record = localProjectFilesMemory.get(key);
  if (!record) {
    return null;
  }
  if (record.projectId !== projectId || record.cacheKey !== cacheKey) return null;
  cacheLocalProjectFileRecord(record);
  return cloneLocalProjectFileRecord(record);
}

export async function getLocalTextFile(projectId: string, cacheKey: string): Promise<LocalTextProjectFileRecord | null> {
  const record = await getLocalProjectFile(projectId, cacheKey);
  return record && record.type === "note" ? record : null;
}

export async function upsertLocalTextFileContent(projectId: string, cacheKey: string, content: string) {
  const existing = await getLocalTextFile(projectId, cacheKey);
  if (!existing) return;
  const updatedAt = Date.now();
  const next: LocalTextProjectFileRecord = { ...existing, content, updatedAt };
  cacheLocalProjectFileRecord(next);
  await persistDesktopLocalProjectFile(next);
}

export async function markLocalProjectFileUploaded(
  projectId: string,
  localCacheKey: string,
  remote: LocalProjectFileUploadBinding,
) {
  getLocalProjectFilesUserKey();
  if (!projectId || !localCacheKey || !remote.cacheKey) {
    return;
  }

  localProjectFileUploadBindingsMemory.set(
    buildLocalFileKey(projectId, localCacheKey),
    {
      cacheKey: remote.cacheKey,
      documentId: remote.documentId ?? null,
      url: remote.url ?? null,
    },
  );
  await deleteDesktopLocalProjectFile(buildLocalFileKey(projectId, localCacheKey));
}

export async function getLocalProjectFileUploadBinding(
  projectId: string,
  localCacheKey: string,
): Promise<LocalProjectFileUploadBinding | null> {
  getLocalProjectFilesUserKey();
  return (
    localProjectFileUploadBindingsMemory.get(
      buildLocalFileKey(projectId, localCacheKey),
    ) ?? null
  );
}

export async function renameLocalProjectFile(
  projectId: string,
  cacheKey: string,
  fileName: string,
): Promise<LocalProjectFileRecord | null> {
  const existing = await getLocalProjectFile(projectId, cacheKey);
  if (!existing) {
    return null;
  }

  const next = {
    ...existing,
    fileName,
    updatedAt: Date.now(),
  };

  cacheLocalProjectFileRecord(next);
  await persistDesktopLocalProjectFile(next);
  return next;
}

export async function deleteLocalProjectFile(projectId: string, cacheKey: string) {
  getLocalProjectFilesUserKey();
  const key = buildLocalFileKey(projectId, cacheKey);
  localProjectFilesMemory.delete(key);
  localProjectFileUploadBindingsMemory.delete(key);
  await deleteDesktopLocalProjectFile(key);
  try {
    await idbDelete(key);
  } catch {
    // Local draft deletion should still succeed from memory if IndexedDB cleanup fails.
  }
}

export async function listLocalProjectFiles(projectId: string): Promise<LocalProjectFileRecord[]> {
  await hydrateDesktopLocalProjectFiles();
  const prefix = localFileKeyPrefix(projectId);
  const outByKey = new Map<string, LocalProjectFileRecord>();
  for (const [key, record] of localProjectFilesMemory.entries()) {
    if (!key.startsWith(prefix) || record.projectId !== projectId) continue;
    outByKey.set(key, cloneLocalProjectFileRecord(record));
  }

  const out = [...outByKey.values()];

  out.sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
  return out;
}

export async function listLocalTextFiles(
  projectId: string,
  kind?: "note",
): Promise<LocalTextProjectFileRecord[]> {
  const files = await listLocalProjectFiles(projectId);
  return files.filter((record): record is LocalTextProjectFileRecord => {
    if (record.type !== "note") return false;
    return kind ? record.type === kind : true;
  });
}
