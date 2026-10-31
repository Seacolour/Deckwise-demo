type SharedReadEntry<T> = {
  promise: Promise<T>;
  controller: AbortController;
  consumers: number;
  abortTimer: ReturnType<typeof setTimeout> | null;
};

type SharedReadCacheEntry<T> = {
  value: T;
  expiresAt: number;
};

const sharedReadInFlight = new Map<string, SharedReadEntry<unknown>>();
const sharedReadCache = new Map<string, SharedReadCacheEntry<unknown>>();
const MAX_SHARED_READ_CACHE_ENTRIES = 64;
const SHARED_READ_ABORT_GRACE_MS = 50;

function createAbortError(): Error {
  if (typeof DOMException === "function") {
    return new DOMException("The operation was aborted.", "AbortError");
  }

  const error = new Error("The operation was aborted.");
  error.name = "AbortError";
  return error;
}

function getCachedSharedReadValue<T>(key: string): T | null {
  const cached = sharedReadCache.get(key);
  if (!cached) {
    return null;
  }

  if (cached.expiresAt <= Date.now()) {
    sharedReadCache.delete(key);
    return null;
  }

  sharedReadCache.delete(key);
  sharedReadCache.set(key, cached);
  return cached.value as T;
}

function pruneSharedReadCache(now = Date.now()) {
  for (const [key, entry] of sharedReadCache.entries()) {
    if (entry.expiresAt <= now) {
      sharedReadCache.delete(key);
    }
  }

  while (sharedReadCache.size > MAX_SHARED_READ_CACHE_ENTRIES) {
    const oldestKey = sharedReadCache.keys().next().value;
    if (!oldestKey) {
      break;
    }
    sharedReadCache.delete(oldestKey);
  }
}

function clearEntryAbortTimer(entry: SharedReadEntry<unknown>) {
  if (entry.abortTimer !== null) {
    clearTimeout(entry.abortTimer);
    entry.abortTimer = null;
  }
}

export async function runSharedRead<T>(
  key: string,
  load: (signal: AbortSignal) => Promise<T>,
  opts?: { cacheMs?: number; signal?: AbortSignal },
): Promise<T> {
  if (opts?.signal?.aborted) {
    throw createAbortError();
  }

  const cached = getCachedSharedReadValue<T>(key);
  if (cached !== null) {
    return cached;
  }

  let entry = sharedReadInFlight.get(key) as SharedReadEntry<T> | undefined;
  if (entry?.controller.signal.aborted) {
    clearEntryAbortTimer(entry);
    sharedReadInFlight.delete(key);
    entry = undefined;
  }
  if (!entry) {
    const cacheMs = Math.max(0, opts?.cacheMs ?? 0);
    const controller = new AbortController();
    const promise = load(controller.signal)
      .then((value) => {
        if (cacheMs > 0) {
          sharedReadCache.set(key, {
            value,
            expiresAt: Date.now() + cacheMs,
          });
          pruneSharedReadCache();
        }
        return value;
      })
      .finally(() => {
        const current = sharedReadInFlight.get(key);
        if (current?.promise === promise) {
          clearEntryAbortTimer(current);
          sharedReadInFlight.delete(key);
        }
      });

    entry = {
      promise,
      controller,
      consumers: 0,
      abortTimer: null,
    };
    sharedReadInFlight.set(key, entry);
  }

  clearEntryAbortTimer(entry);
  entry.consumers += 1;

  const release = () => {
    if (!entry) {
      return;
    }

    entry.consumers = Math.max(0, entry.consumers - 1);
    if (
      entry.consumers === 0 &&
      entry.abortTimer === null &&
      sharedReadInFlight.get(key) === entry
    ) {
      const orphanedEntry = entry;
      // React dev Strict Mode can unmount and remount the same loader in the
      // same tick. Keep the request briefly reusable so the remount does not
      // create a canceled upstream request followed by a duplicate retry.
      orphanedEntry.abortTimer = setTimeout(() => {
        if (
          orphanedEntry.consumers > 0 ||
          sharedReadInFlight.get(key) !== orphanedEntry
        ) {
          return;
        }

        orphanedEntry.abortTimer = null;
        sharedReadInFlight.delete(key);
        orphanedEntry.controller.abort();
      }, SHARED_READ_ABORT_GRACE_MS);
    }
  };

  if (!opts?.signal) {
    return entry.promise.finally(release);
  }

  const consumerSignal = opts.signal;

  return new Promise<T>((resolve, reject) => {
    let settled = false;

    const cleanup = () => {
      consumerSignal.removeEventListener("abort", handleAbort);
    };

    const finalize = () => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      release();
    };

    const handleAbort = () => {
      finalize();
      reject(createAbortError());
    };

    if (consumerSignal.aborted) {
      handleAbort();
      return;
    }

    consumerSignal.addEventListener("abort", handleAbort, { once: true });
    entry.promise.then(
      (value) => {
        finalize();
        resolve(value);
      },
      (error) => {
        finalize();
        reject(error);
      },
    );
  });
}

export function clearSharedReadState() {
  for (const entry of sharedReadInFlight.values()) {
    clearEntryAbortTimer(entry);
    entry.controller.abort();
  }
  sharedReadInFlight.clear();
  sharedReadCache.clear();
}
