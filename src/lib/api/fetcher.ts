/**
 * API 请求核心模块
 *
 * 提供统一的 API 请求接口，支持：
 * - 自动添加认证头
 * - 请求参数序列化
 * - 超时控制
 * - 重试机制
 * - 大数字精度保护
 * - 统一错误处理
 */

import type { ApiResp } from '@/src/lib/types/types';
import { isSuccessCode } from '@/src/lib/types/types';
import { CONFIG } from '@/src/lib/config/env';
import { isObject } from '@/src/lib/types/guards';
import { fetchWithRetry, type RetryOptions } from './fetchers/retry';
import { RequestTimeoutError } from './requestError';
import {
    handleHttpError,
    handleBusinessError,
    handleParseError,
    createEmptyResponse,
    AUTH_ERROR_CODES,
} from './fetchers/error-handler';
import { parseJsonWithBigNumberSupport } from './fetchers/big-number';
import {
    registerManagedRequest,
    type ManagedRequestKind,
} from './requestRuntime';
import { joinApiUrl } from './apiUrl';

// 重新导出 AUTH_ERROR_CODES 以保持向后兼容
export { AUTH_ERROR_CODES };

type NativeRequestInit = Omit<globalThis.RequestInit, 'body'> & {
    body?: BodyInit | Record<string, unknown> | null;
};

export type ApiRequestPolicy = 'query' | 'mutation' | 'upload';

const DEFAULT_QUERY_TIMEOUT_MS = 15_000;
const DEFAULT_QUERY_RETRIES = 1;

function buildUrl(url: string, params?: Record<string, string | number | boolean | null | undefined>): string {
    if (!params || !Object.keys(params).length) return url;

    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null) qs.append(k, String(v));
    }

    return url + (url.includes('?') ? '&' : '?') + qs.toString();
}

function authHeader(): Record<string, string> {
    return {};
}

function toHeaderObject(h?: HeadersInit): Record<string, string> {
    if (!h) return {};
    if (h instanceof Headers) {
        const o: Record<string, string> = {};
        h.forEach((v, k) => (o[k] = v));
        return o;
    }
    if (Array.isArray(h)) return Object.fromEntries(h as [string, string][]);
    return h as Record<string, string>;
}

function isPlainObjectBody(v: unknown): v is Record<string, unknown> {
    return (
        isObject(v) &&
        !(v instanceof FormData) &&
        !(typeof Blob !== 'undefined' && v instanceof Blob) &&
        !(v instanceof ArrayBuffer) &&
        !ArrayBuffer.isView(v)
    );
}

function normalizeMethod(method?: string): string {
    return (method ?? 'GET').toUpperCase();
}

function isUploadBody(body: BodyInit | undefined): boolean {
    return (
        (typeof FormData !== 'undefined' && body instanceof FormData) ||
        (typeof Blob !== 'undefined' && body instanceof Blob) ||
        body instanceof ArrayBuffer ||
        ArrayBuffer.isView(body)
    );
}

function resolveRequestPolicy(
    requestPolicy: ApiRequestPolicy | undefined,
    method: string,
    body: BodyInit | undefined
): ApiRequestPolicy {
    if (requestPolicy) {
        return requestPolicy;
    }

    if (isUploadBody(body)) {
        return 'upload';
    }

    if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
        return 'query';
    }

    return 'mutation';
}

function resolveTimeoutMs(
    timeoutMs: number | undefined,
    requestPolicy: ApiRequestPolicy
): number | undefined {
    if (timeoutMs === 0) {
        return undefined;
    }

    if (typeof timeoutMs === 'number' && Number.isFinite(timeoutMs) && timeoutMs > 0) {
        return timeoutMs;
    }

    return requestPolicy === 'query' ? DEFAULT_QUERY_TIMEOUT_MS : undefined;
}

function toManagedRequestKind(policy: ApiRequestPolicy): ManagedRequestKind {
    switch (policy) {
        case 'query':
            return 'query';
        case 'mutation':
            return 'mutation';
        case 'upload':
            return 'upload';
        default:
            return 'unknown';
    }
}

function isPublicApiPath(path: string): boolean {
    return path === '/user/login' || path === '/user/login/email-code';
}

function createTimeoutError(timeoutMs: number): Error {
    return new RequestTimeoutError(timeoutMs);
}

function getAbortReason(signal?: AbortSignal): unknown {
    if (!signal) {
        return undefined;
    }

    return (signal as AbortSignal & { reason?: unknown }).reason;
}

function composeAbortSignal(signals: Array<AbortSignal | null | undefined>): {
    signal?: AbortSignal;
    cleanup: () => void;
} {
    const activeSignals = signals.filter((signal): signal is AbortSignal => Boolean(signal));
    if (activeSignals.length <= 1) {
        return {
            signal: activeSignals[0],
            cleanup: () => {},
        };
    }

    const controller = new AbortController();
    const listeners = new Map<AbortSignal, () => void>();

    const cleanup = () => {
        listeners.forEach((listener, signal) => {
            signal.removeEventListener('abort', listener);
        });
        listeners.clear();
    };

    const abortFrom = (signal: AbortSignal) => {
        if (!controller.signal.aborted) {
            controller.abort(getAbortReason(signal));
        }
        cleanup();
    };

    for (const signal of activeSignals) {
        if (signal.aborted) {
            abortFrom(signal);
            return {
                signal: controller.signal,
                cleanup,
            };
        }

        const listener = () => abortFrom(signal);
        listeners.set(signal, listener);
        signal.addEventListener('abort', listener, { once: true });
    }

    return {
        signal: controller.signal,
        cleanup,
    };
}

/**
 * 额外请求选项
 */
type Extra = {
    raw?: true;
    params?: Record<string, string | number | boolean | null | undefined>;
    requestPolicy?: ApiRequestPolicy;
    timeoutMs?: number;
    retries?: number;
    retryOn?: number[];
    disableAuthHeader?: boolean;
};

/**
 * API 请求函数类型定义
 */
export async function apiFetch(
    path: string,
    init?: NativeRequestInit & Extra & { raw: true }
): Promise<Response>;
export async function apiFetch<T>(
    path: string,
    init?: NativeRequestInit & Extra
): Promise<T>;

/**
 * 核心 API 请求函数
 *
 * @param path - API 路径
 * @param init - 请求配置选项
 * @returns Promise<T | Response> - 响应数据或原始 Response 对象
 *
 * @example
 * ```ts
 * // 基本请求
 * const data = await apiFetch<User>('/user/info');
 *
 * // 带参数请求
 * const list = await apiFetch<Item[]>('/items', { params: { page: 1, size: 10 } });
 *
 * // POST 请求
 * await apiFetch('/user/update', {
 *   method: 'POST',
 *   body: { name: 'test' }
 * });
 *
 * // 带重试的请求
 * const data = await apiFetch('/unstable-api', { retries: 3 });
 *
 * // 获取原始响应
 * const response = await apiFetch('/download', { raw: true });
 * ```
 */
export async function apiFetch<T>(
    path: string,
    init?: NativeRequestInit & Extra
): Promise<T | Response> {
    const {
        raw,
        params,
        requestPolicy,
        timeoutMs,
        headers: initHeaders,
        body: initBody,
        retries,
        retryOn,
        disableAuthHeader,
        ...restInit
    } = init ?? {};

    // 构建完整 URL
    const apiBase = CONFIG.API_BASE_URL.replace(/\/$/, '');
    let url = path.startsWith('http') ? path : joinApiUrl(apiBase, path);
    url = buildUrl(url, params);

    // 处理请求头和请求体
    const baseHeaders = toHeaderObject(initHeaders);
    let body: BodyInit | undefined = initBody as BodyInit | undefined;

    if (isPlainObjectBody(initBody)) {
        body = JSON.stringify(initBody);
        baseHeaders['Content-Type'] = 'application/json';
    }

    const headers = new Headers(CONFIG.APP_HEADERS);
    // JSON is the contract for the shared API client. Sending an explicit
    // Accept header avoids the browser's `*/*` fallback reaching Spring's
    // response negotiation and being reused as an invalid Content-Type.
    headers.set('Accept', 'application/json');
    Object.entries(baseHeaders).forEach(([key, value]) => headers.set(key, value));
    if (!disableAuthHeader) {
        Object.entries(authHeader()).forEach(([key, value]) => headers.set(key, value));
    }

    const method = normalizeMethod(restInit.method);
    const resolvedRequestPolicy = resolveRequestPolicy(requestPolicy, method, body);
    const resolvedTimeoutMs = resolveTimeoutMs(timeoutMs, resolvedRequestPolicy);
    const runtimeController =
        typeof AbortController !== 'undefined'
            ? new AbortController()
            : undefined;
    const managedRequest = runtimeController
        ? registerManagedRequest({
            controller: runtimeController,
            path,
            method,
            kind: toManagedRequestKind(resolvedRequestPolicy),
            authSensitive: !disableAuthHeader && !isPublicApiPath(path),
        })
        : null;

    // 设置超时
    const timeoutController =
        typeof AbortController !== 'undefined' && resolvedTimeoutMs
            ? new AbortController()
            : undefined;
    let didTimeout = false;
    const tid = timeoutController && resolvedTimeoutMs
        ? setTimeout(() => {
            didTimeout = true;
            timeoutController.abort(createTimeoutError(resolvedTimeoutMs));
        }, resolvedTimeoutMs)
        : undefined;
    const { signal: requestSignal, cleanup: cleanupSignal } = composeAbortSignal([
        runtimeController?.signal,
        timeoutController?.signal,
        (restInit as NativeRequestInit).signal,
    ]);
    try {
        // 创建请求函数
        const doFetch = async (): Promise<Response> => {
            return await fetch(url, {
                ...restInit,
                // Web auth is cookie-based. Same-origin /api calls should include
                // the httpOnly session cookie; callers can still override this.
                credentials: (restInit as NativeRequestInit).credentials ?? "include",
                headers,
                body,
                signal: requestSignal,
            });
        };

        // 执行请求（带重试）
        const retryOptions: RetryOptions = {
            retries: retries ?? (resolvedRequestPolicy === 'query' ? DEFAULT_QUERY_RETRIES : 0),
            retryOn,
            signal: requestSignal,
            retryNetworkErrors: resolvedRequestPolicy === 'query',
        };
        const res = await fetchWithRetry(doFetch, retryOptions);

        // 如果请求原始响应，直接返回
        if (raw) return res;

        // 处理 HTTP 层错误
        if (!res.ok) {
            await handleHttpError(res, url);
        }

        // 解析响应体
        const rawText = await res.text();

        // 如果响应体为空或只包含空白字符，返回默认成功响应
        if (!rawText || rawText.trim().length === 0) {
            return createEmptyResponse<T>().data as T;
        }

        // 解析 JSON（带大数字精度保护）
        let json: ApiResp<T>;
        try {
            json = parseJsonWithBigNumberSupport<ApiResp<T>>(rawText);
        } catch (parseError) {
            json = handleParseError(url, parseError, rawText) as ApiResp<T>;
        }

        // 处理业务层错误
        if (!isSuccessCode(json.code)) {
            handleBusinessError(json, url);
        }

        return json.data as T;
    } catch (error) {
        if (didTimeout && error instanceof Error && error.name === 'AbortError' && resolvedTimeoutMs) {
            throw createTimeoutError(resolvedTimeoutMs);
        }

        throw error;
    } finally {
        if (tid) clearTimeout(tid);
        cleanupSignal();
        managedRequest?.unregister();
    }
}
