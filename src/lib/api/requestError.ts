import { isRecord, pickString } from "@/src/lib/types/guards";

export const REQUEST_TIMEOUT_CODE = "REQUEST_TIMEOUT" as const;

type RequestErrorMessageOptions = {
  networkErrorMessage?: string;
  serverErrorMessage?: string;
  serviceUnavailableMessage?: string;
  timeoutMessage?: string;
  fallbackMessage?: string;
};

export class RequestTimeoutError extends Error {
  code = REQUEST_TIMEOUT_CODE;
  timeoutMs: number;

  constructor(timeoutMs: number) {
    super("Request timed out.");
    this.name = "TimeoutError";
    this.timeoutMs = timeoutMs;
  }
}

export function isRequestTimeoutError(error: unknown): error is RequestTimeoutError {
  if (error instanceof RequestTimeoutError) {
    return true;
  }

  if (!(error instanceof Error)) {
    return false;
  }

  if (error.name === "TimeoutError") {
    return true;
  }

  return isRecord(error) && error.code === REQUEST_TIMEOUT_CODE;
}

export function getRequestErrorCode(error: unknown): number | null {
  if (!isRecord(error)) {
    return null;
  }

  const code = error.code;
  if (typeof code === "number" && Number.isFinite(code)) {
    return code;
  }

  const status = error.status;
  if (typeof status === "number" && Number.isFinite(status)) {
    return status;
  }

  return null;
}

export function isRequestNetworkError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false;
  }

  return /\b(fetch failed|failed to fetch|load failed|network(?:error)?)\b/i.test(error.message);
}

export function isRequestServerError(error: unknown): boolean {
  const code = getRequestErrorCode(error);
  return code !== null && code >= 500 && code < 600;
}

export function getRequestErrorMessage(
  error: unknown,
  options: RequestErrorMessageOptions = {},
): string {
  const {
    fallbackMessage = "Unknown error.",
    networkErrorMessage,
    serverErrorMessage,
    serviceUnavailableMessage,
    timeoutMessage,
  } = options;

  if (isRequestTimeoutError(error) && timeoutMessage) {
    return timeoutMessage;
  }

  const errorCode = getRequestErrorCode(error);
  if ((errorCode === 503 || errorCode === 504) && serviceUnavailableMessage) {
    return serviceUnavailableMessage;
  }

  if (isRequestServerError(error) && serverErrorMessage) {
    return serverErrorMessage;
  }

  if (isRequestNetworkError(error) && networkErrorMessage) {
    return networkErrorMessage;
  }

  if (typeof error === "string") {
    const trimmed = error.trim();
    return trimmed || fallbackMessage;
  }

  if (error instanceof Error) {
    const trimmed = error.message.trim();
    return trimmed || fallbackMessage;
  }

  if (isRecord(error)) {
    const directMessage = pickString(error, ["message", "msg", "detail", "error"]);
    if (directMessage) {
      return directMessage;
    }

    if ("payload" in error && isRecord(error.payload)) {
      const payloadMessage = pickString(error.payload, ["message", "msg", "detail", "error"]);
      if (payloadMessage) {
        return payloadMessage;
      }
    }
  }

  return fallbackMessage;
}
