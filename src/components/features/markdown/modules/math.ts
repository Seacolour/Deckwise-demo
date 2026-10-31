import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import type { Pluggable } from "unified";
import "katex/dist/katex.min.css";
import { inspectFencedCodeBlocks } from "../../../../lib/markdown/fencedCode.ts";
import { normalizeMarkdownScaffolding, type MarkdownRole } from "./normalizeInput.ts";

function convertMathDelimiters(src: string): string {
  src = src.replace(/\\\\\[([\s\S]*?)\\\\]/g, (_m, g1) => `$$${g1}$$`);
  src = src.replace(/\\\[([\s\S]*?)\\]/g, (_m, g1) => `$$${g1}$$`);
  src = src.replace(/\\\\\((([\s\S]*?))\\\\\)/g, (_m, g1) => `$${g1}$`);
  src = src.replace(/\\\((([\s\S]*?))\\\)/g, (_m, g1) => `$${g1}$`);
  return src;
}

function protectCode(src: string) {
  const slots: string[] = [];
  const { blocks } = inspectFencedCodeBlocks(src);

  if (blocks.length > 0) {
    let next = "";
    let lastIndex = 0;

    for (const block of blocks) {
      next += src.slice(lastIndex, block.start);
      slots.push(src.slice(block.start, block.end));
      next += `\u0000CODE_SLOT_${slots.length - 1}\u0000`;
      lastIndex = block.end;
    }

    next += src.slice(lastIndex);
    src = next;
  }

  src = src.replace(/`[^`]*`/g, (m) => {
    slots.push(m);
    return `\u0000CODE_SLOT_${slots.length - 1}\u0000`;
  });

  return { src, slots };
}

function restoreCode(src: string, slots: string[]) {
  return src.replace(/\u0000CODE_SLOT_(\d+)\u0000/g, (_m, i) => slots[Number(i)]);
}

const MATH_RE =
  /\$\$[\s\S]*?\$\$|\$[^$\n]*\$|\\\\\[[\s\S]*?\\\\]|\\\\\([\s\S]*?\\\\\)|\\\[[\s\S]*?\\]|\\\([\s\S]*?\\\)/g;

function unescapeBackslashesInMath(src: string): string {
  return src.replace(MATH_RE, (m) => m.replace(/\\\\/g, "\\"));
}

function escapePipesInMath(src: string): string {
  return src.replace(MATH_RE, (m) => m.replace(/(?<!\\)\|/g, "\\|"));
}

function fixUnicodeInMath(src: string): string {
  return src.replace(MATH_RE, (m) => {
    let fixed = m;

    fixed = fixed.replace(/\u2013/g, "--");
    fixed = fixed.replace(/\u2014/g, "---");
    fixed = fixed.replace(/\u03b7/g, "\\eta");
    fixed = fixed.replace(/\u00b2/g, "^2");
    fixed = fixed.replace(/\u00b3/g, "^3");
    fixed = fixed.replace(/\u2018/g, "'");
    fixed = fixed.replace(/\u2019/g, "'");
    fixed = fixed.replace(/\u201C/g, '"');
    fixed = fixed.replace(/\u201D/g, '"');

    return fixed;
  });
}

export function normalizeMarkdownInput(src: string, role?: MarkdownRole): string {
  let out = normalizeMarkdownScaffolding(src, role);

  const protectedCode = protectCode(out);
  out = protectedCode.src;
  out = convertMathDelimiters(out);
  out = escapePipesInMath(out);
  out = fixUnicodeInMath(out);
  out = unescapeBackslashesInMath(out);

  out = out
    .split("\n")
    .map((line) => {
      if (!line.includes("|")) return line;
      return line.replace(/\$\$([^\n]*?)\$\$/g, (_m, g1) => `$\\displaystyle ${g1}$`);
    })
    .join("\n");

  return restoreCode(out, protectedCode.slots);
}

export function getMathRemarkPlugins(): Pluggable[] {
  return [remarkMath as Pluggable];
}

export function getMathRehypePlugins(): Pluggable[] {
  return [rehypeKatex as Pluggable];
}

export function remarkMathInTablesAsInline(): Pluggable {
  return function attacher() {
    return function transformer(tree: unknown) {
      const visit = (node: unknown, inTable: boolean) => {
        if (!node || typeof node !== "object") return;
        const n = node as Record<string, unknown>;
        const nextInTable =
          inTable || n.type === "table" || n.type === "tableRow" || n.type === "tableCell";

        if (nextInTable && n.type === "math" && typeof n.value === "string") {
          const value = n.value.trim();
          n.type = "inlineMath";
          n.value = value.startsWith("\\displaystyle") ? value : `\\displaystyle ${value}`;
          delete n.meta;
          delete n.data;
        }

        const children = n.children;
        if (Array.isArray(children)) {
          for (const child of children as unknown[]) visit(child, nextInTable);
        }
      };

      visit(tree, false);
    };
  };
}
