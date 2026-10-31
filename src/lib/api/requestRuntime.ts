"use client";

import { safeGet } from "@/src/lib/utils/errorHandler";

export type ManagedRequestKind =
  | "query"
  | "mutation"
  | "upload"
  | "stream"
  | "proxy"
  | "unknown";

export type ManagedRequestEntry = {
  id: string;
  path: string;
  method: string;
  kind: ManagedRequestKind;
  authSensitive: boolean;
  startedAt: number;
};

type ManagedRequestInternalEntry = ManagedRequestEntry & {
  controller: AbortController;
};

const activeRequests = new Map<string, ManagedRequestInternalEntry>();
const inFlightSingleFlights = new Map<string, Promise<unknown>>();

function randomSuffix(): string {
  return Math.random().toString(36).slice(2, 10);
}

function buildRequestId(): string {
  return `req-${Date.now().toString(36)}-${randomSuffix()}`;
}

function createAbortReason(message: string): Error {
  if (typeof DOMException === "function") {
    return new DOMException(message, "AbortError");
  }

  const error = new Error(message);
  error.name = "AbortError";
  return error;
}

function getAbortReason(signal?: AbortSignal): unknown {
  if (!signal) {
    return undefined;
  }

  return (signal as AbortSignal & { reason?: unknown }).reason;
}

function resolveAbortReason(signal?: AbortSignal, fallbackMessage?: string): Error {
  const reason = getAbortReason(signal);
  if (reason instanceof Error) {
    return reason;
  }

  return createAbortReason(fallbackMessage ?? "The operation was aborted.");
}

function composeAbortSignal(signals: Array<AbortSignal | null | undefined>): {
  signal?: AbortSignal;
  cleanup: () => void;
} {
  const activeSignals = signals.filter((signal): signal is AbortSignal => Boolean(signal));
  if (activeSignals.length <= 1) {
    return {
      signal: activeSignals[0],
      cleanup: () => {},
    };
  }

  const controller = new AbortController();
  const listeners = new Map<AbortSignal, () => void>();

  const cleanup = () => {
    listeners.forEach((listener, signal) => {
      signal.removeEventListener("abort", listener);
    });
    listeners.clear();
  };

  const abortFrom = (signal: AbortSignal) => {
    if (!controller.signal.aborted) {
      controller.abort(resolveAbortReason(signal));
    }
    cleanup();
  };

  for (const signal of activeSignals) {
    if (signal.aborted) {
      abortFrom(signal);
      return {
        signal: controller.signal,
        cleanup,
      };
    }

    const listener = () => abortFrom(signal);
    listeners.set(signal, listener);
    signal.addEventListener("abort", listener, { once: true });
  }

  return {
    signal: controller.signal,
    cleanup,
  };
}

export function registerManagedRequest(input: {
  controller: AbortController;
  path: string;
  method: string;
  kind: ManagedRequestKind;
  authSensitive: boolean;
}): {
  id: string;
  unregister: () => void;
} {
  const id = buildRequestId();
  activeRequests.set(id, {
    id,
    controller: input.controller,
    path: input.path,
    method: input.method,
    kind: input.kind,
    authSensitive: input.authSensitive,
    startedAt: Date.now(),
  });

  return {
    id,
    unregister: () => {
      activeRequests.delete(id);
    },
  };
}

export function abortManagedRequests(
  predicate: (entry: ManagedRequestEntry) => boolean,
  reason: string,
): number {
  let abortedCount = 0;

  for (const entry of activeRequests.values()) {
    if (!predicate(entry)) {
      continue;
    }

    abortedCount += 1;
    entry.controller.abort(createAbortReason(reason));
  }

  return abortedCount;
}

export function abortAuthSensitiveRequests(
  reason = "Authentication became invalid.",
): number {
  return abortManagedRequests(
    (entry) => entry.authSensitive,
    reason,
  );
}

export function getManagedRequestSnapshot(): ManagedRequestEntry[] {
  return [...activeRequests.values()].map((entry) => ({
    id: entry.id,
    path: entry.path,
    method: entry.method,
    kind: entry.kind,
    authSensitive: entry.authSensitive,
    startedAt: entry.startedAt,
  }));
}

export function getManagedRequestCount(): number {
  return activeRequests.size;
}

export function isLikelyAuthSensitiveClientUrl(url: string): boolean {
  if (!url) {
    return false;
  }

  if (url.startsWith("/")) {
    return true;
  }

  if (typeof window === "undefined") {
    return false;
  }

  try {
    return new URL(url, window.location.href).origin === window.location.origin;
  } catch {
    return false;
  }
}

export function createManagedRequestContext(input: {
  path: string;
  method: string;
  kind: ManagedRequestKind;
  authSensitive: boolean;
  signal?: AbortSignal;
  timeoutMs?: number;
  timeoutMessage?: string;
}) {
  const runtimeController = new AbortController();
  const managedRequest = registerManagedRequest({
    controller: runtimeController,
    path: input.path,
    method: input.method,
    kind: input.kind,
    authSensitive: input.authSensitive,
  });

  const timeoutController =
    typeof input.timeoutMs === "number" && input.timeoutMs > 0
      ? new AbortController()
      : undefined;
  const timeoutId = timeoutController
    ? setTimeout(() => {
        timeoutController.abort(
          createAbortReason(input.timeoutMessage ?? `Request timed out after ${input.timeoutMs}ms.`),
        );
      }, input.timeoutMs)
    : undefined;

  const { signal, cleanup: cleanupSignal } = composeAbortSignal([
    runtimeController.signal,
    timeoutController?.signal,
    input.signal,
  ]);

  return {
    signal: signal ?? runtimeController.signal,
    cleanup: () => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      cleanupSignal();
      managedRequest.unregister();
    },
  };
}

export function startManagedSingleFlight<T>(
  key: string,
  work: () => Promise<T>,
): {
  promise: Promise<T>;
  started: boolean;
} {
  const existing = inFlightSingleFlights.get(key) as Promise<T> | undefined;
  if (existing) {
    return {
      promise: existing,
      started: false,
    };
  }

  const promise = work().finally(() => {
    const current = inFlightSingleFlights.get(key);
    if (current === promise) {
      inFlightSingleFlights.delete(key);
    }
  });

  inFlightSingleFlights.set(key, promise);
  return {
    promise,
    started: true,
  };
}

export function isManagedSingleFlightInFlight(key: string): boolean {
  return inFlightSingleFlights.has(key);
}

export function debugManagedRequests(): ManagedRequestEntry[] {
  return safeGet(() => getManagedRequestSnapshot(), [], {
    source: "requestRuntime.debugManagedRequests",
  });
}

export function clearManagedRequestsForTests() {
  activeRequests.clear();
  inFlightSingleFlights.clear();
}
