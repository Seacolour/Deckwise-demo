/**
 * 客户端环境变量配置
 *
 * Production-grade rules:
 * - Only `NEXT_PUBLIC_*` may be imported by client code.
 * - Public env is **build-time** for Next.js (rebuild to change).
 * - Validate and normalize with schema to avoid silent misconfig.
 */

import { createEnv } from "@t3-oss/env-nextjs";
import { z } from "zod";

export type AppEnv = 'development' | 'staging' | 'production';

const booleanFromString = z.preprocess((v) => {
  if (v === undefined || v === null || v === "") return undefined;
  if (v === "true") return true;
  if (v === "false") return false;
  return v;
}, z.boolean());

// Web client identity is fixed for this repository.
const STATIC_APP_VERSION = "2025";
const STATIC_APP_PLATFORM = "web";

const publicEnv = createEnv({
  client: {
    // `staging` is allowed with NODE_ENV=production (production build).
    NEXT_PUBLIC_APP_ENV: z.enum(["development", "staging", "production"]).optional(),

    NEXT_PUBLIC_API_BASE_URL: z.string().min(1).default("/api"),
    NEXT_PUBLIC_DEBUG: booleanFromString.default(false),
    NEXT_PUBLIC_BACKEND_SIGNATURE: z.string().min(1).default("local"),

  },
  runtimeEnv: {
    NEXT_PUBLIC_APP_ENV: process.env.NEXT_PUBLIC_APP_ENV,
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
    NEXT_PUBLIC_DEBUG: process.env.NEXT_PUBLIC_DEBUG,
    NEXT_PUBLIC_BACKEND_SIGNATURE: process.env.NEXT_PUBLIC_BACKEND_SIGNATURE,
  },
});

// Environment semantics:
// - NODE_ENV controls Next build/runtime mode.
// - NEXT_PUBLIC_APP_ENV controls product environment: development/staging/production.
const nodeEnv = process.env.NODE_ENV || "development";
export const APP_ENV: AppEnv =
  (publicEnv.NEXT_PUBLIC_APP_ENV as AppEnv | undefined) ||
  (nodeEnv === "development" ? "development" : "production");

// Guardrails against ambiguous configs.
if (nodeEnv === "development" && publicEnv.NEXT_PUBLIC_APP_ENV && publicEnv.NEXT_PUBLIC_APP_ENV !== "development" && publicEnv.NEXT_PUBLIC_APP_ENV !== "staging") {
  throw new Error(
    `[env] NODE_ENV=development but NEXT_PUBLIC_APP_ENV=${publicEnv.NEXT_PUBLIC_APP_ENV}. ` +
    `Use NEXT_PUBLIC_APP_ENV=development (or omit it in dev).`
  );
}
if (nodeEnv === "production" && publicEnv.NEXT_PUBLIC_BACKEND_SIGNATURE === "local") {
  throw new Error(
    "[env] NEXT_PUBLIC_BACKEND_SIGNATURE must be set for production/staging builds (not 'local')."
  );
}

// API 基址
export const API_BASE_URL =
  publicEnv.NEXT_PUBLIC_API_BASE_URL;

// Debug 开关
export const DEBUG =
  publicEnv.NEXT_PUBLIC_DEBUG;

// 应用元信息
export const APP_VERSION =
  STATIC_APP_VERSION;

export const APP_PLATFORM =
  STATIC_APP_PLATFORM;

// 后端签名（用于环境变更检测）
export const BACKEND_SIGNATURE =
  publicEnv.NEXT_PUBLIC_BACKEND_SIGNATURE;

// 应用请求头（单独导出供服务端使用）
export const APP_HEADERS = {
  'X-App-Version': APP_VERSION,
  'X-App-Platform': APP_PLATFORM,
} as const;

// 统一导出（保持向后兼容）
export const CONFIG = {
  // Backward-compat: only two buckets were used historically.
  // Treat staging as "prod-like" for routing/feature gates that rely on CONFIG.ENV.
  ENV: APP_ENV === 'development' ? 'sandbox' : 'prod',
  API_BASE_URL,
  DEBUG,
  APP_VERSION,
  APP_PLATFORM,
  BACKEND_SIGNATURE,
  APP_HEADERS,

  // 环境签名（用于登录态管理）
  ENV_SIGNATURE: `${APP_ENV}|${API_BASE_URL}|${BACKEND_SIGNATURE}`,
} as const;

export type PublicEnv = typeof CONFIG;

// 兼容旧的导出方式
export function getCONFIG(): PublicEnv {
  return CONFIG;
}
