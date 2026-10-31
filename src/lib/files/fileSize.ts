export function normalizeSizeBytes(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value) && value >= 0) {
    return value;
  }

  if (typeof value !== "string" || value.trim() === "") {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
}

export function pickSizeBytes(
  obj: Record<string, unknown>,
  keys: readonly string[] = ["sizeBytes", "size_bytes", "fileSize", "file_size"],
): number | undefined {
  for (const key of keys) {
    const sizeBytes = normalizeSizeBytes(obj[key]);
    if (sizeBytes !== undefined) {
      return sizeBytes;
    }
  }
  return undefined;
}

export function formatFileSize(sizeBytes: number | undefined): string | null {
  if (sizeBytes === undefined || !Number.isFinite(sizeBytes) || sizeBytes < 0) {
    return null;
  }

  const units = ["B", "KB", "MB", "GB"] as const;
  let value = sizeBytes;
  let unitIndex = 0;

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  const maximumFractionDigits = unitIndex === 0 || value >= 10 ? 0 : 1;
  return `${new Intl.NumberFormat(undefined, {
    maximumFractionDigits,
  }).format(value)} ${units[unitIndex]}`;
}
