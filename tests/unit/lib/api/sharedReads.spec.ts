import { test } from "vitest";
import assert from "node:assert/strict";
import { clearSharedReadState, runSharedRead } from "@/src/lib/api/sharedReads";

test("runSharedRead reuses the same in-flight request", async () => {
  clearSharedReadState();

  let callCount = 0;
  const load = async () => {
    callCount += 1;
    await new Promise((resolve) => setTimeout(resolve, 20));
    return { ok: true };
  };

  const [first, second] = await Promise.all([
    runSharedRead("same-key", () => load()),
    runSharedRead("same-key", () => load()),
  ]);

  assert.equal(callCount, 1);
  assert.deepEqual(first, { ok: true });
  assert.deepEqual(second, { ok: true });
});

test("runSharedRead serves a short warm cache when configured", async () => {
  clearSharedReadState();

  let callCount = 0;
  const load = async () => {
    callCount += 1;
    return callCount;
  };

  const first = await runSharedRead("cached-key", () => load(), { cacheMs: 50 });
  const second = await runSharedRead("cached-key", () => load(), { cacheMs: 50 });

  assert.equal(first, 1);
  assert.equal(second, 1);
  assert.equal(callCount, 1);

  await new Promise((resolve) => setTimeout(resolve, 60));

  const third = await runSharedRead("cached-key", () => load(), { cacheMs: 50 });
  assert.equal(third, 2);
  assert.equal(callCount, 2);
});

test("runSharedRead lets one caller abort while another keeps the shared request alive", async () => {
  clearSharedReadState();

  let callCount = 0;
  const controller = new AbortController();
  const load = async () => {
    callCount += 1;
    await new Promise((resolve) => setTimeout(resolve, 20));
    return { ok: true };
  };

  const firstPromise = runSharedRead("abortable-key", () => load(), { signal: controller.signal });
  const secondPromise = runSharedRead("abortable-key", () => load());

  controller.abort();

  await assert.rejects(firstPromise, (error: unknown) => {
    return error instanceof Error && error.name === "AbortError";
  });

  const second = await secondPromise;
  assert.deepEqual(second, { ok: true });
  assert.equal(callCount, 1);
});

test("runSharedRead keeps a recently orphaned request reusable for immediate remounts", async () => {
  clearSharedReadState();

  let callCount = 0;
  let abortCount = 0;
  const load = async (signal: AbortSignal) => {
    callCount += 1;
    const callId = callCount;

    await new Promise<void>((resolve, reject) => {
      const timer = setTimeout(resolve, 20);
      const abort = () => {
        clearTimeout(timer);
        abortCount += 1;
        const error = new Error("aborted");
        error.name = "AbortError";
        reject(error);
      };

      if (signal.aborted) {
        abort();
        return;
      }

      signal.addEventListener("abort", abort, { once: true });
    });

    return { callId };
  };

  const controller = new AbortController();
  const firstPromise = runSharedRead("remount-key", load, { signal: controller.signal });
  controller.abort();

  const secondPromise = runSharedRead("remount-key", load);

  await assert.rejects(firstPromise, (error: unknown) => {
    return error instanceof Error && error.name === "AbortError";
  });

  const second = await secondPromise;
  assert.deepEqual(second, { callId: 1 });
  assert.equal(callCount, 1);
  assert.equal(abortCount, 0);
});

test("runSharedRead aborts an orphaned request after the remount grace window", async () => {
  clearSharedReadState();

  let callCount = 0;
  let abortCount = 0;
  const load = async (signal: AbortSignal) => {
    callCount += 1;
    const callId = callCount;

    await new Promise<void>((resolve, reject) => {
      const abort = () => {
        abortCount += 1;
        const error = new Error("aborted");
        error.name = "AbortError";
        reject(error);
      };

      if (signal.aborted) {
        abort();
        return;
      }

      signal.addEventListener("abort", abort, { once: true });
    });

    return { callId };
  };

  const controller = new AbortController();
  const firstPromise = runSharedRead("orphan-key", load, { signal: controller.signal });
  controller.abort();

  await assert.rejects(firstPromise, (error: unknown) => {
    return error instanceof Error && error.name === "AbortError";
  });

  await new Promise((resolve) => setTimeout(resolve, 75));
  assert.equal(abortCount, 1);

  const second = await runSharedRead("orphan-key", async () => {
    callCount += 1;
    return { callId: callCount };
  });
  assert.deepEqual(second, { callId: 2 });
  assert.equal(callCount, 2);
});
