type WeakNetworkAbortBehavior = "abort-error" | "reason";

type WeakNetworkDelayableStep = {
  delayMs?: number;
  abortBehavior?: WeakNetworkAbortBehavior;
};

type WeakNetworkJsonStep = WeakNetworkDelayableStep & {
  kind: "json";
  body: unknown;
  status?: number;
  headers?: HeadersInit;
};

type WeakNetworkTextStep = WeakNetworkDelayableStep & {
  kind: "text";
  body: string;
  status?: number;
  headers?: HeadersInit;
};

type WeakNetworkApiJsonStep = WeakNetworkDelayableStep & {
  kind: "api-json";
  data: unknown;
  code?: number;
  message?: string;
  status?: number;
  headers?: HeadersInit;
};

type WeakNetworkErrorStep = WeakNetworkDelayableStep & {
  kind: "network-error";
  error?: Error;
  message?: string;
};

type WeakNetworkHangStep = WeakNetworkDelayableStep & {
  kind: "hang";
};

export type WeakNetworkFetchStep =
  | WeakNetworkJsonStep
  | WeakNetworkTextStep
  | WeakNetworkApiJsonStep
  | WeakNetworkErrorStep
  | WeakNetworkHangStep;

export type WeakNetworkFetchCall = {
  url: string;
  init?: RequestInit;
  stepIndex: number;
};

function createAbortError(reason?: unknown): Error {
  if (reason instanceof Error) {
    return reason;
  }

  if (typeof DOMException === "function") {
    return new DOMException("The operation was aborted.", "AbortError");
  }

  const error = new Error("The operation was aborted.");
  error.name = "AbortError";
  return error;
}

function resolveAbortError(
  signal: AbortSignal | undefined,
  abortBehavior: WeakNetworkAbortBehavior,
): Error {
  if (abortBehavior === "reason" && signal?.reason instanceof Error) {
    return signal.reason;
  }

  return createAbortError(signal?.reason);
}

async function waitForDelay(
  ms: number,
  signal: AbortSignal | undefined,
  abortBehavior: WeakNetworkAbortBehavior,
): Promise<void> {
  if (signal?.aborted) {
    throw resolveAbortError(signal, abortBehavior);
  }

  if (ms <= 0) {
    return;
  }

  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      resolve();
    }, ms);

    const handleAbort = () => {
      cleanup();
      reject(resolveAbortError(signal, abortBehavior));
    };

    const cleanup = () => {
      clearTimeout(timer);
      signal?.removeEventListener("abort", handleAbort);
    };

    signal?.addEventListener("abort", handleAbort, { once: true });
  });
}

async function waitUntilAbort(
  signal: AbortSignal | undefined,
  abortBehavior: WeakNetworkAbortBehavior,
): Promise<never> {
  if (!signal) {
    return await new Promise<never>(() => {});
  }

  if (signal.aborted) {
    throw resolveAbortError(signal, abortBehavior);
  }

  return await new Promise<never>((_, reject) => {
    const handleAbort = () => {
      signal.removeEventListener("abort", handleAbort);
      reject(resolveAbortError(signal, abortBehavior));
    };

    signal.addEventListener("abort", handleAbort, { once: true });
  });
}

function resolveUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    return input;
  }

  if (input instanceof URL) {
    return input.toString();
  }

  return input.url;
}

function buildResponseHeaders(
  headers: HeadersInit | undefined,
  contentType: string,
): Headers {
  const out = new Headers(headers);
  if (!out.has("content-type")) {
    out.set("content-type", contentType);
  }
  return out;
}

export function installWeakNetworkFetch(steps: readonly WeakNetworkFetchStep[]) {
  const originalFetch = globalThis.fetch;
  const calls: WeakNetworkFetchCall[] = [];
  let cursor = 0;

  globalThis.fetch = (async (input, init) => {
    const step = steps[cursor];
    if (!step) {
      throw new Error(
        `Weak-network fetch mock ran out of scripted steps after ${calls.length} call(s).`,
      );
    }

    const stepIndex = cursor;
    cursor += 1;
    calls.push({
      url: resolveUrl(input),
      init,
      stepIndex,
    });

    const signal = init?.signal ?? undefined;
    const abortBehavior = step.abortBehavior ?? "abort-error";
    await waitForDelay(step.delayMs ?? 0, signal, abortBehavior);

    switch (step.kind) {
      case "json":
        return new Response(JSON.stringify(step.body), {
          status: step.status ?? 200,
          headers: buildResponseHeaders(step.headers, "application/json"),
        });
      case "text":
        return new Response(step.body, {
          status: step.status ?? 200,
          headers: buildResponseHeaders(step.headers, "text/plain;charset=UTF-8"),
        });
      case "api-json":
        return new Response(
          JSON.stringify({
            code: step.code ?? 0,
            message: step.message ?? "success",
            data: step.data,
            timestamp: "2026-03-28T00:00:00.000Z",
          }),
          {
            status: step.status ?? 200,
            headers: buildResponseHeaders(step.headers, "application/json"),
          },
        );
      case "network-error":
        throw step.error ?? new TypeError(step.message ?? "Failed to fetch");
      case "hang":
        return await waitUntilAbort(signal, abortBehavior);
    }
  }) as typeof fetch;

  return {
    calls,
    restore() {
      globalThis.fetch = originalFetch;
    },
    assertConsumed() {
      const remaining = steps.length - cursor;
      if (remaining !== 0) {
        throw new Error(
          `Weak-network fetch mock still has ${remaining} unconsumed step(s).`,
        );
      }
    },
    remainingSteps() {
      return Math.max(0, steps.length - cursor);
    },
  };
}

export function readRequestJson(call: WeakNetworkFetchCall): unknown {
  const body = call.init?.body;
  if (typeof body !== "string") {
    throw new Error("Expected a JSON string request body.");
  }

  return JSON.parse(body);
}
