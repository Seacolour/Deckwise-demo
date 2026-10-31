import { cpSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";

const root = process.cwd();

copyIntoStandalone(".next/static", ".next/standalone/.next/static");
copyIntoStandalone("public", ".next/standalone/public", { optional: true });

function copyIntoStandalone(sourceRelativePath, targetRelativePath, options = {}) {
  const sourcePath = join(root, sourceRelativePath);
  const targetPath = join(root, targetRelativePath);

  if (!existsSync(sourcePath)) {
    if (options.optional) {
      return;
    }
    throw new Error(`Missing standalone source path: ${sourceRelativePath}`);
  }

  rmSync(targetPath, { force: true, recursive: true });
  mkdirSync(dirname(targetPath), { recursive: true });
  cpSync(sourcePath, targetPath, { recursive: true });
}
