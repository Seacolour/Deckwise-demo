import { CONFIG } from "@/src/lib/config/env";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Health / diagnostics endpoint (no secrets).
 * Useful to confirm which config is currently effective.
 */
export async function GET() {
  return Response.json({
    ok: true,
    nodeEnv: process.env.NODE_ENV || "development",
    appEnv: CONFIG.ENV,
    envSignature: CONFIG.ENV_SIGNATURE,
    publicConfig: {
      apiBaseUrl: CONFIG.API_BASE_URL,
      debug: CONFIG.DEBUG,
      appVersion: CONFIG.APP_VERSION,
      appPlatform: CONFIG.APP_PLATFORM,
      backendSignature: CONFIG.BACKEND_SIGNATURE,
    },
  }, { headers: { "Cache-Control": "no-store" } });
}
