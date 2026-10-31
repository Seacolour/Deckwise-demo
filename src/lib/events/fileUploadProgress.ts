import type { UploadedProjectFile } from "@/src/lib/upload/projectFiles";
import { safeDispatchEvent } from "@/src/lib/utils/errorHandler";

export const FILE_UPLOAD_PROGRESS_EVENTS = {
  STARTED: "file-upload:started",
  SUCCEEDED: "file-upload:succeeded",
  FAILED: "file-upload:failed",
} as const;

export type FileUploadKind = "image" | "pdf" | "office" | "note" | "slides" | "sheet";

export type FileUploadStartedDetail = {
  projectId: string;
  tempCacheKey: string;
  type: FileUploadKind;
  name: string;
  rawName?: string;
  file?: File;
  sizeBytes?: number;
};

export type FileUploadSucceededDetail = {
  projectId: string;
  tempCacheKey: string;
  file: UploadedProjectFile;
};

export type FileUploadFailedDetail = {
  projectId: string;
  tempCacheKey: string;
};

export function createTempUploadCacheKey(): string {
  return `uploading:${Date.now()}:${Math.random().toString(36).slice(2, 10)}`;
}

export function dispatchFileUploadStarted(detail: FileUploadStartedDetail): void {
  safeDispatchEvent(new CustomEvent(FILE_UPLOAD_PROGRESS_EVENTS.STARTED, { detail }));
}

export function dispatchFileUploadSucceeded(detail: FileUploadSucceededDetail): void {
  safeDispatchEvent(new CustomEvent(FILE_UPLOAD_PROGRESS_EVENTS.SUCCEEDED, { detail }));
}

export function dispatchFileUploadFailed(detail: FileUploadFailedDetail): void {
  safeDispatchEvent(new CustomEvent(FILE_UPLOAD_PROGRESS_EVENTS.FAILED, { detail }));
}
