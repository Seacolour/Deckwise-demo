// lib/server/proxy/core.ts
import { serverDebug } from "@/src/lib/server/log";

/**
 * 服务端代理核心模块
 * 仅供 Route Handlers 使用；不要在客户端代码中 import
 */

/* ---------------------------------------------------------------------------
   常量定义
--------------------------------------------------------------------------- */

/** 默认允许透传的响应头 */
export const DEFAULT_FORWARD_HEADERS = [
    'content-type',
    'content-length',
    'content-disposition',
    'accept-ranges',
    'content-range',
    'last-modified',
    'etag',
    'cache-control',
    'expires',
] as const;

/* ---------------------------------------------------------------------------
   类型定义
--------------------------------------------------------------------------- */

export type ProxyDownloadOptions = {
    /** 允许透传的响应头（默认使用 DEFAULT_FORWARD_HEADERS） */
    forwardHeaders?: readonly string[];
    /** 额外写入到返回给客户端的响应头 */
    extraResponseHeaders?: Record<string, string>;
    /** 是否强制 no-store（默认 true） */
    noStore?: boolean;
    /** 在 next/server 路由中用于调试的 label */
    debugLabel?: string;
    /** 打印日志（默认仅非生产） */
    log?: boolean;
};

/* ---------------------------------------------------------------------------
   服务端代理实现
--------------------------------------------------------------------------- */

/**
 * 服务端代理下载核心函数
 * 将上游请求转发给客户端，处理错误并透传必要的响应头
 * @param upstreamUrl - 上游资源 URL
 * @param init - RequestInit，用于传递给上游 fetch
 * @param opts - 代理选项
 * @returns Response 对象
 */
export async function proxyDownload(
    upstreamUrl: string,
    init: RequestInit,
    opts: ProxyDownloadOptions = {}
): Promise<Response> {
    const {
        forwardHeaders = DEFAULT_FORWARD_HEADERS,
        extraResponseHeaders,
        noStore = true,
        debugLabel,
        log = process.env.NODE_ENV !== 'production',
    } = opts;

    serverDebug(log, `→ proxyDownload${debugLabel ? `(${debugLabel})` : ''}: ${upstreamUrl}`);

    let upstreamRes: Response;
    try {
        upstreamRes = await fetch(upstreamUrl, init);
    } catch (e) {
        if (log) console.error('proxyDownload fetch failed:', e);
        return new Response('Upstream fetch failed', {status: 502});
    }

    // 构建响应头
    const headers = new Headers();

    // 透传指定的响应头
    for (const key of forwardHeaders) {
        const val = upstreamRes.headers.get(key);
        if (val) headers.set(key, val);
    }

    // 强制 no-store
    if (noStore) {
        headers.set('cache-control', 'no-store');
    }

    // 添加额外的响应头（仅在上游缺失时才写入，避免覆盖真实值）
    if (extraResponseHeaders) {
        for (const [k, v] of Object.entries(extraResponseHeaders)) {
            if (v && !headers.has(k)) {
                headers.set(k, v);
            }
        }
    }

    return new Response(upstreamRes.body, {
        status: upstreamRes.status,
        statusText: upstreamRes.statusText,
        headers,
    });
}

/**
 * 创建流式代理响应
 * 用于需要实时转发上游流的场景
 * @param upstreamUrl - 上游资源 URL
 * @param init - RequestInit
 * @param opts - 代理选项
 * @returns Response 对象
 */
export async function proxyStream(
    upstreamUrl: string,
    init: RequestInit,
    opts: ProxyDownloadOptions = {}
): Promise<Response> {
    return proxyDownload(upstreamUrl, init, opts);
}
