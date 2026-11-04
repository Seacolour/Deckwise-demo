export const SETTINGS_ROOT_PATH = "/app/settings";

// These are the concrete sections owned by the unified Settings Center shell.
// Intentional dual-entry contract:
// - `#settings/...` is the normal in-app UI path because it preserves whatever
//   surface is open behind the modal, including query-backed canvas pages.
// - `/app/settings/...` remains as a real-route fallback for reloads, shared
//   links, external callbacks, and future route-level settings needs.
export const SETTINGS_CENTER_SECTIONS = [
  "account",
  "general",
  "models",
  "plugins",
  "personalization",
] as const;

export type SettingsSheetSection = typeof SETTINGS_CENTER_SECTIONS[number];

export const SETTINGS_DEFAULT_SECTION: SettingsSheetSection = "account";
export const SETTINGS_DEFAULT_ROUTE_PATH = `${SETTINGS_ROOT_PATH}/${SETTINGS_DEFAULT_SECTION}`;
export const PERSONALIZATION_SETTINGS_ROUTE_PATH = `${SETTINGS_ROOT_PATH}/personalization`;
export const SETTINGS_HASH_ROOT = "settings";
export const SETTINGS_DEFAULT_HASH = getSettingsSheetHash(SETTINGS_DEFAULT_SECTION);
export const PERSONALIZATION_SETTINGS_HASH = getSettingsSheetHash("personalization");

export function isSettingsSheetSection(value: string | null | undefined): value is SettingsSheetSection {
  return SETTINGS_CENTER_SECTIONS.includes(value as SettingsSheetSection);
}

export function getSettingsSheetPath(section: SettingsSheetSection) {
  return `${SETTINGS_ROOT_PATH}/${section}`;
}

export function getSettingsSheetHash(section: SettingsSheetSection) {
  return `#${SETTINGS_HASH_ROOT}/${section}`;
}

export function isSettingsRoutePath(pathname?: string | null): boolean {
  return Boolean(pathname && (pathname === SETTINGS_ROOT_PATH || pathname.startsWith(`${SETTINGS_ROOT_PATH}/`)));
}

export function resolveSettingsSheetSectionFromPath(pathname?: string | null): SettingsSheetSection | null {
  if (!pathname?.startsWith(`${SETTINGS_ROOT_PATH}/`)) {
    return null;
  }

  const section = pathname.slice(SETTINGS_ROOT_PATH.length + 1).split("/")[0];
  return isSettingsSheetSection(section) ? section : null;
}

export function resolveSettingsSheetSectionFromHash(hash?: string | null): SettingsSheetSection | null {
  const normalized = (hash ?? "").replace(/^#/, "").replace(/^\/+/, "");
  if (!normalized) {
    return null;
  }

  const [root, section = SETTINGS_DEFAULT_SECTION] = normalized.split("/");
  if (root !== SETTINGS_HASH_ROOT) {
    return null;
  }

  return isSettingsSheetSection(section) ? section : SETTINGS_DEFAULT_SECTION;
}
