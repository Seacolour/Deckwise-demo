export const WORKSPACE_FILE_DRAG_TYPE = "application/x-agentworkbench-workspace-file";

export type WorkspaceFileDrag =
  | { kind: "document"; projectId: string; documentId: string; name: string }
  | { kind: "file"; projectId: string; fileId: string; cacheKey: string; name: string; image: boolean };

export function writeWorkspaceFileDrag(dataTransfer: DataTransfer, entry: WorkspaceFileDrag) {
  dataTransfer.effectAllowed = "copy";
  dataTransfer.setData(WORKSPACE_FILE_DRAG_TYPE, JSON.stringify(entry));
  dataTransfer.setData("text/plain", entry.name);
}

export function readWorkspaceFileDrag(dataTransfer: DataTransfer): WorkspaceFileDrag | null {
  const raw = dataTransfer.getData(WORKSPACE_FILE_DRAG_TYPE);
  if (!raw || raw.length > 2048) return null;
  try {
    const value: unknown = JSON.parse(raw);
    if (!value || typeof value !== "object" || Array.isArray(value)) return null;
    const entry = value as Record<string, unknown>;
    const validId = (id: unknown): id is string => typeof id === "string" && /^[1-9]\d*$/.test(id);
    if (!validId(entry.projectId) || typeof entry.name !== "string" || !entry.name.trim() || entry.name.length > 512) return null;
    if (entry.kind === "document" && validId(entry.documentId)) return {
      kind: "document", projectId: entry.projectId, documentId: entry.documentId, name: entry.name,
    };
    if (entry.kind === "file" && validId(entry.fileId)
      && typeof entry.cacheKey === "string" && /^[a-f\d]{64}$/i.test(entry.cacheKey)
      && typeof entry.image === "boolean") return {
      kind: "file", projectId: entry.projectId, fileId: entry.fileId,
      cacheKey: entry.cacheKey, name: entry.name, image: entry.image,
    };
  } catch {
    // Ignore foreign or malformed drag payloads.
  }
  return null;
}
