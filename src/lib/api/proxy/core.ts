// lib/api/proxy/core.ts
/**
 * 统一代理下载核心模块
 * 提取公共的 fetch 逻辑、错误处理和头部处理
 */

import {CONFIG} from '@/src/lib/config/env';
import {ProxyConstants} from '@/src/lib/config/constants/api';
import {
    createManagedRequestContext,
    isLikelyAuthSensitiveClientUrl,
} from '@/src/lib/api/requestRuntime';

/* ---------------------------------------------------------------------------
   类型定义
--------------------------------------------------------------------------- */

/** 进度回调函数类型 */
export type ProgressFn = (progress: number, written: number, expected: number) => void;

/** 基础代理选项 - 所有代理类型通用 */
export type BaseProxyOptions = {
    /** 进度回调；若无 Content-Length，则 progress=-1, expected=-1 */
    onProgress?: ProgressFn;
    /** 超时毫秒；默认不超时 */
    timeoutMs?: number;
    /** 额外请求头 */
    headers?: Record<string, string>;
    /** 取消信号 */
    signal?: AbortSignal;
    /** 请求的 Accept 头；默认根据代理类型自动设置 */
    accept?: string;
};

/** 代理结果的基础信息 */
export type ProxyResultBase = {
    blob: Blob;
    contentType?: string | null;
    contentLength?: number | null;
};

/** 从响应中提取的元数据 */
export type ResponseMetadata = {
    contentType: string | null;
    contentLength: number | null;
    contentDisposition: string | null;
    contentRange: string | null;
    status: number;
};

/* ---------------------------------------------------------------------------
   头部处理工具
--------------------------------------------------------------------------- */

/**
 * 组装鉴权与通用请求头
 * @param accept - Accept 头的值
 * @param extra - 额外的请求头
 * @param includeAuth - 是否包含认证头（默认 true）
 */
export function buildProxyHeaders(
    accept: string,
    extra?: Record<string, string>,
    includeAuth: boolean = true
): Record<string, string> {
    void includeAuth;
    const headers: Record<string, string> = {
        ...CONFIG.APP_HEADERS,
        Accept: accept,
        ...(extra ?? {}),
    };

    return headers;
}

/**
 * 获取默认的 Web 请求头部（不包含 Accept）。Web 端认证凭证由 httpOnly cookie 承载。
 */
export function getAuthHeaders(): Record<string, string> {
    return {...CONFIG.APP_HEADERS};
}

/* ---------------------------------------------------------------------------
   响应元数据解析工具
--------------------------------------------------------------------------- */

/**
 * 从 Response 中提取常用的元数据
 */
export function extractResponseMetadata(res: Response): ResponseMetadata {
    return {
        contentType: res.headers.get('Content-Type'),
        contentLength: parseContentLength(res.headers.get('Content-Length')),
        contentDisposition: res.headers.get('Content-Disposition'),
        contentRange: res.headers.get('Content-Range'),
        status: res.status,
    };
}

/**
 * 解析 Content-Length 为 number/null
 */
export function parseContentLength(lenHeader: string | null): number | null {
    if (!lenHeader) return null;
    const n = Number(lenHeader);
    return Number.isFinite(n) && n > 0 ? n : null;
}

/**
 * 从 Content-Range 里解析总长度（bytes start-end/total）
 */
export function parseTotalFromContentRange(cr: string | null): number | null {
    if (!cr) return null;
    const m = cr.match(ProxyConstants.CONTENT_RANGE_PATTERN);
    if (!m) return null;
    const total = m[1];
    return total === '*' ? null : Number(total);
}

/**
 * 解析 RFC6266/8187 的 Content-Disposition，拿到 filename
 * 优先级：filename*=UTF-8''xxx > filename="xxx" > filename=xxx
 */
export function parseFilenameFromDisposition(disposition: string | null | undefined): string | undefined {
    if (!disposition) return undefined;

    // filename*=UTF-8''xxx
    const star = disposition.match(ProxyConstants.FILENAME_STAR_PATTERN)?.[1];
    if (star) {
        const m = star.match(/^[^']*''(.+)$/);
        if (m) {
            try {
                return decodeURIComponent(m[1]);
            } catch {
                // 解码失败，继续尝试其他方式
            }
        }
    }

    // filename="xxx"
    const quoted = disposition.match(ProxyConstants.FILENAME_QUOTED_PATTERN)?.[1];
    if (quoted) return quoted;

    // filename=xxx
    const bare = disposition.match(ProxyConstants.FILENAME_BARE_PATTERN)?.[1];
    return bare?.trim();
}

/* ---------------------------------------------------------------------------
   错误处理工具
--------------------------------------------------------------------------- */

/**
 * 标准化的代理错误类
 */
export class ProxyError extends Error {
    constructor(
        public readonly url: string,
        public readonly status: number,
        public readonly statusText: string,
        public readonly snippet?: string
    ) {
        super(`Proxy download failed: HTTP ${status} ${statusText} | ${url}`);
        this.name = 'ProxyError';
    }
}

/**
 * 从失败的 Response 中提取错误信息并构造 ProxyError
 */
export async function createProxyError(res: Response, url: string, snippetLength: number = ProxyConstants.SNIPPET_LENGTH): Promise<ProxyError> {
    const snippet = await res
        .clone()
        .text()
        .then((t) => t.slice(0, snippetLength))
        .catch(() => '');

    return new ProxyError(url, res.status, res.statusText, snippet || undefined);
}

/**
 * 检查 Response 是否成功，失败则抛出 ProxyError
 */
export async function ensureResponseOk(res: Response, url: string): Promise<void> {
    if (!res.ok) {
        throw await createProxyError(res, url);
    }
}

/* ---------------------------------------------------------------------------
   Fetch 核心逻辑
--------------------------------------------------------------------------- */

/**
 * 内部流式读取实现
 */
async function readStreamWithProgress(
    res: Response,
    contentLength: number | null,
    onProgress: ProgressFn,
    mimeType?: string
): Promise<Blob> {
    if (!res.body) {
        throw new Error('Response body is null');
    }

    const reader = res.body.getReader();
    const parts: BlobPart[] = [];
    let received = 0;

    while (true) {
        const {done, value} = await reader.read();
        if (done) break;
        if (value) {
            // Uint8Array is valid BlobPart, no type assertion needed
            parts.push(value);
            received += value.length;
            const progress = contentLength ? received / contentLength : -1;
            onProgress(progress, received, contentLength ?? -1);
        }
    }

    return new Blob(parts, mimeType ? {type: mimeType} : undefined);
}

/**
 * 统一的代理 fetch 核心函数
 * @param url - 代理 URL（如 /api/proxy?url=xxx）
 * @param options - 代理选项
 * @returns Blob 和响应元数据
 */
export async function fetchViaProxy(
    url: string,
    options: BaseProxyOptions = {}
): Promise<{blob: Blob; metadata: ResponseMetadata}> {
    // 构建请求头
    const accept = options.accept ?? '*/*';
    const headers = buildProxyHeaders(accept, options.headers, true);
    const managedRequest = createManagedRequestContext({
        path: url,
        method: 'GET',
        kind: 'proxy',
        authSensitive: isLikelyAuthSensitiveClientUrl(url),
        signal: options.signal,
        timeoutMs: options.timeoutMs,
        timeoutMessage: 'Proxy request timed out.',
    });

    try {
        const res = await fetch(url, {
            method: 'GET',
            headers,
            credentials: 'include',
            signal: managedRequest.signal,
            redirect: 'follow',
        });

        // 检查响应状态
        await ensureResponseOk(res, url);

        // 提取元数据
        const metadata = extractResponseMetadata(res);

        // 处理进度监听或直接获取 blob
        let blob: Blob;
        if (options.onProgress && res.body) {
            blob = await readStreamWithProgress(res, metadata.contentLength, options.onProgress, metadata.contentType ?? undefined);
        } else {
            blob = await res.blob();
        }

        return {blob, metadata};
    } finally {
        managedRequest.cleanup();
    }
}

/**
 * 创建带超时和取消信号的 AbortController
 * @param timeoutMs - 超时毫秒数
 * @param externalSignal - 外部传入的 AbortSignal
 * @returns AbortController 和清理函数
 */
export function createAbortController(
    timeoutMs?: number,
    externalSignal?: AbortSignal
): {controller: AbortController; cleanup: () => void} {
    const controller = new AbortController();

    // 如果有外部信号，当外部信号中止时也中止本地 controller
    let abortListener: (() => void) | undefined;
    if (externalSignal) {
        abortListener = () => controller.abort();
        externalSignal.addEventListener('abort', abortListener);
    }

    let timeoutId: ReturnType<typeof setTimeout> | undefined;

    if (typeof timeoutMs === 'number' && timeoutMs > 0) {
        timeoutId = setTimeout(() => controller.abort(), timeoutMs);
    }

    const cleanup = () => {
        if (timeoutId) clearTimeout(timeoutId);
        if (abortListener && externalSignal) {
            externalSignal.removeEventListener('abort', abortListener);
        }
    };

    return {controller, cleanup};
}
