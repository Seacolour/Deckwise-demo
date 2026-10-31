import { defineConfig } from "@playwright/test";
import { playwrightDevWebServer, playwrightSharedConfig } from "./playwright.shared";

export default defineConfig({
  ...playwrightSharedConfig,
  webServer: playwrightDevWebServer,
});
