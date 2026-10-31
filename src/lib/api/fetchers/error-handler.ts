/**
 * API 错误处理模块
 *
 * 统一处理 API 请求中的各种错误情况：
 * - HTTP 层错误（4xx, 5xx）
 * - 业务层错误（code !== 0）
 * - JSON 解析错误
 * - 认证错误
 */

import type { ApiResp } from '@/src/lib/types/types';
import { ApiError } from '@/src/lib/types/types';
import { AUTH_ERROR_CODES as CONFIG_AUTH_ERROR_CODES, BusinessErrorCodes, HttpStatus, JsonParseConstants } from '@/src/lib/config/constants/api';
import { dispatchProjectMissing } from '@/src/lib/events/project';
import { extractProjectIdFromApiUrl, isProjectMissingError } from '@/src/lib/api/missingResource';

/** 40102 = 业务未登录/鉴权失败；401 = HTTP 未授权（兜底） */
export const AUTH_ERROR_CODES = CONFIG_AUTH_ERROR_CODES;

const AUTH_ERROR_MESSAGE_PATTERNS = [
    /(?:用户|账号|账户)不存在/u,
    /(?:用户|账号|账户)已注销/u,
    /未登录/u,
    /登录(?:状态)?(?:已)?(?:失效|过期)/u,
    /(?:鉴权|认证)失败/u,
    /token(?:已)?(?:失效|过期|无效)/iu,
    /\buser(?:\s+account)?\s+(?:not\s+found|does\s+not\s+exist|doesn't\s+exist|not\s+exist)\b/i,
    /\baccount\s+(?:not\s+found|does\s+not\s+exist|doesn't\s+exist|not\s+exist)\b/i,
    /\blogin(?:\s+state)?\s+(?:expired|invalid)\b/i,
    /\b(?:invalid|expired)\s+token\b/i,
    /\bunauthorized\b/i,
    /\bauth(?:entication|orization)?\s+failed\b/i,
] as const;

function collectAuthMessageCandidates(value: unknown, depth = 0, out = new Set<string>()): Set<string> {
    if (depth > 3 || value == null) {
        return out;
    }

    if (typeof value === 'string') {
        const trimmed = value.trim();
        if (trimmed) {
            out.add(trimmed);
        }
        return out;
    }

    if (Array.isArray(value)) {
        value.forEach((item) => collectAuthMessageCandidates(item, depth + 1, out));
        return out;
    }

    if (typeof value !== 'object') {
        return out;
    }

    const record = value as Record<string, unknown>;
    const fields = ['message', 'msg', 'error', 'detail', 'description', 'reason', 'data'];
    fields.forEach((field) => {
        if (field in record) {
            collectAuthMessageCandidates(record[field], depth + 1, out);
        }
    });

    return out;
}

function hasAuthRelatedMessage(value: unknown): boolean {
    const candidates = collectAuthMessageCandidates(value);
    for (const candidate of candidates) {
        if (AUTH_ERROR_MESSAGE_PATTERNS.some((pattern) => pattern.test(candidate))) {
            return true;
        }
    }
    return false;
}

function normalizeErrorCode(input: {
    code?: number;
    status?: number;
    message?: unknown;
    payload?: unknown;
}): number {
    if (shouldTreatAsAuthError(input)) {
        return BusinessErrorCodes.AUTH_FAILED;
    }

    if (typeof input.code === 'number') {
        return input.code;
    }

    if (typeof input.status === 'number') {
        return input.status;
    }

    return HttpStatus.INTERNAL_SERVER_ERROR;
}

export function shouldTreatAsAuthError(input: {
    code?: number;
    status?: number;
    message?: unknown;
    payload?: unknown;
}): boolean {
    if (typeof input.code === 'number' && AUTH_ERROR_CODES.has(input.code)) {
        return true;
    }

    if (typeof input.status === 'number' && input.status === HttpStatus.UNAUTHORIZED) {
        return true;
    }

    if (hasAuthRelatedMessage(input.message)) {
        return true;
    }

    return hasAuthRelatedMessage(input.payload);
}

/**
 * 从响应载荷中提取 traceId
 */
export function getTraceId(payload: unknown): string | undefined {
    return typeof payload === 'object' && payload !== null && 'traceId' in payload
        ? (payload as { traceId?: string }).traceId
        : undefined;
}

/**
 * HTTP 层错误信息
 */
interface HttpErrorInfo {
    url: string;
    status: number;
    payload?: unknown;
    traceId?: string;
}

/**
 * 业务层错误信息
 */
interface BusinessErrorInfo {
    url: string;
    code: number;
    message: string;
    traceId?: string;
    payload?: ApiResp<unknown>;
}

function dispatchMissingProjectFromError(input: {
    url: string;
    code?: number;
    status?: number;
    message?: string | null;
    payload?: unknown;
}) {
    // Fast-path: skip URL parsing and error inspection for non-project endpoints.
    if (!input.url.includes('/projects/')) {
        return;
    }

    const missingProjectId = extractProjectIdFromApiUrl(input.url);
    if (!missingProjectId || !isProjectMissingError(input)) {
        return;
    }

    dispatchProjectMissing({
        id: missingProjectId,
        sourceUrl: input.url,
        code: input.code ?? input.status,
        message: input.message ?? null,
    });
}

/**
 * JSON 解析错误信息
 */
interface ParseErrorInfo {
    url: string;
    error: string;
    textPreview: string;
}

/**
 * 处理 HTTP 层错误（非 2xx 响应）
 *
 * @param response - Fetch 响应对象
 * @param url - 请求 URL（用于日志）
 * @throws ApiError HTTP 错误
 */
export async function handleHttpError(response: Response, url: string): Promise<never> {
    let payload: unknown;
    try {
        payload = await response.clone().json();
    } catch (e) {
        // Non-blocking: response may be non-JSON (HTML/plain text). Keep payload undefined.
        if (process.env.NODE_ENV !== "production") {
            console.warn("[apiFetch] Non-JSON error payload", e);
        }
    }

    const traceId = getTraceId(payload);

    const errorInfo: HttpErrorInfo = {
        url,
        status: response.status,
        payload,
        traceId,
    };

    console.warn('[API HTTP ERROR]', errorInfo);

    const resolvedCode = normalizeErrorCode({
        code: response.status,
        status: response.status,
        payload,
    });
    const resolvedMessage = collectAuthMessageCandidates(payload).values().next().value ?? `HTTP ${response.status}`;
    dispatchMissingProjectFromError({
        url,
        code: resolvedCode,
        status: response.status,
        message: typeof resolvedMessage === "string" ? resolvedMessage : null,
        payload,
    });

    throw new ApiError(
        resolvedMessage,
        resolvedCode,
        traceId,
        payload
    );
}

/**
 * 处理业务层错误（code !== 0）
 *
 * @param json - 已解析的 API 响应
 * @param url - 请求 URL（用于日志）
 * @throws ApiError 业务错误
 */
export function handleBusinessError<T>(json: ApiResp<T>, url: string): never {
    const traceId = json.traceId;
    const resolvedCode = normalizeErrorCode({
        code: json.code,
        message: json.message,
        payload: json,
    });

    const errorInfo: BusinessErrorInfo = {
        url,
        code: json.code,
        message: json.message,
        traceId,
        payload: json as ApiResp<unknown>,
    };

    // 对未登录/鉴权失败不打印 error，避免 Next Dev Overlay 红屏
    if (resolvedCode === BusinessErrorCodes.AUTH_FAILED) {
        console.warn('[API AUTH REQUIRED]', errorInfo);
    } else {
        console.warn('[API BUSINESS ERROR]', errorInfo);
    }

    dispatchMissingProjectFromError({
        url,
        code: resolvedCode,
        message: json.message ?? null,
        payload: json,
    });

    throw new ApiError(
        json.message || 'Request failed',
        resolvedCode,
        traceId,
        json
    );
}

/**
 * 处理 JSON 解析错误
 *
 * @param url - 请求 URL（用于日志）
 * @param parseError - 解析错误对象
 * @param rawText - 原始响应文本
 * @param responseStatus - 响应状态码
 * @throws Error 解析错误
 */
export function handleParseError(
    url: string,
    parseError: unknown,
    rawText: string
): ApiResp<unknown> {
    const errorInfo: ParseErrorInfo = {
        url,
        error: parseError instanceof Error ? parseError.message : String(parseError),
        textPreview: rawText.slice(0, JsonParseConstants.TEXT_PREVIEW_LENGTH),
    };

    console.error('[API JSON PARSE ERROR]', errorInfo);
    throw parseError;
}

/**
 * 创建默认的空响应对象
 * 用于响应体为空的情况
 *
 * @returns 默认的成功响应
 */
export function createEmptyResponse<T>(): ApiResp<T> {
    return {
        code: 0,
        message: 'success',
        data: undefined as T,
        timestamp: new Date().toISOString(),
    };
}
