export const DECK_PACKAGE_SUFFIX = ".deck";
export const DECK_CANONICAL_JSON_SUFFIX = ".deck.json";
export const DECK_OUTLINE_SUFFIX = ".deck-outline.json";
export const LEGACY_MSLIDES_SUFFIX = ".mslides";

export const PROJECT_FILE_SLIDE_COMPOUND_SUFFIXES = [
  DECK_CANONICAL_JSON_SUFFIX,
  DECK_PACKAGE_SUFFIX,
  LEGACY_MSLIDES_SUFFIX,
] as const;

export const PROJECT_FILE_OUTLINE_SUFFIX = DECK_OUTLINE_SUFFIX;
export const PROJECT_FILE_OUTLINE_SUFFIXES = [PROJECT_FILE_OUTLINE_SUFFIX] as const;

const PROJECT_FILE_DISPLAY_COMPOUND_SUFFIXES = [
  ...PROJECT_FILE_OUTLINE_SUFFIXES,
  ...PROJECT_FILE_SLIDE_COMPOUND_SUFFIXES,
] as const;

const SIMPLE_EXTENSIONS = new Set([
  "avif",
  "bmp",
  "doc",
  "docx",
  "deck",
  "gif",
  "heic",
  "heif",
  "ico",
  "jpeg",
  "jpg",
  "json",
  "md",
  "mslides",
  "pdf",
  "png",
  "ppt",
  "pptx",
  "svg",
  "txt",
  "webp",
  "xlsx",
]);

function trimToNull(value: string | null | undefined): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim();
  return normalized || null;
}

export function stripProjectFileExtension(fileName: string): string {
  const normalized = trimToNull(fileName);
  if (!normalized) {
    return "";
  }

  const lower = normalized.toLowerCase();
  for (const suffix of PROJECT_FILE_DISPLAY_COMPOUND_SUFFIXES) {
    if (lower.endsWith(suffix)) {
      return normalized.slice(0, normalized.length - suffix.length).trim() || normalized;
    }
  }

  const dotIndex = normalized.lastIndexOf(".");
  if (dotIndex <= 0) {
    return normalized;
  }

  const extension = lower.slice(dotIndex + 1);
  if (!SIMPLE_EXTENSIONS.has(extension)) {
    return normalized;
  }

  return normalized.slice(0, dotIndex).trim() || normalized;
}

export function getProjectFileExtensionSuffix(fileName: string): string {
  const normalized = trimToNull(fileName);
  if (!normalized) {
    return "";
  }

  const lower = normalized.toLowerCase();
  for (const suffix of PROJECT_FILE_DISPLAY_COMPOUND_SUFFIXES) {
    if (lower.endsWith(suffix)) {
      return normalized.slice(normalized.length - suffix.length);
    }
  }

  const dotIndex = normalized.lastIndexOf(".");
  if (dotIndex <= 0) {
    return "";
  }

  const extension = lower.slice(dotIndex + 1);
  return SIMPLE_EXTENSIONS.has(extension) ? normalized.slice(dotIndex) : "";
}

export function isProjectOutlineFileName(
  fileName: string | null | undefined,
): boolean {
  const normalized = trimToNull(fileName);
  const lower = normalized?.toLowerCase();
  return Boolean(
    lower && PROJECT_FILE_OUTLINE_SUFFIXES.some((suffix) => lower.endsWith(suffix)),
  );
}

export function toProjectCanonicalJsonFileName(
  fileName: string | null | undefined,
  fallbackBaseName = "slides",
): string {
  const baseName = stripProjectFileExtension(fileName ?? "")
    || stripProjectFileExtension(fallbackBaseName)
    || "slides";
  return `${baseName}${DECK_CANONICAL_JSON_SUFFIX}`;
}

export function toProjectDeckPackageFileName(
  fileName: string | null | undefined,
  fallbackBaseName = "slides",
): string {
  const baseName = stripProjectFileExtension(fileName ?? "")
    || stripProjectFileExtension(fallbackBaseName)
    || "slides";
  return `${baseName}${DECK_PACKAGE_SUFFIX}`;
}

export function toProjectOutlineFileName(
  fileName: string | null | undefined,
  fallbackBaseName = "slides-outline",
): string {
  const baseName = stripProjectFileExtension(fileName ?? "")
    || stripProjectFileExtension(fallbackBaseName)
    || "slides-outline";
  return `${baseName}${DECK_OUTLINE_SUFFIX}`;
}

export function toProjectFileDisplayName(
  fileName: string | null | undefined,
): string {
  const normalized = trimToNull(fileName);
  if (!normalized) {
    return "";
  }

  if (isProjectOutlineFileName(normalized)) {
    return "Outline";
  }

  return stripProjectFileExtension(normalized);
}

export function normalizeProjectFileLookupName(
  fileName: string | null | undefined,
): string | null {
  const displayName = toProjectFileDisplayName(fileName).trim().toLowerCase();
  return displayName || null;
}
