// ============================================================================
// Next.js API 代理路由 — /api/[...path]
// ============================================================================
//
// 所有 /api/* 请求经由此文件代理到后端，避免浏览器直连后端产生 CORS 问题。
//
// 上传可选用 UPLOAD_PROXY_TARGET；为空时与其他请求一样走 API_PROXY_TARGET。
// 浏览器始终调用同源 /api，避免跨域 Cookie 和 SSE 配置漂移。
//
// ============================================================================

import { SERVER_CONFIG } from '@/src/lib/config/env.server';
import { APP_HEADERS } from '@/src/lib/config/env';
import { serverInfo } from '@/src/lib/server/log';
import {
  buildApiProxyRejectionResponse,
  inspectKnownApiProxyRequest,
  isRequestAbort,
  normalizeKnownApiRequestHeaders,
} from '@/src/lib/server/apiProxyRequestPolicy';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const HOP_BY_HOP_HEADERS = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailers',
  'transfer-encoding',
  'upgrade',
]);

const STRIP_RESPONSE_HEADERS = new Set([
  ...HOP_BY_HOP_HEADERS,
  // These must NOT be forwarded as-is when proxying via fetch(),
  // otherwise browsers may try to decode an already-decoded body.
  'content-encoding',
  'content-length',
]);
const API_PROXY_LOGS_ENABLED =
  process.env.NODE_ENV !== 'production' || process.env.DECKWISE_API_PROXY_LOGS === 'true';
const UPSTREAM_UNAVAILABLE_ERROR_CODE = 'UPSTREAM_UNAVAILABLE';

let apiProxyRequestSequence = 0;

// 只有现存文件上传接口使用可选的独立上传目标。
const UPLOAD_PATH_PREFIXES = [
  'files/upload',
  'files/pending/upload',
];

function isUploadPath(pathParts: string[]): boolean {
  const joined = pathParts.join('/');
  return UPLOAD_PATH_PREFIXES.some((prefix) => joined.startsWith(prefix));
}

function buildTargetUrl(req: Request, pathParts: string[]): string {
  // 上传请求走专用上传服务器（UPLOAD_PROXY_TARGET），fallback 到主 API 服务器。
  // 本地开发通常只配置 API_PROXY_TARGET；需要独立上传服务时再配置 UPLOAD_PROXY_TARGET。
  const uploadTarget = SERVER_CONFIG.UPLOAD_PROXY_TARGET?.replace(/\/$/, '');
  const apiTarget = SERVER_CONFIG.API_PROXY_TARGET.replace(/\/$/, '');
  const base = (isUploadPath(pathParts) && uploadTarget) ? uploadTarget : apiTarget;
  if (!base) throw new Error('API_PROXY_TARGET is not configured');
  const p = pathParts.join('/');
  const url = `${base}/api/${p}`;
  const qs = new URL(req.url).search;
  return qs ? `${url}${qs}` : url;
}

function resolveProxyTargetKind(pathParts: string[]): 'api' | 'upload' {
  const uploadTarget = SERVER_CONFIG.UPLOAD_PROXY_TARGET?.trim();
  return isUploadPath(pathParts) && uploadTarget ? 'upload' : 'api';
}

function nextApiProxyRequestId(): string {
  apiProxyRequestSequence = apiProxyRequestSequence >= Number.MAX_SAFE_INTEGER
    ? 1
    : apiProxyRequestSequence + 1;
  return `fe-${apiProxyRequestSequence.toString().padStart(5, '0')}`;
}

function formatLocalTimestamp(date = new Date()): string {
  const offsetMinutes = -date.getTimezoneOffset();
  const offsetSign = offsetMinutes >= 0 ? '+' : '-';
  const absoluteOffsetMinutes = Math.abs(offsetMinutes);
  const offsetHours = Math.floor(absoluteOffsetMinutes / 60);
  const remainingOffsetMinutes = absoluteOffsetMinutes % 60;

  return `${date.getFullYear()}-${padDatePart(date.getMonth() + 1)}-${padDatePart(date.getDate())}`
    + `T${padDatePart(date.getHours())}:${padDatePart(date.getMinutes())}:${padDatePart(date.getSeconds())}`
    + `.${date.getMilliseconds().toString().padStart(3, '0')}`
    + `${offsetSign}${padDatePart(offsetHours)}:${padDatePart(remainingOffsetMinutes)}`;
}

function padDatePart(value: number): string {
  return String(value).padStart(2, '0');
}

function nowMs(): number {
  return typeof performance !== 'undefined' ? performance.now() : Date.now();
}

function elapsedMs(startedAtMs: number): number {
  return Math.round((nowMs() - startedAtMs) * 10) / 10;
}

function requestPathForLog(req: Request): string {
  try {
    const url = new URL(req.url);
    return `${url.pathname}${url.search}`;
  } catch {
    return req.url;
  }
}

function targetForLog(targetUrl: string): string {
  try {
    const url = new URL(targetUrl);
    return `${url.origin}${url.pathname}${url.search}`;
  } catch {
    return targetUrl;
  }
}

function logApiProxyRequest(
  phase: 'start' | 'end' | 'error',
  payload: Record<string, unknown>,
) {
  if (!API_PROXY_LOGS_ENABLED) {
    return;
  }

  const details = Object.entries(payload)
    .map(([key, value]) => `${key}=${formatLogValue(value)}`)
    .join(' ');
  serverInfo(`${formatLocalTimestamp()} [api-proxy] ${phase} ${details}`);
}

function formatLogValue(value: unknown): string {
  if (typeof value === 'string') {
    return value.length > 0 && !/\s/.test(value) ? value : JSON.stringify(value);
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  if (value === null) {
    return 'null';
  }
  if (value === undefined) {
    return 'undefined';
  }
  return JSON.stringify(value);
}

function readErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function buildUpstreamUnavailableResponse(options: {
  error: unknown;
  path: string;
  target: string;
  targetKind: 'api' | 'upload';
}): Response {
  const body: Record<string, unknown> = {
    code: UPSTREAM_UNAVAILABLE_ERROR_CODE,
    errorCode: UPSTREAM_UNAVAILABLE_ERROR_CODE,
    message: 'Upstream API unavailable.',
  };

  if (process.env.NODE_ENV !== 'production') {
    body.path = options.path;
    body.target = options.target;
    body.targetKind = options.targetKind;
    body.cause = readErrorMessage(options.error);
  }

  return Response.json(body, {
    status: 502,
    headers: {
      'cache-control': 'no-store',
      'x-deckwise-proxy-error': UPSTREAM_UNAVAILABLE_ERROR_CODE,
    },
  });
}

function buildForwardHeaders(req: Request): Headers {
    const src = req.headers;
    const h = new Headers();

    for (const [k, v] of src.entries()) {
        const key = k.toLowerCase();
        if (HOP_BY_HOP_HEADERS.has(key)) continue;
        if (key === 'host' || key === 'x-api-contract') continue;
        // Avoid upstream compression to reduce decoding mismatch risk.
        if (key === 'accept-encoding') continue;
        h.set(k, v);
    }

    // Web client identity is owned by this server-side proxy. Do not allow an
    // incoming browser header to impersonate another platform.
    for (const [key, value] of Object.entries(APP_HEADERS)) {
        h.set(key, value);
    }

    // Prefer configured API_HOST; otherwise infer from incoming headers.
    const host = SERVER_CONFIG.API_HOST.trim();

    if (host) {
        h.set('host', host);
        h.set('x-forwarded-host', host);
    } else {
        const inferred =
            src.get('x-forwarded-host') ??
            src.get('host') ??
            'localhost';
        h.set('host', inferred);
        h.set('x-forwarded-host', inferred);
    }

    if (!h.has('x-forwarded-proto')) {
        h.set('x-forwarded-proto', src.get('x-forwarded-proto') || 'https');
    }

    return h;
}

async function proxy(req: Request, ctx: { params: Promise<{ path?: string[] }> }) {
  const { path = [] } = await ctx.params;
  const rejection = await inspectKnownApiProxyRequest(req, path);
  if (rejection) {
    return buildApiProxyRejectionResponse(rejection);
  }

  const targetUrl = buildTargetUrl(req, path);
  const headers = buildForwardHeaders(req);
  normalizeKnownApiRequestHeaders(path, headers);
  const requestId = nextApiProxyRequestId();
  const startedAtMs = nowMs();

  const method = req.method.toUpperCase();
  const body: BodyInit | null =
    method === 'GET' || method === 'HEAD' ? null : req.body;

  logApiProxyRequest('start', {
    id: requestId,
    method,
    path: requestPathForLog(req),
    target: targetForLog(targetUrl),
    targetKind: resolveProxyTargetKind(path),
  });

  const init: RequestInit = {
    method,
    headers,
    body,
    redirect: 'follow',
    cache: 'no-store',
    signal: req.signal,
  };

  // Node fetch requires duplex for streaming request bodies.
  if (body && typeof body === 'object') {
    (init as RequestInit & { duplex?: string }).duplex = 'half';
  }

  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, init);
  } catch (error) {
    if (isRequestAbort(error, req.signal)) {
      return new Response(null, {
        status: 499,
        headers: {
          'cache-control': 'no-store',
          'x-deckwise-proxy-error': 'CLIENT_CLOSED_REQUEST',
        },
      });
    }

    const requestPath = requestPathForLog(req);
    const target = targetForLog(targetUrl);
    const targetKind = resolveProxyTargetKind(path);
    logApiProxyRequest('error', {
      id: requestId,
      method,
      path: requestPath,
      target,
      targetKind,
      durationMs: elapsedMs(startedAtMs),
      error: readErrorMessage(error),
    });
    return buildUpstreamUnavailableResponse({
      error,
      path: requestPath,
      target,
      targetKind,
    });
  }

  logApiProxyRequest('end', {
    id: requestId,
    method,
    path: requestPathForLog(req),
    target: targetForLog(targetUrl),
    targetKind: resolveProxyTargetKind(path),
    status: upstream.status,
    durationMs: elapsedMs(startedAtMs),
  });

  const respHeaders = new Headers();
  upstream.headers.forEach((v, k) => {
    const key = k.toLowerCase();
    if (STRIP_RESPONSE_HEADERS.has(key)) return;
    respHeaders.set(k, v);
  });

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: respHeaders,
  });
}

export function GET(req: Request, ctx: { params: Promise<{ path?: string[] }> }) {
  return proxy(req, ctx);
}
export function POST(req: Request, ctx: { params: Promise<{ path?: string[] }> }) {
  return proxy(req, ctx);
}
export function PUT(req: Request, ctx: { params: Promise<{ path?: string[] }> }) {
  return proxy(req, ctx);
}
export function PATCH(req: Request, ctx: { params: Promise<{ path?: string[] }> }) {
  return proxy(req, ctx);
}
export function DELETE(req: Request, ctx: { params: Promise<{ path?: string[] }> }) {
  return proxy(req, ctx);
}
export function OPTIONS(req: Request, ctx: { params: Promise<{ path?: string[] }> }) {
  return proxy(req, ctx);
}
