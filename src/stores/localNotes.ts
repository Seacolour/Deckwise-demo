"use client";

export type LocalNoteRecord = {
  key: string;
  type: "note";
  projectId: string;
  cacheKey: string;
  fileName: string;
  content: string;
  createdAt: number;
  updatedAt: number;
};

function noteKeyPrefix(projectId: string) {
  return `note:${projectId}:`;
}

function buildNoteKey(projectId: string, cacheKey: string) {
  return `${noteKeyPrefix(projectId)}${cacheKey}`;
}

function randomId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function pad2(n: number) {
  return String(n).padStart(2, "0");
}

export function makeNoteFileName(now = new Date()) {
  const y = now.getFullYear();
  const m = pad2(now.getMonth() + 1);
  const d = pad2(now.getDate());
  const hh = pad2(now.getHours());
  const mm = pad2(now.getMinutes());
  const ss = pad2(now.getSeconds());
  // Keep it simple & filesystem-safe.
  return `note-${y}${m}${d}-${hh}${mm}${ss}`;
}

// Local notes are intentionally kept in-memory only.
// This avoids persisting newly created note content to IndexedDB.
const localNotesMemory = new Map<string, LocalNoteRecord>();

export async function createLocalNote(projectId: string, opts?: { content?: string; fileName?: string }) {
  const cacheKey = `local-note-${randomId()}`;
  const createdAt = Date.now();
  const fileName = opts?.fileName ?? makeNoteFileName();
  const content = opts?.content ?? "";
  const record: LocalNoteRecord = {
    key: buildNoteKey(projectId, cacheKey),
    type: "note",
    projectId,
    cacheKey,
    fileName,
    content,
    createdAt,
    updatedAt: createdAt,
  };

  localNotesMemory.set(record.key, record);

  return record;
}

export async function getLocalNote(projectId: string, cacheKey: string): Promise<LocalNoteRecord | null> {
  const key = buildNoteKey(projectId, cacheKey);
  const r = localNotesMemory.get(key);
  if (!r) return null;
  if (r.type !== "note" || r.projectId !== projectId || r.cacheKey !== cacheKey) return null;
  return r;
}

export async function upsertLocalNoteContent(projectId: string, cacheKey: string, content: string) {
  const existing = await getLocalNote(projectId, cacheKey);
  if (!existing) return;
  const next: LocalNoteRecord = { ...existing, content, updatedAt: Date.now() };
  localNotesMemory.set(next.key, next);
}

export async function deleteLocalNote(projectId: string, cacheKey: string) {
  const key = buildNoteKey(projectId, cacheKey);
  localNotesMemory.delete(key);
}

export async function listLocalNotes(projectId: string): Promise<LocalNoteRecord[]> {
  const prefix = noteKeyPrefix(projectId);
  const out: LocalNoteRecord[] = [];
  for (const [key, rec] of localNotesMemory.entries()) {
    if (key.startsWith(prefix) && rec.type === "note" && rec.projectId === projectId) {
      out.push(rec);
    }
  }
  // newest first
  out.sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
  return out;
}

export function clearLocalNotesMemory() {
  localNotesMemory.clear();
}
