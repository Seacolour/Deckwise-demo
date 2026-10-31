"use client";

import { useCallback, useSyncExternalStore } from "react";
import { LocalStorageKeys } from "@/src/lib/config/constants/storage";
import {
  THEME_DARK_MEDIA_QUERY,
  applyThemePreference,
  isThemePreference,
  persistThemePreference,
  readStoredThemePreference,
  resolveThemePreference,
  type ResolvedTheme,
  type ThemePreference,
} from "@/src/lib/theme";

type ThemeSnapshot = {
  themePreference: ThemePreference;
  resolvedTheme: ResolvedTheme;
};

const DEFAULT_THEME_SNAPSHOT: ThemeSnapshot = {
  themePreference: "system",
  resolvedTheme: "light",
};

const themeStoreListeners = new Set<() => void>();

let currentThemeSnapshot = DEFAULT_THEME_SNAPSHOT;
let themeStoreInitialized = false;
let themeMediaQuery: MediaQueryList | null = null;

function readResolvedThemeFromDom(): ResolvedTheme | null {
  if (typeof document === "undefined") {
    return null;
  }

  const resolved = document.documentElement.dataset.themeResolved;
  return resolved === "dark" || resolved === "light" ? resolved : null;
}

function readThemeSnapshotFromDom(): ThemeSnapshot {
  if (typeof document === "undefined") {
    return DEFAULT_THEME_SNAPSHOT;
  }

  const root = document.documentElement;
  const themePreference = isThemePreference(root.dataset.theme)
    ? root.dataset.theme
    : readStoredThemePreference();
  const prefersDark = themeMediaQuery?.matches ??
    (typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia(THEME_DARK_MEDIA_QUERY).matches);
  const resolvedTheme = readResolvedThemeFromDom() ??
    resolveThemePreference(themePreference, Boolean(prefersDark));

  return {
    themePreference,
    resolvedTheme,
  };
}

function emitThemeSnapshot() {
  for (const listener of themeStoreListeners) {
    listener();
  }
}

function syncThemeSnapshot(
  nextPreference?: ThemePreference,
  options?: {
    animate?: boolean;
    prefersDarkOverride?: boolean;
  },
) {
  const themePreference = nextPreference ?? readStoredThemePreference();
  const resolvedTheme = applyThemePreference(
    themePreference,
    options?.prefersDarkOverride,
    { animate: options?.animate },
  );
  const nextSnapshot = { themePreference, resolvedTheme };
  const didChange =
    currentThemeSnapshot.themePreference !== nextSnapshot.themePreference ||
    currentThemeSnapshot.resolvedTheme !== nextSnapshot.resolvedTheme;

  currentThemeSnapshot = nextSnapshot;

  if (didChange) {
    emitThemeSnapshot();
  }
}

function ensureThemeStoreInitialized() {
  if (themeStoreInitialized || typeof window === "undefined") {
    return;
  }

  themeStoreInitialized = true;
  themeMediaQuery = typeof window.matchMedia === "function"
    ? window.matchMedia(THEME_DARK_MEDIA_QUERY)
    : null;
  const initialSnapshot = readThemeSnapshotFromDom();

  currentThemeSnapshot = {
    themePreference: initialSnapshot.themePreference,
    resolvedTheme: applyThemePreference(
      initialSnapshot.themePreference,
      themeMediaQuery?.matches,
    ),
  };

  themeMediaQuery?.addEventListener("change", () => {
    syncThemeSnapshot(undefined, {
      animate: true,
      prefersDarkOverride: themeMediaQuery?.matches,
    });
  });

  window.addEventListener("storage", (event) => {
    if (
      event.storageArea !== window.localStorage ||
      (event.key !== null && event.key !== LocalStorageKeys.THEME_PREFERENCE)
    ) {
      return;
    }

    syncThemeSnapshot(undefined, {
      animate: true,
      prefersDarkOverride: themeMediaQuery?.matches,
    });
  });
}

function subscribeToThemeStore(listener: () => void) {
  ensureThemeStoreInitialized();
  themeStoreListeners.add(listener);

  return () => {
    themeStoreListeners.delete(listener);
  };
}

function getThemeSnapshot(): ThemeSnapshot {
  if (typeof window === "undefined") {
    return DEFAULT_THEME_SNAPSHOT;
  }

  ensureThemeStoreInitialized();
  return currentThemeSnapshot;
}

export function useThemePreference() {
  const { themePreference, resolvedTheme } = useSyncExternalStore(
    subscribeToThemeStore,
    getThemeSnapshot,
    () => DEFAULT_THEME_SNAPSHOT,
  );

  const setThemePreference = useCallback((nextPreference: ThemePreference) => {
    persistThemePreference(nextPreference);
    syncThemeSnapshot(nextPreference, { animate: true });
  }, []);

  return {
    themePreference,
    resolvedTheme,
    setThemePreference,
  };
}
