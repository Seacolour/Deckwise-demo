import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

const CONSOLE_DEBUG_PATTERN = /console\.(log|info|debug)\b/g;
const SOURCE_EXTENSIONS = new Set([".ts", ".tsx"]);
const ALLOWED_CONSOLE_DEBUG_FILES = new Set([
  "src/components/features/editor/internal/Editor/utils/debug.ts",
  "src/lib/debug.ts",
  "src/lib/server/log.ts",
]);

describe("bare console debug gate", () => {
  it("keeps console.log/info/debug centralized in debug helpers", () => {
    const hits = findConsoleDebugHits(["app", "src"]);

    expect(hits).toEqual([]);
  });
});

function findConsoleDebugHits(roots: string[]) {
  const workspaceRoot = process.cwd();
  const hits: string[] = [];

  for (const root of roots) {
    for (const filePath of walkSourceFiles(join(workspaceRoot, root))) {
      const relativePath = relative(workspaceRoot, filePath).replaceAll("\\", "/");
      if (ALLOWED_CONSOLE_DEBUG_FILES.has(relativePath)) {
        continue;
      }

      const source = readFileSync(filePath, "utf8");
      for (const match of source.matchAll(CONSOLE_DEBUG_PATTERN)) {
        hits.push(`${relativePath}:${lineNumberForIndex(source, match.index ?? 0)}:${match[0]}`);
      }
    }
  }

  return hits;
}

function* walkSourceFiles(dir: string): Generator<string> {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === "generated" || entry.name === "types") {
      continue;
    }

    const entryPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      yield* walkSourceFiles(entryPath);
      continue;
    }

    if (!entry.isFile()) {
      continue;
    }

    const extension = entry.name.slice(entry.name.lastIndexOf("."));
    if (SOURCE_EXTENSIONS.has(extension) && statSync(entryPath).isFile()) {
      yield entryPath;
    }
  }
}

function lineNumberForIndex(source: string, index: number) {
  return source.slice(0, index).split("\n").length;
}
