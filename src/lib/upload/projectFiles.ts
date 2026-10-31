"use client";

import { uploadFile, uploadFiles as uploadFilesBatchRequest, uploadImage } from "@/src/lib/api/generated/file-upload";
import { unwrapGeneratedPayload } from "@/src/lib/api/generatedCompat";
import { startManagedSingleFlight } from "@/src/lib/api/requestRuntime";
import type { UploadFileParams } from "@/src/lib/types/all/uploadFileParams";
import type { UploadFilesParams } from "@/src/lib/types/all/uploadFilesParams";
import type { FileUploadBatchResponse } from "@/src/lib/types/all/fileUploadBatchResponse";
import type { UploadImageParams } from "@/src/lib/types/all/uploadImageParams";
import { dispatchFileRefresh } from "@/src/lib/events/editor";
import { getProjectFilesCache, setProjectFilesCache, type CachedUploadedFile } from "@/src/stores/projectFilesCache";
import { withTimeout } from "@/src/lib/upload/withTimeout";
import { isRecord } from "@/src/lib/types/guards";
import {
  isSlidesSourceFileName,
  resolveUploadedProjectFileKind,
} from "@/src/lib/files/projectFileKinds";
import { isUnsupportedSpreadsheetFile } from "@/src/lib/files/unsupportedSpreadsheetFiles";
import { pickSizeBytes } from "@/src/lib/files/fileSize";
import { toProjectFileDisplayName } from "@/src/lib/files/projectFileName";
import { debugFlagLog } from "@/src/lib/debug";
import { isDebugEnabled } from "@/src/lib/storage/localStorageManager";
import {
  completeUploadedProjectFileUpload,
  type UploadedProjectFileForPipeline,
} from "./projectFileUploadPipeline";
import {
  normalizeProjectFileUploadSource,
  type ProjectFileUploadSource,
} from "./uploadSource";
import { reportBusinessOperationDuration } from "@/src/lib/monitoring/performanceMetrics";

export type UploadedProjectFile = {
  type: "image" | "pdf" | "office" | "note" | "slides" | "sheet";
  cacheKey: string;
  objectKey?: string;
  sourceFileId?: string;
  storageChannel?: string;
  url?: string;
  visibility?: string;
  name: string;
  rawName?: string;
  projectId?: string | null;
  previewUrl?: string | null;
  file?: File;
  documentId?: string;
  sizeBytes?: number;
  local?: boolean;
};

type UploadProjectFileOptions = {
  kind: "image" | "pdf" | "office" | "note" | "slides" | "sheet";
  file: File;
  projectId?: string | null;
  sessionId?: string | null;
  includePreviewUrl?: boolean;
  uploadSource?: ProjectFileUploadSource;
  tFiles: (key: string) => string;
};

type UploadProjectFileBatchItem = Pick<UploadProjectFileOptions, "file" | "includePreviewUrl" | "kind">;

type UploadProjectFilesBatchOptions = {
  files: UploadProjectFileBatchItem[];
  projectId: string;
  uploadSource?: ProjectFileUploadSource;
  tFiles: (key: string) => string;
};

type UploadInfo = {
  cacheKey?: string;
  ossUrl?: string;
  fileName?: string;
  objectKey?: string;
  sourceFileId?: string;
  storageChannel?: string;
  documentId?: string;
  sizeBytes?: number;
  visibility?: string;
};

type UploadFileParamsWithSource = UploadFileParams & {
  uploadSource: ProjectFileUploadSource;
};

type UploadFilesParamsWithSource = UploadFilesParams & {
  uploadSource: ProjectFileUploadSource;
};

type UploadImageParamsWithSource = UploadImageParams & {
  uploadSource: ProjectFileUploadSource;
};

function extractUploadInfo(resp: unknown): UploadInfo {
  let layer: unknown = resp;
  for (let i = 0; i < 2; i += 1) {
    if (isRecord(layer) && "data" in layer) {
      layer = (layer as Record<string, unknown>).data;
    }
  }

  const out: UploadInfo = {};
  if (isRecord(layer)) {
    if (typeof layer.cacheKey === "string") out.cacheKey = layer.cacheKey;
    if (typeof layer.ossUrl === "string") out.ossUrl = layer.ossUrl;
    if (!out.ossUrl && typeof layer.accessUrl === "string") out.ossUrl = layer.accessUrl;
    if (!out.ossUrl && typeof layer.publicUrl === "string") out.ossUrl = layer.publicUrl;
    if (typeof layer.fileName === "string") out.fileName = layer.fileName;
    if (typeof layer.objectKey === "string") out.objectKey = layer.objectKey;
    if (typeof layer.storageChannel === "string") out.storageChannel = layer.storageChannel;
    if (typeof layer.visibility === "string") out.visibility = layer.visibility;
    const sizeBytes = pickSizeBytes(layer);
    if (sizeBytes !== undefined) out.sizeBytes = sizeBytes;

    const sourceFileId = layer.sourceFileId;
    if (sourceFileId !== undefined && sourceFileId !== null && sourceFileId !== 0) {
      out.sourceFileId = String(sourceFileId);
    }

    const documentId = layer.documentId;
    if (documentId !== undefined && documentId !== null && documentId !== 0) {
      out.documentId = String(documentId);
    }
  }

  return out;
}

function buildUploadFileParams(
  projectId?: string | null,
  uploadSource?: ProjectFileUploadSource,
): UploadFileParamsWithSource | undefined {
  if (!projectId) {
    return undefined;
  }

  return {
    projectId,
    uploadSource: normalizeProjectFileUploadSource(uploadSource),
  };
}

function buildUploadFilesParams(
  projectId: string,
  uploadSource?: ProjectFileUploadSource,
): UploadFilesParamsWithSource {
  return {
    projectId,
    uploadSource: normalizeProjectFileUploadSource(uploadSource),
  };
}

function buildUploadImageParams(
  projectId: string,
  uploadSource?: ProjectFileUploadSource,
): UploadImageParamsWithSource {
  return {
    projectId,
    uploadSource: normalizeProjectFileUploadSource(uploadSource),
  };
}

function buildUploadedProjectFile(opts: {
  file: File;
  includePreviewUrl?: boolean;
  info: UploadInfo;
  kind: UploadProjectFileOptions["kind"];
  projectId: string;
}): UploadedProjectFile {
  const { file, includePreviewUrl = false, info, kind, projectId } = opts;
  return {
    // Pitfall: backend upload no longer preserves raw Office files in Docs. The
    // returned fileName already represents the converted project artifact, so
    // final type must be derived from that result instead of the request kind.
    type: resolveUploadedProjectFileKind({
      requestedKind: kind,
      originalFileName: file.name,
      uploadedFileName: info.fileName,
    }),
    cacheKey: info.cacheKey ?? "",
    ...(info.objectKey ? { objectKey: info.objectKey } : {}),
    ...(info.sourceFileId ? { sourceFileId: info.sourceFileId } : {}),
    ...(info.storageChannel ? { storageChannel: info.storageChannel } : {}),
    url: info.ossUrl,
    ...(info.visibility ? { visibility: info.visibility } : {}),
    name: toProjectFileDisplayName(info.fileName || file.name),
    rawName: info.fileName || file.name,
    projectId,
    ...(includePreviewUrl && kind === "image" ? { previewUrl: URL.createObjectURL(file), file } : {}),
    ...(info.documentId ? { documentId: info.documentId } : {}),
    ...(info.sizeBytes !== undefined ? { sizeBytes: info.sizeBytes } : {}),
    local: true,
  };
}

function toCachedProjectFile(file: UploadedProjectFileForPipeline): CachedUploadedFile {
  return {
    type: file.type,
    cacheKey: file.cacheKey,
    url: file.url,
    name: file.name,
    rawName: file.rawName,
    documentId: file.documentId,
    sizeBytes: file.sizeBytes,
  };
}

async function upsertProjectFileCache(
  projectId: string,
  file: UploadedProjectFileForPipeline,
) {
  const existing = await getProjectFilesCache(projectId);
  const next = [...(existing?.files ?? [])];
  const idx = next.findIndex((candidate) => candidate.cacheKey === file.cacheKey);
  const cachedFile = toCachedProjectFile(file);

  if (idx >= 0) {
    next[idx] = { ...next[idx], ...cachedFile };
  } else {
    next.unshift(cachedFile);
  }

  await setProjectFilesCache(projectId, next);
}

export async function publishUploadedProjectFileToMaterials(
  projectId: string,
  file: UploadedProjectFileForPipeline,
) {
  try {
    await upsertProjectFileCache(projectId, file);
  } catch {
    // Cache writes are best-effort only.
  }
  scheduleProjectFileRefresh();
}

function scheduleProjectFileRefresh() {
  dispatchFileRefresh();
}

function logPowerPointImportStage(
  stage: string,
  payload: Record<string, unknown>,
) {
  if (!isDebugEnabled.officeUpload()) {
    return;
  }

  debugFlagLog(isDebugEnabled.officeUpload(), "[uploadProjectFile] ppt-import", {
    stage,
    ...payload,
  });
}

function buildUploadFileFingerprint(file: File): string {
  return [
    file.name,
    file.size,
    file.type || "",
    Number.isFinite(file.lastModified) ? file.lastModified : 0,
  ].join(":");
}

export function buildUploadProjectFileSingleFlightKey(
  opts: Pick<UploadProjectFileOptions, "kind" | "file" | "projectId" | "sessionId" | "uploadSource">,
): string {
  return [
    "upload",
    opts.projectId ?? "no-project",
    opts.sessionId ?? "no-session",
    normalizeProjectFileUploadSource(opts.uploadSource),
    opts.kind,
    buildUploadFileFingerprint(opts.file),
  ].join("|");
}

async function performUploadProjectFile(
  opts: UploadProjectFileOptions,
): Promise<UploadedProjectFile> {
  const { kind, file, projectId, sessionId, includePreviewUrl = false, tFiles } = opts;
  const uploadSource = normalizeProjectFileUploadSource(opts.uploadSource);
  const isPowerPointImport = isSlidesSourceFileName(file.name);

  // Web rule: every image upload goes through the project-files pipeline first.
  // Chat input must reference that uploaded project file via FileChip instead of
  // creating a second, ad-hoc image attachment storage path.
  if (!projectId) {
    throw new Error(tFiles("uploadFailedNoProject"));
  }

  if (isUnsupportedSpreadsheetFile(file)) {
    throw new Error(tFiles("spreadsheetUnsupported"));
  }

  const fileParams = buildUploadFileParams(projectId, uploadSource);
  const imageParams = buildUploadImageParams(projectId, uploadSource);

  if (isPowerPointImport) {
    logPowerPointImportStage("upload-start", {
      fileName: file.name,
      requestedKind: kind,
      projectId,
      sessionId: sessionId ?? null,
      size: file.size,
      mimeType: file.type,
    });
  }

  const response =
    kind === "image"
      ? await uploadImage({ file }, imageParams)
      : await withTimeout(
          uploadFile({ file }, fileParams),
          60000,
          "Upload timed out (60s)"
        );

  const info = extractUploadInfo(response);
  if (!info.cacheKey) {
    throw new Error(tFiles("uploadFailedNoCacheKey"));
  }

  const uploadedFile = buildUploadedProjectFile({
    file,
    includePreviewUrl,
    info,
    kind,
    projectId,
  });
  if (isPowerPointImport) {
    logPowerPointImportStage("upload-finished", {
      fileName: file.name,
      cacheKey: uploadedFile.cacheKey,
      uploadedType: uploadedFile.type,
      uploadedName: uploadedFile.name,
      rawName: uploadedFile.rawName ?? null,
      documentId: uploadedFile.documentId ?? null,
      hasUrl: Boolean(uploadedFile.url),
    });
  }

  // PPT/PPTX imports currently publish directly after upload. The local web
  // app no longer hangs any product path off the temporary Next.js AI routes,
  // so post-process normalization stays disconnected until the backend-owned
  // replacement is ready to be wired back in.
  await completeUploadedProjectFileUpload({
    originalFileName: file.name,
    projectId,
    uploadedFile,
  }, {
    publishUploadedProjectFileToMaterials,
    logPowerPointImportStage,
  });

  return uploadedFile;
}

export function startUploadProjectFile(opts: UploadProjectFileOptions): {
  promise: Promise<UploadedProjectFile>;
  started: boolean;
  key: string;
} {
  const key = buildUploadProjectFileSingleFlightKey(opts);
  const { promise, started } = startManagedSingleFlight(
    key,
    async () => {
      const startedAt = performance.now();
      try {
        const result = await performUploadProjectFile(opts);
        reportBusinessOperationDuration("file_upload", performance.now() - startedAt, {
          kind: opts.kind,
          outcome: "success",
          source: normalizeProjectFileUploadSource(opts.uploadSource),
        });
        return result;
      } catch (error) {
        reportBusinessOperationDuration("file_upload", performance.now() - startedAt, {
          kind: opts.kind,
          outcome: "error",
          source: normalizeProjectFileUploadSource(opts.uploadSource),
        });
        throw error;
      }
    },
  );

  return {
    promise,
    started,
    key,
  };
}

export async function uploadProjectFile(
  opts: UploadProjectFileOptions,
): Promise<UploadedProjectFile> {
  return startUploadProjectFile(opts).promise;
}

export async function uploadProjectFilesBatch(
  opts: UploadProjectFilesBatchOptions,
): Promise<UploadedProjectFile[]> {
  const { files, projectId, tFiles } = opts;
  const uploadSource = normalizeProjectFileUploadSource(opts.uploadSource);
  if (!projectId) {
    throw new Error(tFiles("uploadFailedNoProject"));
  }
  if (files.length === 0) {
    return [];
  }

  for (const item of files) {
    if (isUnsupportedSpreadsheetFile(item.file)) {
      throw new Error(tFiles("spreadsheetUnsupported"));
    }
  }

  const response = await withTimeout(
    uploadFilesBatchRequest(
      { files: files.map((item) => item.file) },
      buildUploadFilesParams(projectId, uploadSource),
    ),
    120000,
    "Upload timed out (120s)"
  );
  const payload = unwrapGeneratedPayload<FileUploadBatchResponse>(response);
  const batchFiles = Array.isArray(payload?.files) ? payload.files : [];
  if (batchFiles.length !== files.length) {
    throw new Error(tFiles("uploadFailedNoCacheKey"));
  }

  const uploadedFiles: UploadedProjectFile[] = [];
  for (let index = 0; index < files.length; index += 1) {
    const item = files[index];
    const file = item.file;
    const info = extractUploadInfo(batchFiles[index]);
    if (!info.cacheKey) {
      throw new Error(tFiles("uploadFailedNoCacheKey"));
    }

    const uploadedFile = buildUploadedProjectFile({
      file,
      includePreviewUrl: item.includePreviewUrl,
      info,
      kind: item.kind,
      projectId,
    });
    await completeUploadedProjectFileUpload({
      originalFileName: file.name,
      projectId,
      uploadedFile,
    }, {
      publishUploadedProjectFileToMaterials,
      logPowerPointImportStage,
    });
    uploadedFiles.push(uploadedFile);
  }

  return uploadedFiles;
}
