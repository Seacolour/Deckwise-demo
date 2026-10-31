import { isRecord, pickString } from "@/src/lib/types/guards";

// TODO(orval-runtime):
// Keep wrapper-level compatibility helpers for now instead of trying to
// dynamically unify every generated client return type at the transport layer.
//
// Rationale:
// - backend still uses generic BaseResponse<T> widely
// - frontend business flows are currently stable with explicit wrapper mapping
// - forcing a fully dynamic runtime-side unwrapping contract would increase
//   migration risk and may create subtle shape mismatches
//
// Revisit only after:
// 1. OpenAPI output is stable for a sustained period
// 2. More wrappers have been migrated to generated clients
// 3. We have enough confidence to standardize generated function return types
export function unwrapGeneratedPayload<T>(response: unknown): T {
  if (isRecord(response) && "data" in response) {
    const nested = response.data;
    if (isRecord(nested) && "data" in nested) {
      return nested.data as T;
    }
    return nested as T;
  }

  return response as T;
}

export function unwrapGeneratedBoolean(response: unknown): boolean {
  const payload = unwrapGeneratedPayload<unknown>(response);
  if (typeof payload === "boolean") {
    return payload;
  }

  if (typeof payload === "string") {
    if (payload === "true") {
      return true;
    }
    if (payload === "false") {
      return false;
    }
  }

  if (isRecord(payload)) {
    const raw = pickString(payload, ["data"]);
    if (raw === "true") {
      return true;
    }
    if (raw === "false") {
      return false;
    }
  }

  return Boolean(payload);
}
