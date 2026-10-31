import { EVENTS } from "@/src/lib/config/constants/events";
import { safeDispatchEvent } from "@/src/lib/utils/errorHandler";

// Keep editor-related window events in one place so the embedded editor
// module and host shell do not drift on literal strings.
export const EDITOR_EVENTS = {
  FILE_ACTIVE: EVENTS.FILE_ACTIVE,
  FILE_DISPLAY: EVENTS.FILE_DISPLAY,
  NOTE_OPEN: EVENTS.NOTE_OPEN,
  LEGACY_FILE_DISPLAY: "middle:display",
  LEGACY_NOTE_OPEN: "middle:editor",
  NOTE_CONTENT_UPDATE: "note:content-update",
  NOTE_REFETCH: "note:refetch",
  NOTE_CREATE_REQUEST: "note:create-request",
  FILE_CREATE_REQUEST: "file:create-request",
  FILE_REFRESH: "file:refresh",
  LOCAL_FILE_UPLOADED: "file:local-uploaded",
  FILE_REMOVED: "file:removed",
  FILE_UPLOAD_REQUEST: "file:upload-request",
  FILE_RENAME_REQUEST: "file:rename-request",
  FILE_ACTION_REQUEST: "file:action-request",
  DOCUMENT_SAVED: "document:saved",
  PROJECT_DOCUMENT_SAVE_REQUEST: "project:document-save-request",
  PROJECT_COVER_IMAGE_UPDATED: "project:cover-image-updated",
  SLIDES_DECK_CREATED: "slides:deck-created",
  SLIDES_CONTENT_CHANGED: "slides:content-changed",
  SLIDES_THEME_CHANGED: "slides:theme-changed",
  SLIDES_PREVIEW_NAVIGATE: "slides:preview:navigate",
} as const;

export type FileActiveDetail = {
  cacheKey?: string | null;
};

export type NoteContentUpdateDetail = {
  cacheKey?: string;
  content?: string;
  documentId?: string | null;
  version?: number | null;
};

export type NoteRefetchDetail = {
  cacheKey?: string;
};

export type FileRemovedDetail = {
  cacheKey?: string;
};

export type LocalFileUploadedDetail = {
  cacheKey: string;
  documentId?: string | null;
  localCacheKey: string;
  projectId?: string | null;
};

export type FileRenameRequestDetail = {
  projectId?: string | null;
  cacheKey?: string | null;
  documentId?: string | null;
  sourceId?: string | null;
  nextName?: string | null;
};

export type FileActionRequestAction =
  | "duplicate"
  | "download"
  | "export"
  | "openInNewTab"
  | "remove";

export type FileActionRequestDetail = {
  action?: FileActionRequestAction | null;
  projectId?: string | null;
  cacheKey?: string | null;
  documentId?: string | null;
  sourceId?: string | null;
};

export type DocumentSavedDetail = {
  documentId: number | null;
  toolName: string;
};

export type ProjectDocumentSaveRequestDetail = {
  projectId?: string | null;
};

export type ProjectCoverImageUpdatedDetail = {
  coverImage: string;
  projectId: string;
};

export type SlidesDeckCreatedDetail = {
  cacheKey?: string | null;
  coverImagePrepared?: boolean | null;
  documentId: string;
  fileId?: string | null;
  fileName?: string | null;
  handoff?: "streaming-deck" | null;
  projectId: string;
  thumbnailUploadPending?: boolean | null;
};

export type SlidesThemeChangedDetail = {
  documentId: number | string | null;
  projectId?: string | null;
  themeId?: string | null;
  toolName: string;
};

export type SlidesContentChangedDetail = {
  documentId: number | string | null;
  projectId?: string | null;
  reason: "agent-tools";
  toolNames: string[];
};

export function dispatchFileActive(detail: FileActiveDetail) {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.FILE_ACTIVE, { detail }));
}

export function dispatchFileRefresh() {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.FILE_REFRESH));
}

export function dispatchLocalFileUploaded(detail: LocalFileUploadedDetail) {
  safeDispatchEvent(new CustomEvent<LocalFileUploadedDetail>(
    EDITOR_EVENTS.LOCAL_FILE_UPLOADED,
    { detail },
  ));
}

export function dispatchDocumentSaved(detail: DocumentSavedDetail) {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.DOCUMENT_SAVED, { detail }));
}

export function dispatchProjectDocumentSaveRequest(detail: ProjectDocumentSaveRequestDetail) {
  safeDispatchEvent(new CustomEvent<ProjectDocumentSaveRequestDetail>(
    EDITOR_EVENTS.PROJECT_DOCUMENT_SAVE_REQUEST,
    { detail },
  ));
}

export function dispatchProjectCoverImageUpdated(detail: ProjectCoverImageUpdatedDetail) {
  safeDispatchEvent(new CustomEvent<ProjectCoverImageUpdatedDetail>(
    EDITOR_EVENTS.PROJECT_COVER_IMAGE_UPDATED,
    { detail },
  ));
}

export function dispatchSlidesDeckCreated(detail: SlidesDeckCreatedDetail) {
  safeDispatchEvent(new CustomEvent<SlidesDeckCreatedDetail>(
    EDITOR_EVENTS.SLIDES_DECK_CREATED,
    { detail },
  ));
}

export function dispatchSlidesContentChanged(detail: SlidesContentChangedDetail) {
  safeDispatchEvent(new CustomEvent<SlidesContentChangedDetail>(
    EDITOR_EVENTS.SLIDES_CONTENT_CHANGED,
    { detail },
  ));
}

export function dispatchSlidesThemeChanged(detail: SlidesThemeChangedDetail) {
  safeDispatchEvent(new CustomEvent<SlidesThemeChangedDetail>(
    EDITOR_EVENTS.SLIDES_THEME_CHANGED,
    { detail },
  ));
}

export function dispatchNoteContentUpdate(detail: NoteContentUpdateDetail) {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.NOTE_CONTENT_UPDATE, { detail }));
}

export function dispatchNoteRefetch(detail: NoteRefetchDetail) {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.NOTE_REFETCH, { detail }));
}

export function dispatchNoteCreateRequest() {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.NOTE_CREATE_REQUEST));
}

export type FileCreateKind = "note";

export type FileCreateRequestDetail = {
  kind?: FileCreateKind;
};

export function dispatchFileCreateRequest(kind: FileCreateKind) {
  safeDispatchEvent(new CustomEvent<FileCreateRequestDetail>(
    EDITOR_EVENTS.FILE_CREATE_REQUEST,
    {
      detail: {
        kind,
      },
    },
  ));
}

export function dispatchFileUploadRequest() {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.FILE_UPLOAD_REQUEST));
}

export function dispatchFileRenameRequest(detail: FileRenameRequestDetail) {
  safeDispatchEvent(new CustomEvent<FileRenameRequestDetail>(
    EDITOR_EVENTS.FILE_RENAME_REQUEST,
    { detail },
  ));
}

export function dispatchFileActionRequest(detail: FileActionRequestDetail) {
  safeDispatchEvent(new CustomEvent<FileActionRequestDetail>(
    EDITOR_EVENTS.FILE_ACTION_REQUEST,
    { detail },
  ));
}

export function dispatchFileRemoved(detail: FileRemovedDetail) {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.FILE_REMOVED, { detail }));
}

export type SlidesPreviewNavigateDetail = {
  page: number;
};

export function dispatchSlidesPreviewNavigate(detail: SlidesPreviewNavigateDetail) {
  safeDispatchEvent(new CustomEvent(EDITOR_EVENTS.SLIDES_PREVIEW_NAVIGATE, { detail }));
}
