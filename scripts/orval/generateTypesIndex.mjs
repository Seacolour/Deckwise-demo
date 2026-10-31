import fs from "node:fs/promises";
import path from "node:path";

/**
 * Regenerate `types/all/index.ts` to export every file in the folder.
 *
 * Why:
 * - orval-generated clients import types from `types/all` (barrel).
 * - some versions/configs don't keep the barrel exporting *Params / helper types in sync
 * - which breaks TypeScript compilation (missing exported members).
 */
async function main() {
  const root = process.cwd();
  const typesDir = path.resolve(root, "src", "lib", "types", "all");
  const indexFile = path.join(typesDir, "index.ts");

  const entries = await fs.readdir(typesDir, { withFileTypes: true });
  const files = entries
    .filter((e) => e.isFile() && e.name.endsWith(".ts"))
    .map((e) => e.name)
    .filter((name) => name !== "index.ts")
    .sort((a, b) => a.localeCompare(b, "en"));

  const lines = [
    "/**",
    " * Auto-generated barrel file.",
    " * Do not edit manually; run `node scripts/orval/generateTypesIndex.mjs`.",
    " */",
    "",
    ...files.map((f) => `export * from './${path.basename(f, ".ts")}';`),
    "",
  ];

  await fs.writeFile(indexFile, lines.join("\n"), "utf8");
  console.log(`[types] wrote ${path.relative(root, indexFile)} (${files.length} exports)`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});


