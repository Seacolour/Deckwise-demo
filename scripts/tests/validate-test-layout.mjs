import { existsSync, readdirSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const ROOTS = [
  path.resolve(process.cwd(), "src"),
  path.resolve(process.cwd(), "tests"),
];

const TEST_LIKE_RE = /\.(?:test|spec)\.tsx?$/;
const allowedPatterns = [
  /^tests\/unit\/.+\.spec\.tsx?$/,
  /^tests\/integration\/.+\.spec\.tsx?$/,
  /^tests\/e2e\/.+\.spec\.ts$/,
];

function toPosix(filePath) {
  return filePath.split(path.sep).join("/");
}

function walk(root) {
  const files = [];
  const entries = readdirSync(root, { withFileTypes: true });

  for (const entry of entries) {
    const absolutePath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(absolutePath));
      continue;
    }

    if (entry.isFile() && TEST_LIKE_RE.test(entry.name)) {
      files.push(absolutePath);
    }
  }

  return files;
}

const offenders = ROOTS
  .filter((root) => existsSync(root))
  .flatMap((root) => {
    try {
      return walk(root);
    } catch {
      return [];
    }
  })
  .map((absolutePath) => path.relative(process.cwd(), absolutePath))
  .filter((relativePath) => {
    const normalized = toPosix(relativePath);
    return !allowedPatterns.some((pattern) => pattern.test(normalized));
  })
  .sort();

if (offenders.length > 0) {
  console.error("[test-layout] Found test files outside the allowed structure:");
  offenders.forEach((filePath) => {
    console.error(` - ${toPosix(filePath)}`);
  });
  process.exit(1);
}

console.log("[test-layout] Test file structure is valid.");
