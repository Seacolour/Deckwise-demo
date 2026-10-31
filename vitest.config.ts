import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const projectRoot = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": projectRoot,
    },
  },
  test: {
    include: [
      "tests/unit/**/*.spec.ts",
      "tests/unit/**/*.spec.tsx",
      "tests/integration/**/*.spec.ts",
      "tests/integration/**/*.spec.tsx",
    ],
    exclude: [
      "tests/e2e/**",
      "node_modules/**",
      ".next/**",
    ],
    environment: "node",
    setupFiles: ["./tests/setup/vitest.setup.ts"],
    passWithNoTests: false,
    // Expected error-path tests and jsdom shims should not bury actionable CI
    // output. Vitest still prints their console output whenever a test fails.
    silent: "passed-only",
    testTimeout: 15000,
    css: true,
  },
});
