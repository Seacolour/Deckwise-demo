import assert from "node:assert/strict";
import { test } from "vitest";
import { inspectFencedCodeBlocks, isInsideFencedCodeBlock } from "@/src/lib/markdown/fencedCode.ts";

test("inspectFencedCodeBlocks tracks balanced tilde fences", () => {
  const input = [
    "Before",
    "~~~js",
    'const latex = "\\\\(x\\\\)";',
    "~~~",
    "After",
  ].join("\n");

  const { blocks, trailingFence } = inspectFencedCodeBlocks(input);

  assert.equal(blocks.length, 1);
  assert.equal(trailingFence, null);
  assert.equal(input.slice(blocks[0]!.start, blocks[0]!.end), '~~~js\nconst latex = "\\\\(x\\\\)";\n~~~');
  assert.equal(isInsideFencedCodeBlock(input.indexOf("latex"), blocks), true);
  assert.equal(isInsideFencedCodeBlock(input.indexOf("Before"), blocks), false);
});

test("inspectFencedCodeBlocks reports unterminated trailing fences", () => {
  const input = [
    "Before",
    "```json",
    '{"ok": true}',
  ].join("\n");

  const { blocks, trailingFence } = inspectFencedCodeBlocks(input);

  assert.equal(blocks.length, 1);
  assert.deepEqual(trailingFence, { marker: "`", length: 3 });
  assert.equal(blocks[0]?.closed, false);
  assert.equal(blocks[0]?.end, input.length);
});
