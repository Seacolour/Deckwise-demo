import { defineConfig } from "@playwright/test";
import { playwrightBuildWebServer, playwrightSharedConfig } from "./playwright.shared";

export default defineConfig({
  ...playwrightSharedConfig,
  webServer: playwrightBuildWebServer,
});
