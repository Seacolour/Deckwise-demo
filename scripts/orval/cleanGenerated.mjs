import fs from "node:fs/promises";
import path from "node:path";

async function removeTsFiles(dir) {
  try {
    const entries = await fs.readdir(dir, { withFileTypes: true });
    await Promise.all(entries.map(async (entry) => {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        await removeTsFiles(fullPath);
        return;
      }
      if (entry.isFile() && entry.name.endsWith(".ts")) {
        await fs.unlink(fullPath);
      }
    }));
  } catch (error) {
    if (error && typeof error === "object" && error.code === "ENOENT") {
      return;
    }
    throw error;
  }
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function main() {
  const root = process.cwd();
  const generatedDir = path.resolve(root, "src", "lib", "api", "generated");
  const typesDir = path.resolve(root, "src", "lib", "types", "all");

  await ensureDir(generatedDir);
  await ensureDir(typesDir);
  await removeTsFiles(generatedDir);
  await removeTsFiles(typesDir);

  console.log(`[orval-clean] cleared ${path.relative(root, generatedDir)}`);
  console.log(`[orval-clean] cleared ${path.relative(root, typesDir)}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
