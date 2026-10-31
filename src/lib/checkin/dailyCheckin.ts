import { fetchCheckinStatus, submitCheckin } from "@/src/lib/api/checkin";
import { StorageKeyBuilders } from "@/src/lib/config/constants/storage";
import { CONFIG } from "@/src/lib/config/env";
import {
  getJSON,
  getString,
  remove,
  setJSON,
  setString,
} from "@/src/lib/storage/localStorageManager";
import { isRecord, pickString } from "@/src/lib/types/guards";

type PendingMarker = {
  dayKey: string;
  startedAt: number;
};

const CHECKIN_PENDING_TTL_MS = 30_000;
const inFlightByUser = new Map<string, Promise<void>>();

function getTodayLocalDayKey(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getLastCheckinStorageKey(userKey: string): string {
  return StorageKeyBuilders.userCheckinLastDate(userKey);
}

function getPendingStorageKey(userKey: string): string {
  return StorageKeyBuilders.userCheckinPending(userKey);
}

function readLastCheckinDate(userKey: string): string | null {
  return getString(getLastCheckinStorageKey(userKey));
}

function writeLastCheckinDate(userKey: string, dayKey: string): void {
  setString(getLastCheckinStorageKey(userKey), dayKey);
}

function clearPendingCheckin(userKey: string): void {
  remove(getPendingStorageKey(userKey));
}

function readPendingMarker(userKey: string): PendingMarker | null {
  const raw = getJSON<unknown>(getPendingStorageKey(userKey));
  if (!isRecord(raw)) {
    return null;
  }

  const dayKey = pickString(raw, ["dayKey"]);
  const startedAtRaw = raw.startedAt;
  const startedAt = typeof startedAtRaw === "number" && Number.isFinite(startedAtRaw)
    ? startedAtRaw
    : null;

  if (!dayKey || startedAt === null) {
    clearPendingCheckin(userKey);
    return null;
  }

  return { dayKey, startedAt };
}

function hasFreshPendingCheckin(userKey: string, dayKey: string, now: number): boolean {
  const pending = readPendingMarker(userKey);
  if (!pending) {
    return false;
  }

  if (pending.dayKey !== dayKey || now - pending.startedAt > CHECKIN_PENDING_TTL_MS) {
    clearPendingCheckin(userKey);
    return false;
  }

  return true;
}

function markPendingCheckin(userKey: string, dayKey: string, now: number): void {
  setJSON(getPendingStorageKey(userKey), {
    dayKey,
    startedAt: now,
  });
}

function resolveBrowserLocale(): string | undefined {
  if (typeof navigator === "undefined") {
    return undefined;
  }

  const directLocale = pickString(navigator.language);
  if (directLocale) {
    return directLocale;
  }

  if (!Array.isArray(navigator.languages)) {
    return undefined;
  }

  for (const candidate of navigator.languages) {
    const locale = pickString(candidate);
    if (locale) {
      return locale;
    }
  }

  return undefined;
}

function resolveCountry(locale: string | undefined): string | undefined {
  if (!locale) {
    return undefined;
  }

  const match = locale.match(/[-_]([a-z]{2}|\d{3})(?:[-_].*)?$/i);
  return match?.[1]?.toUpperCase();
}

function resolveBrowserTimeZone(): string | undefined {
  try {
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return pickString(timeZone);
  } catch {
    return undefined;
  }
}

function resolveEnvironment(): string {
  return CONFIG.ENV === "sandbox" ? "sandbox" : "production";
}

function buildCheckinBody() {
  const locale = resolveBrowserLocale();

  return {
    deviceType: "web",
    environment: resolveEnvironment(),
    timeZone: resolveBrowserTimeZone(),
    locale,
    country: resolveCountry(locale),
  };
}

async function runDailyCheckin(userKey: string): Promise<void> {
  const today = getTodayLocalDayKey();
  if (readLastCheckinDate(userKey) === today) {
    return;
  }

  const now = Date.now();
  if (hasFreshPendingCheckin(userKey, today, now)) {
    return;
  }

  markPendingCheckin(userKey, today, now);

  try {
    try {
      const status = await fetchCheckinStatus();
      if (status?.lastCheckinDate === today) {
        writeLastCheckinDate(userKey, today);
        return;
      }
    } catch {
      if (readLastCheckinDate(userKey) === today) {
        return;
      }
    }

    await submitCheckin(buildCheckinBody());
    writeLastCheckinDate(userKey, today);
  } finally {
    clearPendingCheckin(userKey);
  }
}

export function ensureDailyCheckin(userKey: string): Promise<void> {
  if (!userKey || userKey === "anon") {
    return Promise.resolve();
  }

  const existingTask = inFlightByUser.get(userKey);
  if (existingTask) {
    return existingTask;
  }

  const task = runDailyCheckin(userKey).finally(() => {
    if (inFlightByUser.get(userKey) === task) {
      inFlightByUser.delete(userKey);
    }
  });

  inFlightByUser.set(userKey, task);
  return task;
}
