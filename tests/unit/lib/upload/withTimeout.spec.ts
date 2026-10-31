import assert from "node:assert/strict";
import { test } from "vitest";
import { withTimeout } from "@/src/lib/upload/withTimeout.ts";

test("withTimeout rejects hung uploads with the configured timeout message", async () => {
  await assert.rejects(
    withTimeout(new Promise<never>(() => {}), 10, "Upload timed out (60s)"),
    (error: unknown) =>
      error instanceof Error && error.message === "Upload timed out (60s)",
  );
});

test("withTimeout lets slow uploads finish before the timeout window closes", async () => {
  const result = await withTimeout(
    new Promise<string>((resolve) => {
      setTimeout(() => resolve("ok"), 5);
    }),
    50,
    "Upload timed out (60s)",
  );

  assert.equal(result, "ok");
});
