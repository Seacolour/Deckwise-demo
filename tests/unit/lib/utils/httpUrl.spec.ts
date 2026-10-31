import { test } from "vitest";
import assert from "node:assert/strict";
import { normalizeHttpUrl, toHttpUrlOrNull } from "@/src/lib/utils/httpUrl.ts";

test("normalizeHttpUrl repairs single-slash http prefixes", () => {
  assert.equal(normalizeHttpUrl("https:/example.com/file.pdf"), "https://example.com/file.pdf");
  assert.equal(normalizeHttpUrl("http:/example.com/file.pdf"), "http://example.com/file.pdf");
});

test("toHttpUrlOrNull accepts normalized http urls and rejects non-http values", () => {
  assert.equal(
    toHttpUrlOrNull(" https:/example.com/image.png "),
    "https://example.com/image.png",
  );
  assert.equal(toHttpUrlOrNull("ftp://example.com/file.pdf"), null);
  assert.equal(toHttpUrlOrNull("/relative/path.png"), null);
  assert.equal(toHttpUrlOrNull({ url: "https://example.com" }), null);
});
