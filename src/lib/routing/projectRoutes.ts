// Authenticated product home stays at `/app` and projects stay at `/canvas`.

export const HOME_PATH = "/app";
export const CANVAS_PATH = "/canvas";

export function normalizeRouteProjectId(value?: string | null): string | null {
  const normalized = typeof value === "string" ? value.trim() : "";
  return normalized || null;
}

export function getProjectCanvasPath(
  projectId: string | number,
  options: { sourceExampleId?: string | null } = {},
): string {
  const params = new URLSearchParams({ projectId: String(projectId) });
  const sourceExampleId = options.sourceExampleId?.trim();
  if (sourceExampleId) {
    params.set("sourceExampleId", sourceExampleId);
  }
  return `${CANVAS_PATH}?${params.toString()}`;
}

export function isCanvasPath(pathname?: string | null): boolean {
  return pathname === CANVAS_PATH;
}

export function isProjectRoutePath(pathname?: string | null): boolean {
  return isCanvasPath(pathname);
}

export function extractProjectIdFromRoute(
  pathname?: string | null,
  searchProjectId?: string | null,
): string | null {
  if (isCanvasPath(pathname)) {
    return normalizeRouteProjectId(searchProjectId);
  }

  return null;
}
