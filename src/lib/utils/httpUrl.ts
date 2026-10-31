const HTTP_URL_PREFIX = /^https?:\/\//i;
const SINGLE_SLASH_HTTP_PREFIX = /^(https?):\/(?!\/)/i;

export function normalizeHttpUrl(raw: string): string {
  return raw.trim().replace(SINGLE_SLASH_HTTP_PREFIX, "$1://");
}

export function toHttpUrlOrNull(raw: unknown): string | null {
  if (typeof raw !== "string") {
    return null;
  }

  const normalized = normalizeHttpUrl(raw);
  if (!HTTP_URL_PREFIX.test(normalized)) {
    return null;
  }

  return normalized;
}
