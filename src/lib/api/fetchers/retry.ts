/**
 * 重试机制模块
 *
 * 提供可配置的 HTTP 请求重试逻辑，支持：
 * - 指定重试次数
 * - 自定义可重试的 HTTP 状态码
 * - 指数退避策略
 */

import { RetryConstants } from '@/src/lib/config/constants/api';

/**
 * 重试配置选项
 */
export interface RetryOptions {
    /** 最大重试次数，默认为 0（不重试） */
    retries?: number;
    /** 可重试的 HTTP 状态码列表，默认为 [429, 503, 504] */
    retryOn?: number[];
    /** 基础退避时间（毫秒），默认为 500ms */
    baseDelayMs?: number;
    /** 中断信号，用于取消重试等待 */
    signal?: AbortSignal;
    /** 是否在瞬时网络错误时重试 */
    retryNetworkErrors?: boolean;
    /** 客户端退避等待上限 */
    maxDelayMs?: number;
    /** 服务端 Retry-After 等待上限 */
    maxRetryAfterMs?: number;
}

/**
 * 默认重试配置
 */
export const DEFAULT_RETRY_OPTIONS = {
    retries: RetryConstants.DEFAULT_RETRIES,
    retryOn: Array.from(RetryConstants.RETRY_ON_STATUS_CODES),
    baseDelayMs: RetryConstants.BASE_DELAY_MS,
    retryNetworkErrors: false,
    maxDelayMs: 5_000,
    maxRetryAfterMs: 300_000,
} as const;

function getAbortReason(signal?: AbortSignal): unknown {
    if (!signal) {
        return undefined;
    }

    return (signal as AbortSignal & { reason?: unknown }).reason;
}

function createAbortError(reason?: unknown): Error {
    if (reason instanceof Error) {
        return reason;
    }

    if (typeof DOMException === "function") {
        return new DOMException("The operation was aborted.", "AbortError");
    }

    const error = new Error("The operation was aborted.");
    error.name = "AbortError";
    return error;
}

function throwIfAborted(signal?: AbortSignal): void {
    if (!signal?.aborted) {
        return;
    }

    throw createAbortError(getAbortReason(signal));
}

function isAbortLikeError(error: unknown): boolean {
    return error instanceof Error &&
        (error.name === "AbortError" || error.name === "TimeoutError");
}

function isRetryableNetworkError(error: unknown): boolean {
    if (!(error instanceof Error) || isAbortLikeError(error)) {
        return false;
    }

    if (error instanceof TypeError) {
        return true;
    }

    return /\b(fetch failed|network|load failed|failed to fetch)\b/i.test(error.message);
}

function parseRetryAfterMs(value: string | null): number | null {
    if (!value) {
        return null;
    }

    const seconds = Number(value);
    if (Number.isFinite(seconds) && seconds >= 0) {
        return seconds * 1_000;
    }

    const dateMs = Date.parse(value);
    if (Number.isNaN(dateMs)) {
        return null;
    }

    return Math.max(0, dateMs - Date.now());
}

function resolveRetryDelay(
    response: Response,
    attempt: number,
    baseDelayMs: number,
    maxDelayMs: number,
    maxRetryAfterMs: number
): number {
    const retryAfterMs = parseRetryAfterMs(response.headers.get("Retry-After"));
    if (retryAfterMs !== null) {
        return Math.min(maxRetryAfterMs, retryAfterMs);
    }

    return Math.min(maxDelayMs, calculateBackoffDelay(attempt, baseDelayMs));
}

async function sleep(ms: number, signal?: AbortSignal): Promise<void> {
    if (ms <= 0) {
        throwIfAborted(signal);
        return;
    }

    await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => {
            cleanup();
            resolve();
        }, ms);

        const handleAbort = () => {
            cleanup();
            reject(createAbortError(getAbortReason(signal)));
        };

        const cleanup = () => {
            clearTimeout(timer);
            signal?.removeEventListener("abort", handleAbort);
        };

        if (signal?.aborted) {
            handleAbort();
            return;
        }

        signal?.addEventListener("abort", handleAbort, { once: true });
    });
}

/**
 * 执行带重试机制的请求
 *
 * @param fetchFn - 执行请求的函数
 * @param options - 重试配置选项
 * @returns Promise<Response> - 请求响应
 *
 * @example
 * ```ts
 * const response = await fetchWithRetry(
 *   () => fetch(url, options),
 *   { retries: 3, retryOn: [429, 503] }
 * );
 * ```
 */
export async function fetchWithRetry(
    fetchFn: () => Promise<Response>,
    options: RetryOptions = {}
): Promise<Response> {
    const {
        retries = DEFAULT_RETRY_OPTIONS.retries,
        retryOn = DEFAULT_RETRY_OPTIONS.retryOn,
        baseDelayMs = DEFAULT_RETRY_OPTIONS.baseDelayMs,
        signal,
        retryNetworkErrors = DEFAULT_RETRY_OPTIONS.retryNetworkErrors,
        maxDelayMs = DEFAULT_RETRY_OPTIONS.maxDelayMs,
        maxRetryAfterMs = DEFAULT_RETRY_OPTIONS.maxRetryAfterMs,
    } = options;

    let attempt = 0;

    while (true) {
        throwIfAborted(signal);

        try {
            const response = await fetchFn();
            if (!retryOn.includes(response.status) || attempt >= retries) {
                return response;
            }

            await sleep(
                resolveRetryDelay(response, attempt, baseDelayMs, maxDelayMs, maxRetryAfterMs),
                signal,
            );
        } catch (error) {
            if (
                attempt >= retries ||
                !retryNetworkErrors ||
                !isRetryableNetworkError(error)
            ) {
                throw error;
            }

            await sleep(
                Math.min(maxDelayMs, calculateBackoffDelay(attempt, baseDelayMs)),
                signal,
            );
        }

        attempt += 1;
    }
}

/**
 * 计算退避延迟时间
 *
 * @param attempt - 当前尝试次数（从 0 开始）
 * @param baseDelayMs - 基础延迟时间
 * @returns 延迟时间（毫秒）
 *
 * @example
 * ```ts
 * calculateBackoffDelay(0, 500); // 500
 * calculateBackoffDelay(1, 500); // 1000
 * calculateBackoffDelay(2, 500); // 2000
 * ```
 */
export function calculateBackoffDelay(attempt: number, baseDelayMs: number): number {
    return baseDelayMs * Math.pow(2, attempt);
}
