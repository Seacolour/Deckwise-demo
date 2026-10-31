export function joinApiUrl(base: string, path: string): string {
  const normalizedBase = base.replace(/\/$/u, "");
  if (!normalizedBase) {
    return path;
  }
  return `${normalizedBase}/${path.replace(/^\/+/, "")}`;
}
