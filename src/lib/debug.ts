// lib/debug.ts
// 统一的前端 debug 日志开关

"use client";

import { DEBUG } from "@/src/lib/config/env";

export function debugEnabled(): boolean {
  return DEBUG;
}

export function debugLog(...args: unknown[]) {
  if (debugEnabled()) {
    // eslint-disable-next-line no-console -- central debug logger.
    console.log(...args);
  }
}

export function debugFlagLog(enabled: boolean, ...args: unknown[]) {
  if (process.env.NODE_ENV !== "production" && enabled) {
    // eslint-disable-next-line no-console -- central debug flag logger.
    console.log(...args);
  }
}

export function debugLocalStorageFlagEnabled(storageKeys: readonly string[]): boolean {
  if (process.env.NODE_ENV === "production" || typeof window === "undefined") {
    return false;
  }

  try {
    return storageKeys.some((key) => window.localStorage.getItem(key) === "1");
  } catch {
    return false;
  }
}

type DebugChannel = "middle" | "slides";

function readDebugOverride(
  windowKey: "__DECKWISE_MIDDLE_DEBUG__" | "__DECKWISE_SLIDES_DISPLAY_TRACE__",
  storageKeys: readonly string[],
): boolean | null {
  if (typeof window === "undefined") {
    return null;
  }

  const debugWindow = window as typeof window & {
    __DECKWISE_MIDDLE_DEBUG__?: boolean;
    __DECKWISE_SLIDES_DISPLAY_TRACE__?: boolean;
  };
  const windowValue = debugWindow[windowKey];
  if (windowValue === true) {
    return true;
  }
  if (windowValue === false) {
    return false;
  }

  try {
    for (const storageKey of storageKeys) {
      const stored = window.localStorage.getItem(storageKey);
      if (stored === "1") {
        return true;
      }
      if (stored === "0") {
        return false;
      }
    }
  } catch {
    // Fall through to env defaults below.
  }

  return null;
}

export function debugChannelEnabled(channel: DebugChannel): boolean {
  if (process.env.NODE_ENV === "production") {
    return false;
  }

  if (channel === "middle") {
    return (
      readDebugOverride("__DECKWISE_MIDDLE_DEBUG__", [
        "deckwise:middle-debug",
        "deckwise:slides-display-trace",
      ]) ?? DEBUG
    );
  }

  return (
    readDebugOverride("__DECKWISE_SLIDES_DISPLAY_TRACE__", [
      "deckwise:slides-display-trace",
      "deckwise:middle-debug",
    ]) ?? DEBUG
  );
}

export function debugChannelLog(channel: DebugChannel, ...args: unknown[]) {
  if (debugChannelEnabled(channel)) {
    // eslint-disable-next-line no-console -- central debug channel logger.
    console.log(...args);
  }
}
