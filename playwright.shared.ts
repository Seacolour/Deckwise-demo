import type { PlaywrightTestConfig } from "@playwright/test";

export const PLAYWRIGHT_BASE_URL = "http://127.0.0.1:3310";
const PLAYWRIGHT_BUILD_START_COMMAND = [
  // Playwright forces colored webServer output; clear an inherited NO_COLOR so
  // Node does not warn about conflicting terminal color settings in CI logs.
  "cross-env NO_COLOR= node ./scripts/tests/cleanNextBuild.mjs",
  "cross-env NO_COLOR= npm run build",
  "cross-env NO_COLOR= node ./scripts/tests/prepareStandaloneStatic.mjs",
  "cross-env NO_COLOR= HOSTNAME=127.0.0.1 PORT=3310 node .next/standalone/server.js",
].join(" && ");

export const playwrightSharedConfig: Pick<PlaywrightTestConfig, "testDir" | "timeout" | "use"> = {
  testDir: "./tests/e2e",
  timeout: 45_000,
  use: {
    baseURL: PLAYWRIGHT_BASE_URL,
    headless: true,
  },
};

export const playwrightBuildWebServer: PlaywrightTestConfig["webServer"] = {
  command: PLAYWRIGHT_BUILD_START_COMMAND,
  port: 3310,
  reuseExistingServer: false,
  timeout: 120_000,
};

export const playwrightDevWebServer: PlaywrightTestConfig["webServer"] = {
  // Pitfall: a dev-only config that simply disables `webServer` looks tidy, but
  // it also bakes in an undocumented "you must have started the app already"
  // precondition. Reuse an existing local server when available, but keep a
  // deterministic build/start fallback so ad-hoc runs do not fail with either
  // connection-refused or `.next/dev/lock` errors from a stray Next dev shell.
  command: PLAYWRIGHT_BUILD_START_COMMAND,
  port: 3310,
  reuseExistingServer: true,
  timeout: 120_000,
};
