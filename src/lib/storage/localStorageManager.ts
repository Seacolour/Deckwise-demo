/**
 * Local storage helpers used across the app.
 *
 * Keep these wrappers small and runtime-safe so callers do not need to repeat
 * `typeof window` guards or try/catch around storage access.
 */

export function isClient(): boolean {
  return typeof window !== "undefined";
}

export function getString(key: string): string | null {
  if (!isClient()) return null;
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function setString(key: string, value: string): boolean {
  if (!isClient()) return false;
  try {
    localStorage.setItem(key, value);
    return true;
  } catch {
    return false;
  }
}

export function getJSON<T = unknown>(key: string): T | null {
  if (!isClient()) return null;
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export function setJSON<T = unknown>(key: string, value: T): boolean {
  if (!isClient()) return false;
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

export function remove(key: string): boolean {
  if (!isClient()) return false;
  try {
    localStorage.removeItem(key);
    return true;
  } catch {
    return false;
  }
}

export function clear(): boolean {
  if (!isClient()) return false;
  try {
    localStorage.clear();
    return true;
  } catch {
    return false;
  }
}

export function isDebugFlagEnabled(flag: string): boolean {
  return getString(flag) === "1";
}

export function setDebugFlag(flag: string, enabled: boolean): void {
  setString(flag, enabled ? "1" : "0");
}

export const DebugFlags = {
  // Global switch for dev-only buttons; the Deckwise shell maps it to
  // html[data-debug-only-actions] so CSS can hide all debug affordances.
  ACTIONS_VISIBLE: "debug:actions-visible",
  STREAM: "debug:stream",
  ANIM: "debug:anim",
  ANIM_VERBOSE: "debug:anim-verbose",
  PROJECTS: "debug:projects",
  FILES: "debug:files",
  OFFICE_UPLOAD: "debug:office-upload",
  IMAGE_PREVIEW: "debug:image-preview",
  SHEET_PREVIEW: "debug:sheet-preview",
  CHAT_DND: "debug:chat-dnd",
  CHAT_SESSION_REVEAL: "debug:chat-session-reveal",
  CHAT_DISABLE_TITLE_SYNC: "debug:chat-disable-title-sync",
  CHAT_LEGACY_STREAM_DONE: "debug:chat-legacy-stream-done",
} as const;

export const isDebugEnabled = {
  actionsVisible: () => isDebugFlagEnabled(DebugFlags.ACTIONS_VISIBLE),
  stream: () => isDebugFlagEnabled(DebugFlags.STREAM),
  anim: () => isDebugFlagEnabled(DebugFlags.ANIM),
  animVerbose: () => isDebugFlagEnabled(DebugFlags.ANIM_VERBOSE),
  projects: () => isDebugFlagEnabled(DebugFlags.PROJECTS),
  files: () => isDebugFlagEnabled(DebugFlags.FILES),
  officeUpload: () => isDebugFlagEnabled(DebugFlags.OFFICE_UPLOAD),
  imagePreview: () => isDebugFlagEnabled(DebugFlags.IMAGE_PREVIEW),
  sheetPreview: () => isDebugFlagEnabled(DebugFlags.SHEET_PREVIEW),
  chatDnd: () => isDebugFlagEnabled(DebugFlags.CHAT_DND),
  chatSessionReveal: () => isDebugFlagEnabled(DebugFlags.CHAT_SESSION_REVEAL),
  chatDisableTitleSync: () => isDebugFlagEnabled(DebugFlags.CHAT_DISABLE_TITLE_SYNC),
  chatLegacyStreamDone: () => isDebugFlagEnabled(DebugFlags.CHAT_LEGACY_STREAM_DONE),
} as const;

export const StorageKeys = {
  TOKEN: "token",
  USER: "user",
  ENV_SIGNATURE: "env_signature",
} as const;
