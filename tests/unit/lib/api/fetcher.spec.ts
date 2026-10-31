import assert from "node:assert/strict";
import { test, vi } from "vitest";
import { apiFetch } from "@/src/lib/api/fetcher.ts";
import {
  abortAuthSensitiveRequests,
  clearManagedRequestsForTests,
  getManagedRequestCount,
} from "@/src/lib/api/requestRuntime.ts";
import {
  installWeakNetworkFetch,
  readRequestJson,
} from "@/src/test/network/weakNetwork.ts";

test("apiFetch retries POST requests marked with query policy after a transient network failure", async () => {
  const mock = installWeakNetworkFetch([
    {
      kind: "network-error",
      error: new TypeError("Failed to fetch"),
    },
    {
      kind: "api-json",
      data: {
        records: [{ id: "project-1" }],
      },
    },
  ]);

  try {
    const result = await apiFetch<{ records: Array<{ id: string }> }>(
      "/projects/list",
      {
        method: "POST",
        requestPolicy: "query",
        body: {
          current: 1,
          pageSize: 1,
        },
      },
    );

    assert.deepEqual(result, {
      records: [{ id: "project-1" }],
    });
    assert.equal(mock.calls.length, 2);
    assert.equal(mock.calls[0]?.init?.method, "POST");
    assert.equal(mock.calls[0]?.init?.credentials, "include");
    assert.equal(new Headers(mock.calls[0]?.init?.headers).get("Accept"), "application/json");
    assert.equal(new Headers(mock.calls[0]?.init?.headers).has("Authorization"), false);
    assert.deepEqual(readRequestJson(mock.calls[0]!), {
      current: 1,
      pageSize: 1,
    });
    mock.assertConsumed();
  } finally {
    mock.restore();
  }
});

test("apiFetch does not retry write POST requests on transient network failures by default", async () => {
  const mock = installWeakNetworkFetch([
    {
      kind: "network-error",
      error: new TypeError("Failed to fetch"),
    },
  ]);

  try {
    await assert.rejects(
      apiFetch("/projects/create", {
        method: "POST",
        body: {
          name: "Demo project",
        },
      }),
      (error: unknown) =>
        error instanceof TypeError && error.message === "Failed to fetch",
    );

    assert.equal(mock.calls.length, 1);
    mock.assertConsumed();
  } finally {
    mock.restore();
  }
});

test("apiFetch converts timeout-driven aborts into TimeoutError", async () => {
  const mock = installWeakNetworkFetch([
    {
      kind: "hang",
    },
  ]);

  try {
    await assert.rejects(
      apiFetch("/documents/list", {
        method: "GET",
        timeoutMs: 20,
      }),
      (error: unknown) =>
        error instanceof Error && error.name === "TimeoutError",
    );

    assert.equal(mock.calls.length, 1);
    mock.assertConsumed();
  } finally {
    mock.restore();
  }
});

test("apiFetch rejects invalid 200 text responses instead of treating them as successful empty payloads", async () => {
  const mock = installWeakNetworkFetch([
    {
      kind: "text",
      body: "<html>gateway error</html>",
      status: 200,
    },
  ]);
  const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

  try {
    await assert.rejects(
      apiFetch("/documents/list", {
        method: "GET",
      }),
      (error: unknown) =>
        error instanceof Error && error.name === "SyntaxError",
    );

    assert.equal(mock.calls.length, 1);
    mock.assertConsumed();
  } finally {
    consoleErrorSpy.mockRestore();
    mock.restore();
  }
});

test("apiFetch preserves caller aborts instead of rewriting them as timeouts", async () => {
  const mock = installWeakNetworkFetch([
    {
      kind: "hang",
    },
  ]);
  const controller = new AbortController();

  try {
    const request = apiFetch("/documents/list", {
      method: "GET",
      timeoutMs: 100,
      signal: controller.signal,
    });
    setTimeout(() => controller.abort(), 5);

    await assert.rejects(
      request,
      (error: unknown) =>
        error instanceof Error && error.name === "AbortError",
    );

    assert.equal(mock.calls.length, 1);
    mock.assertConsumed();
  } finally {
    mock.restore();
  }
});

test("apiFetch requests can be aborted through the global auth-sensitive runtime registry", async () => {
  clearManagedRequestsForTests();
  const mock = installWeakNetworkFetch([
    {
      kind: "hang",
    },
  ]);

  try {
    const request = apiFetch("/projects/list", {
      method: "GET",
    });

    await new Promise((resolve) => setTimeout(resolve, 5));
    assert.equal(getManagedRequestCount(), 1);

    const aborted = abortAuthSensitiveRequests("Auth invalidated during test.");
    assert.equal(aborted, 1);

    await assert.rejects(
      request,
      (error: unknown) =>
        error instanceof Error && error.name === "AbortError",
    );

    assert.equal(mock.calls.length, 1);
    mock.assertConsumed();
  } finally {
    clearManagedRequestsForTests();
    mock.restore();
  }
});
