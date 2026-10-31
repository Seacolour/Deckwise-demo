import assert from "node:assert/strict";
import { test } from "vitest";
import { fetchWithRetry } from "@/src/lib/api/fetchers/retry.ts";

test("fetchWithRetry retries transient network errors when enabled", async () => {
  let callCount = 0;

  const response = await fetchWithRetry(
    async () => {
      callCount += 1;
      if (callCount === 1) {
        throw new TypeError("Failed to fetch");
      }

      return new Response("ok", { status: 200 });
    },
    {
      retries: 1,
      retryNetworkErrors: true,
      baseDelayMs: 1,
    },
  );

  assert.equal(callCount, 2);
  assert.equal(response.status, 200);
});

test("fetchWithRetry does not retry transient network errors unless explicitly enabled", async () => {
  let callCount = 0;

  await assert.rejects(
    fetchWithRetry(
      async () => {
        callCount += 1;
        throw new TypeError("Failed to fetch");
      },
      {
        retries: 2,
        retryNetworkErrors: false,
        baseDelayMs: 1,
      },
    ),
    (error: unknown) =>
      error instanceof TypeError && error.message === "Failed to fetch",
  );

  assert.equal(callCount, 1);
});

test("fetchWithRetry aborts while waiting for the next retry window", async () => {
  let callCount = 0;
  const controller = new AbortController();

  const request = fetchWithRetry(
    async () => {
      callCount += 1;
      return new Response("busy", {
        status: 503,
      });
    },
    {
      retries: 2,
      signal: controller.signal,
      baseDelayMs: 50,
    },
  );

  setTimeout(() => controller.abort(), 5);

  await assert.rejects(
    request,
    (error: unknown) => error instanceof Error && error.name === "AbortError",
  );

  assert.equal(callCount, 1);
});
