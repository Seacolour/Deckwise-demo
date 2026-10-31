import { spawnSync } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const tempTsconfigPath = resolve(projectRoot, ".next/typecheck/tsconfig.json");
const nextBinPath = resolve(projectRoot, "node_modules/next/dist/bin/next");
const tscBinPath = resolve(projectRoot, "node_modules/typescript/bin/tsc");
const passThroughArgs = process.argv.slice(2);

const typecheckTsconfig = {
  extends: "../../tsconfig.json",
  include: [
    "../../next-env.d.ts",
    "../../**/*.ts",
    "../../**/*.tsx",
    "../../.next/dev/types/**/*.ts",
    "../../**/*.mts",
  ],
  exclude: ["../../node_modules", "../../.next/types/**/*.ts"],
};

mkdirSync(dirname(tempTsconfigPath), { recursive: true });
writeFileSync(tempTsconfigPath, `${JSON.stringify(typecheckTsconfig, null, 2)}\n`);

const typegenResult = spawnSync(process.execPath, [nextBinPath, "typegen"], {
  cwd: projectRoot,
  stdio: "inherit",
});

if (typegenResult.error) {
  throw typegenResult.error;
}

if (typegenResult.status !== 0) {
  process.exit(typegenResult.status ?? 1);
}

const result = spawnSync(
  process.execPath,
  [tscBinPath, "--noEmit", "--project", tempTsconfigPath, ...passThroughArgs],
  {
    cwd: projectRoot,
    stdio: "inherit",
  },
);

if (result.error) {
  throw result.error;
}

process.exit(result.status ?? 1);
