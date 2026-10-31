import assert from "node:assert/strict";
import { test } from "vitest";
import { normalizeMarkdownScaffolding } from "@/src/components/features/markdown/modules/normalizeInput.ts";

test("normalizeMarkdownScaffolding keeps balanced fenced code blocks intact", () => {
  const input = [
    "Before",
    "```ts",
    "const value = 1;",
    "```",
    "After",
  ].join("\n");

  assert.equal(normalizeMarkdownScaffolding(input, "system"), input);
});

test("normalizeMarkdownScaffolding repairs mixed escaped newlines around system fences", () => {
  const input = [
    "Before",
    "```go",
    'fmt.Println("hi")\\n```\\nAfter',
  ].join("\n");

  const normalized = normalizeMarkdownScaffolding(input, "system");

  assert.match(normalized, /fmt\.Println\("hi"\)\n```\nAfter$/);
});

test("normalizeMarkdownScaffolding auto-closes unterminated fenced blocks", () => {
  const input = [
    "Before",
    "```json",
    '{"ok": true}',
  ].join("\n");

  assert.equal(normalizeMarkdownScaffolding(input, "system"), `${input}\n\`\`\``);
});
