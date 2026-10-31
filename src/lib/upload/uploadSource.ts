export type ProjectFileUploadSource = "user_upload" | "generated";

export const DEFAULT_PROJECT_FILE_UPLOAD_SOURCE: ProjectFileUploadSource = "user_upload";

export function normalizeProjectFileUploadSource(
  uploadSource?: ProjectFileUploadSource | null,
): ProjectFileUploadSource {
  return uploadSource ?? DEFAULT_PROJECT_FILE_UPLOAD_SOURCE;
}
