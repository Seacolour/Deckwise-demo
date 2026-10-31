import {
  isProjectOutlineFileName,
  PROJECT_FILE_SLIDE_COMPOUND_SUFFIXES,
  toProjectCanonicalJsonFileName,
} from "./projectFileName";

export type TextDocumentKind = "note" | "slides";
export type ProjectFileKind = "image" | "pdf" | "sheet" | "office" | TextDocumentKind;

const SHEET_EXTENSIONS = new Set(["xlsx"]);
const WORD_SOURCE_EXTENSIONS = new Set(["doc", "docx"]);
const SLIDES_SOURCE_EXTENSIONS = new Set(["ppt", "pptx"]);
const NOTE_EXTENSIONS = new Set(["md", "txt"]);

function pad2(value: number) {
  return String(value).padStart(2, "0");
}

function buildTimestamp(now: Date) {
  const y = now.getFullYear();
  const m = pad2(now.getMonth() + 1);
  const d = pad2(now.getDate());
  const hh = pad2(now.getHours());
  const mm = pad2(now.getMinutes());
  const ss = pad2(now.getSeconds());
  return `${y}${m}${d}-${hh}${mm}${ss}`;
}

function getLowerName(fileName: string) {
  return fileName.trim().toLowerCase();
}

function getExtension(fileName: string) {
  const normalized = getLowerName(fileName);
  const parts = normalized.split(".");
  return parts.length > 1 ? parts[parts.length - 1] : "";
}

export function isTextDocumentKind(kind: string | null | undefined): kind is TextDocumentKind {
  return kind === "note" || kind === "slides";
}

export function isSlideFileName(fileName: string): boolean {
  const normalized = getLowerName(fileName);
  return PROJECT_FILE_SLIDE_COMPOUND_SUFFIXES.some((suffix) => normalized.endsWith(suffix));
}

export function isJsonFileName(fileName: string): boolean {
  return getExtension(fileName) === "json";
}

export function isSheetFileName(fileName: string): boolean {
  return SHEET_EXTENSIONS.has(getExtension(fileName));
}

export function isWordSourceFileName(fileName: string): boolean {
  return WORD_SOURCE_EXTENSIONS.has(getExtension(fileName));
}

export function isSlidesSourceFileName(fileName: string): boolean {
  return SLIDES_SOURCE_EXTENSIONS.has(getExtension(fileName));
}

export function isNoteFileName(fileName: string): boolean {
  if (isProjectOutlineFileName(fileName)) return false;
  if (isSlideFileName(fileName)) return false;
  return NOTE_EXTENSIONS.has(getExtension(fileName)) || isWordSourceFileName(fileName);
}

export function resolveProjectFileKind(fileName: string): ProjectFileKind {
  const normalized = getLowerName(fileName);

  if (/\.(avif|bmp|gif|heic|heif|jpe?g|png|svg|webp)$/i.test(normalized)) return "image";
  if (normalized.endsWith(".pdf")) return "pdf";
  if (isProjectOutlineFileName(normalized)) return "slides";
  if (isSlideFileName(normalized)) return "slides";
  // Project files represent the backend-converted result, not the original
  // office source upload. Treat raw PowerPoint names as slides and Word names
  // as notes so the Docs area never routes them back into an office viewer.
  if (isSlidesSourceFileName(normalized)) return "slides";
  if (isSheetFileName(normalized)) return "sheet";
  if (isNoteFileName(normalized)) return "note";
  return "note";
}

export function resolveUploadedProjectFileKind(opts: {
  requestedKind: ProjectFileKind;
  originalFileName: string;
  uploadedFileName?: string;
}): ProjectFileKind {
  const uploadedFileName = opts.uploadedFileName?.trim();
  if (uploadedFileName) {
    if (
      isJsonFileName(uploadedFileName) &&
      (
        opts.requestedKind === "sheet" ||
        resolveProjectFileKind(opts.originalFileName) === "sheet"
      )
    ) {
      return "sheet";
    }

    // Backend Office conversion may emit a plain `.md` filename for PowerPoint
    // imports before the follow-up slides normalization runs. Preserve the
    // source semantic here so the deck still opens in slides mode and the
    // post-process helper can rewrite the draft into canonical deck markdown.
    if (isSlidesSourceFileName(opts.originalFileName)) {
      return "slides";
    }

    const resolvedFromUploadedName = resolveProjectFileKind(uploadedFileName);
    if (resolvedFromUploadedName !== "office") {
      return resolvedFromUploadedName;
    }
  }

  if (opts.requestedKind !== "office") {
    return opts.requestedKind;
  }

  const resolvedFromOriginalName = resolveProjectFileKind(opts.originalFileName);
  return resolvedFromOriginalName === "office" ? "note" : resolvedFromOriginalName;
}

export function makeTextFileName(kind: TextDocumentKind, now = new Date()) {
  const stamp = buildTimestamp(now);
  return kind === "slides"
    ? toProjectCanonicalJsonFileName(`slides-${stamp}`)
    : "New note.md";
}
