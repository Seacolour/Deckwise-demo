import assert from "node:assert/strict";
import { test } from "vitest";
import { StreamEventParser } from "@/src/lib/stream/streamEventParser.ts";

test("StreamEventParser recognizes done frames as terminal events", () => {
  const parser = new StreamEventParser();
  const event = parser.parse(JSON.stringify({
    type: "done",
    seq: 9,
    ts: "2026-03-31T12:00:00.000+08:00",
    payload: "end",
  }));

  assert.deepEqual(event, { type: "done" });
});

test("StreamEventParser recognizes abort frames as terminal events", () => {
  const parser = new StreamEventParser();
  const event = parser.parse(JSON.stringify({
    type: "abort",
    seq: 10,
    ts: "2026-03-31T12:00:01.000+08:00",
    payload: "aborted",
  }));

  assert.deepEqual(event, { type: "abort", reason: "aborted" });
});

test("StreamEventParser recognizes error frames without misclassifying them as tool events", () => {
  const parser = new StreamEventParser();
  const event = parser.parse(JSON.stringify({
    type: "error",
    seq: 11,
    ts: "2026-03-31T12:00:02.000+08:00",
    payload: "AI 调用失败",
  }));

  assert.deepEqual(event, {
    type: "error",
    error: {
      kind: "aiServiceError",
      message: "AI 调用失败",
    },
  });
});
