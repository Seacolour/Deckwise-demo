/**
 * 服务端环境变量配置
 *
 * 仅在服务端可访问，包含敏感信息。
 * 不要在客户端组件中 import 这个文件。
 *
 * 主要使用方：app/api/[...path]/route.ts（API 代理路由）。
 *
 * 环境变量来源（Next.js 加载优先级）：
 *   NODE_ENV=development → .env.local > .env
 *   NODE_ENV=production  → .env.production.local > .env.local > .env.production > .env
 *
 * 关键变量说明：
 *   API_PROXY_TARGET   — 主 API 后端地址（本地默认 http://localhost:8104）
 *   UPLOAD_PROXY_TARGET — 可选的上传服务地址；为空时使用 API_PROXY_TARGET。
 *
 * 本地开发时两者可以指向同一后端（上传接口在同一服务器上）。
 * 如需分离上传服务，可单独配置 UPLOAD_PROXY_TARGET。
 */

import { z } from "zod";

export type ServerEnv = Readonly<{
  API_PROXY_TARGET: string;
  // 可选上传后端地址；为空则使用 API_PROXY_TARGET。
  // 由 app/api/[...path]/route.ts 中的 buildTargetUrl() 使用。
  // ⚠️ 这是服务端变量（无 NEXT_PUBLIC_ 前缀），修改后 restart 即可，无需 rebuild。
  UPLOAD_PROXY_TARGET: string;
  API_HOST: string;
}>;

let _cached: ServerEnv | null = null;

export function __resetServerEnvForTests() {
  _cached = null;
}

function readServerEnv(): ServerEnv {
  if (_cached) return _cached;

  const nodeEnv = process.env.NODE_ENV || "development";
  const isProdRuntime = nodeEnv === "production";

  const apiProxyTargetRaw =
    process.env.API_PROXY_TARGET ||
    (isProdRuntime ? "" : "http://localhost:8104");

  const apiProxyTargetSchema = isProdRuntime
    ? z.url()
    : z.url().default("http://localhost:8104");

  /** 上传专用后端地址；不配置时与主 API 共用后端。 */
  const uploadProxyTargetRaw =
    process.env.UPLOAD_PROXY_TARGET || "";

  _cached = {
    API_PROXY_TARGET: apiProxyTargetSchema.parse(apiProxyTargetRaw),
    UPLOAD_PROXY_TARGET: uploadProxyTargetRaw,
    API_HOST: z.string().optional().default("").parse(process.env.API_HOST),
  };
  return _cached;
}

// Lazy getters avoid failing `next build` when server-only env is provided at runtime.
export const SERVER_CONFIG: ServerEnv = {
  get API_PROXY_TARGET() {
    return readServerEnv().API_PROXY_TARGET;
  },
  get UPLOAD_PROXY_TARGET() {
    return readServerEnv().UPLOAD_PROXY_TARGET;
  },
  get API_HOST() {
    return readServerEnv().API_HOST;
  },
};
