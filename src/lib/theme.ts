import { LocalStorageKeys } from "@/src/lib/config/constants/storage";

export type ThemePreference = "light" | "dark" | "system";
export type ResolvedTheme = "light" | "dark";

export const THEME_DARK_MEDIA_QUERY = "(prefers-color-scheme: dark)";
export const THEME_FAVICON_LIGHT_URL = "/icon.png";
export const THEME_FAVICON_DARK_URL = "/icon.png";
export const THEME_SWITCHING_ATTR = "data-theme-switching";
export const THEME_SWITCH_TRANSITION_MS = 180;

const THEME_FAVICON_ICON_ID = "deckwise-theme-favicon-icon";
const THEME_FAVICON_SHORTCUT_ID = "deckwise-theme-favicon-shortcut";
const THEME_ROOT_DATASET_PREFERENCE_KEY = "theme";
const THEME_ROOT_DATASET_RESOLVED_KEY = "themeResolved";
const THEME_DARK_CLASSNAME = "dark";
const REDUCED_MOTION_MEDIA_QUERY = "(prefers-reduced-motion: reduce)";

let themeSwitchCleanupTimer: number | null = null;

export function isThemePreference(value: string | null | undefined): value is ThemePreference {
  return value === "light" || value === "dark" || value === "system";
}

export function resolveThemePreference(
  preference: ThemePreference,
  prefersDark: boolean,
): ResolvedTheme {
  if (preference === "system") {
    return prefersDark ? "dark" : "light";
  }

  return preference;
}

export function readStoredThemePreference(): ThemePreference {
  if (typeof window === "undefined") {
    return "system";
  }

  try {
    const stored = window.localStorage.getItem(LocalStorageKeys.THEME_PREFERENCE);
    return isThemePreference(stored) ? stored : "system";
  } catch {
    return "system";
  }
}

export function resolveThemeFaviconUrl(resolvedTheme: ResolvedTheme): string {
  return resolvedTheme === "dark" ? THEME_FAVICON_DARK_URL : THEME_FAVICON_LIGHT_URL;
}

function upsertThemeFaviconLink(id: string, rel: string, href: string) {
  if (typeof document === "undefined") {
    return;
  }

  const head = document.head;
  if (!head) {
    return;
  }

  let link = document.getElementById(id);
  if (!(link instanceof HTMLLinkElement)) {
    const nextLink = document.createElement("link");
    nextLink.id = id;
    nextLink.rel = rel;
    nextLink.type = "image/png";
    nextLink.sizes = "512x512";
    head.appendChild(nextLink);
    link = nextLink;
  }

  if (link.getAttribute("href") !== href) {
    link.setAttribute("href", href);
  }
}

export function syncThemeFavicon(resolvedTheme: ResolvedTheme) {
  const href = resolveThemeFaviconUrl(resolvedTheme);

  // Keep app-controlled theme changes in sync with the tab icon instead of
  // relying only on the browser's system-level color-scheme detection.
  upsertThemeFaviconLink(THEME_FAVICON_ICON_ID, "icon", href);
  upsertThemeFaviconLink(THEME_FAVICON_SHORTCUT_ID, "shortcut icon", href);
}

export function persistThemePreference(preference: ThemePreference) {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(
      LocalStorageKeys.THEME_PREFERENCE,
      preference,
    );
  } catch {
    // Best effort only.
  }
}

function clearThemeSwitching(root: HTMLElement | null) {
  if (typeof window !== "undefined" && themeSwitchCleanupTimer !== null) {
    window.clearTimeout(themeSwitchCleanupTimer);
    themeSwitchCleanupTimer = null;
  }

  root?.removeAttribute(THEME_SWITCHING_ATTR);
}

function shouldAnimateThemeSwitch(animate: boolean): boolean {
  if (!animate || typeof window === "undefined" || typeof document === "undefined") {
    return false;
  }

  if (typeof window.matchMedia !== "function") {
    return true;
  }

  return !window.matchMedia(REDUCED_MOTION_MEDIA_QUERY).matches;
}

function applyResolvedThemeToRoot(
  root: HTMLElement,
  preference: ThemePreference,
  resolved: ResolvedTheme,
): boolean {
  const nextDark = resolved === "dark";
  const didChange =
    root.dataset[THEME_ROOT_DATASET_PREFERENCE_KEY] !== preference ||
    root.dataset[THEME_ROOT_DATASET_RESOLVED_KEY] !== resolved ||
    root.classList.contains(THEME_DARK_CLASSNAME) !== nextDark ||
    root.style.colorScheme !== resolved;

  root.dataset[THEME_ROOT_DATASET_PREFERENCE_KEY] = preference;
  root.dataset[THEME_ROOT_DATASET_RESOLVED_KEY] = resolved;
  root.classList.toggle(THEME_DARK_CLASSNAME, nextDark);
  root.style.colorScheme = resolved;
  syncThemeFavicon(resolved);

  return didChange;
}

function createApplyResolvedThemeScript(options: {
  rootRef: string;
  preferenceRef: string;
  resolvedRef: string;
  syncFaviconRef: string;
}): string {
  const {
    rootRef,
    preferenceRef,
    resolvedRef,
    syncFaviconRef,
  } = options;

  return [
    `${rootRef}.dataset[${JSON.stringify(THEME_ROOT_DATASET_PREFERENCE_KEY)}] = ${preferenceRef};`,
    `${rootRef}.dataset[${JSON.stringify(THEME_ROOT_DATASET_RESOLVED_KEY)}] = ${resolvedRef};`,
    `${rootRef}.classList.toggle(${JSON.stringify(THEME_DARK_CLASSNAME)}, ${resolvedRef} === "dark");`,
    `${rootRef}.style.colorScheme = ${resolvedRef};`,
    `${syncFaviconRef}(${resolvedRef});`,
  ].join("\n      ");
}

export function applyThemePreference(
  preference: ThemePreference,
  prefersDarkOverride?: boolean,
  options?: {
    animate?: boolean;
  },
): ResolvedTheme {
  const prefersDark = typeof prefersDarkOverride === "boolean"
    ? prefersDarkOverride
    : typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia(THEME_DARK_MEDIA_QUERY).matches;
  const resolved = resolveThemePreference(preference, prefersDark);

  if (typeof document !== "undefined") {
    const root = document.documentElement;
    const animate = shouldAnimateThemeSwitch(Boolean(options?.animate));

    if (!animate) {
      clearThemeSwitching(root);
    } else {
      clearThemeSwitching(root);
      root.setAttribute(THEME_SWITCHING_ATTR, "true");
      // Force the browser to commit the transition contract before variables flip.
      root.getBoundingClientRect();
    }

    const didChange = applyResolvedThemeToRoot(root, preference, resolved);

    if (animate && didChange && typeof window !== "undefined") {
      themeSwitchCleanupTimer = window.setTimeout(() => {
        themeSwitchCleanupTimer = null;
        root.removeAttribute(THEME_SWITCHING_ATTR);
      }, THEME_SWITCH_TRANSITION_MS);
    } else if (!didChange) {
      clearThemeSwitching(root);
    }
  }

  return resolved;
}

export function createThemeInitScript(options?: {
  forcedPreference?: ThemePreference;
}): string {
  return `(() => {
    try {
      const storageKey = ${JSON.stringify(LocalStorageKeys.THEME_PREFERENCE)};
      const mediaQuery = ${JSON.stringify(THEME_DARK_MEDIA_QUERY)};
      const lightIcon = ${JSON.stringify(THEME_FAVICON_LIGHT_URL)};
      const darkIcon = ${JSON.stringify(THEME_FAVICON_DARK_URL)};
      const forcedPreference = ${JSON.stringify(options?.forcedPreference ?? null)};
      const iconId = ${JSON.stringify(THEME_FAVICON_ICON_ID)};
      const shortcutId = ${JSON.stringify(THEME_FAVICON_SHORTCUT_ID)};
      const upsertFaviconLink = (id, rel, href) => {
        const head = document.head;
        if (!head) {
          return;
        }

        let link = document.getElementById(id);
        if (!link || link.tagName !== "LINK") {
          link = document.createElement("link");
          link.id = id;
          link.rel = rel;
          link.type = "image/png";
          link.setAttribute("sizes", "512x512");
          head.appendChild(link);
        }

        if (link.getAttribute("href") !== href) {
          link.setAttribute("href", href);
        }
      };
      const syncFavicon = (resolvedTheme) => {
        const href = resolvedTheme === "dark" ? darkIcon : lightIcon;
        upsertFaviconLink(iconId, "icon", href);
        upsertFaviconLink(shortcutId, "shortcut icon", href);
      };
      let stored = null;
      try {
        stored = window.localStorage.getItem(storageKey);
      } catch (_) {}
      let preference = forcedPreference || "system";
      if (!forcedPreference && (stored === "light" || stored === "dark" || stored === "system")) {
        preference = stored;
      }
      const prefersDark =
        typeof window.matchMedia === "function" &&
        window.matchMedia(mediaQuery).matches;
      let resolved = preference;
      if (preference === "system") {
        resolved = prefersDark ? "dark" : "light";
      }
      const root = document.documentElement;
      ${createApplyResolvedThemeScript({
        rootRef: "root",
        preferenceRef: "preference",
        resolvedRef: "resolved",
        syncFaviconRef: "syncFavicon",
      })}
    } catch (_) {}
  })();`;
}
