"use client";

import { HttpStatus } from "@/src/lib/config/constants/api";
import { isRecord, pickString } from "@/src/lib/types/guards";

const BUSINESS_NOT_FOUND_CODE = 40400;

const PROJECT_MISSING_MESSAGE_PATTERNS = [
  /项目不存在/u,
  /\bproject\s+(?:not\s+found|does\s+not\s+exist|missing)\b/i,
] as const;

function collectNumericCodes(value: unknown, out = new Set<number>()): Set<number> {
  if (!isRecord(value)) {
    return out;
  }

  const directCode = value.code;
  const directStatus = value.status;
  if (typeof directCode === "number") {
    out.add(directCode);
  }
  if (typeof directStatus === "number") {
    out.add(directStatus);
  }

  if ("payload" in value) {
    collectNumericCodes(value.payload, out);
  }

  return out;
}

function extractMessage(value: unknown): string | null {
  if (typeof value === "string") {
    const trimmed = value.trim();
    return trimmed || null;
  }

  if (!isRecord(value)) {
    return null;
  }

  const direct = pickString(value, ["message", "msg", "detail", "error"]);
  if (direct) {
    return direct;
  }

  if ("payload" in value) {
    return extractMessage(value.payload);
  }

  return null;
}

export function isMissingResourceCode(code: number | undefined): boolean {
  return code === HttpStatus.NOT_FOUND || code === BUSINESS_NOT_FOUND_CODE;
}

export function isProjectMissingError(error: unknown): boolean {
  const codes = collectNumericCodes(error);
  for (const code of codes) {
    if (isMissingResourceCode(code)) {
      return true;
    }
  }

  const message = extractMessage(error);
  if (!message) {
    return false;
  }

  return PROJECT_MISSING_MESSAGE_PATTERNS.some((pattern) => pattern.test(message));
}

export function extractProjectIdFromApiUrl(url: string): string | null {
  if (!url) {
    return null;
  }

  try {
    const parsed = new URL(url, "https://deckwise.local");
    const parts = parsed.pathname.split("/").filter(Boolean);
    const projectsIndex = parts.findIndex((part) => part === "projects");
    if (projectsIndex < 0) {
      return null;
    }

    const candidate = parts[projectsIndex + 1] ?? "";
    if (!candidate || candidate === "list") {
      return null;
    }

    return candidate;
  } catch {
    return null;
  }
}
