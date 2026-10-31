import { inspectFencedCodeBlocks } from "../../../../lib/markdown/fencedCode.ts";

export type MarkdownRole = "user" | "assistant" | "system";

function shouldDecodeMixedEscapedWhitespace(src: string, role?: MarkdownRole): boolean {
  if (role !== "system") return false;
  if (!src.includes("\n")) return false;
  if (!src.includes("\\n") && !src.includes("\\r\\n")) return false;
  return /(^|\\n|\n)\s{0,3}(`{3,}|~{3,})/.test(src);
}

function restoreEscapedWhitespace(src: string, role?: MarkdownRole): string {
  const hasRealNL = src.includes("\n");
  const hasEscapedNL = src.includes("\\n") || src.includes("\\r\\n");
  const hasEscapedTab = src.includes("\\t");
  if (!hasEscapedNL && !hasEscapedTab) {
    return src;
  }

  if (!hasRealNL || shouldDecodeMixedEscapedWhitespace(src, role)) {
    return src.replace(/\\r\\n/g, "\n").replace(/\\n/g, "\n").replace(/\\t/g, "\t");
  }

  return src;
}

function closeUnterminatedFencedCodeBlocks(src: string): string {
  const { trailingFence } = inspectFencedCodeBlocks(src);
  if (!trailingFence) {
    return src;
  }

  // Pitfall: streaming can pause before the model emits the closing fence.
  // Closing the tail here prevents a partial frame from swallowing later prose.
  const closingFence = trailingFence.marker.repeat(trailingFence.length);
  return src.endsWith("\n") ? `${src}${closingFence}` : `${src}\n${closingFence}`;
}

export function normalizeMarkdownScaffolding(src: string, role?: MarkdownRole): string {
  const restored = restoreEscapedWhitespace(src, role);
  return closeUnterminatedFencedCodeBlocks(restored);
}
