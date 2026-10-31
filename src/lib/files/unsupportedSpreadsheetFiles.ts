const UNSUPPORTED_SPREADSHEET_EXTENSIONS = new Set(["csv", "xls"]);

const UNSUPPORTED_SPREADSHEET_MIME_TYPES = new Set([
  "text/csv",
  "application/csv",
  "application/vnd.ms-excel",
]);

const UNSUPPORTED_SPREADSHEET_SEMANTIC_TYPES = new Set([
  "xls",
  "csv",
]);

function getExtension(fileName: string): string {
  const parts = fileName.trim().toLowerCase().split(".");
  return parts.length > 1 ? parts[parts.length - 1] ?? "" : "";
}

export function isUnsupportedSpreadsheetFileName(fileName: string): boolean {
  return UNSUPPORTED_SPREADSHEET_EXTENSIONS.has(getExtension(fileName));
}

export function isUnsupportedSpreadsheetMimeType(mimeType: string): boolean {
  return UNSUPPORTED_SPREADSHEET_MIME_TYPES.has((mimeType || "").toLowerCase());
}

export function isUnsupportedSpreadsheetSemanticType(value: unknown): boolean {
  return typeof value === "string" &&
    UNSUPPORTED_SPREADSHEET_SEMANTIC_TYPES.has(value.trim().toLowerCase());
}

export function isUnsupportedSpreadsheetFile(file: Pick<File, "name" | "type">): boolean {
  return isUnsupportedSpreadsheetFileName(file.name) ||
    isUnsupportedSpreadsheetMimeType(file.type);
}
