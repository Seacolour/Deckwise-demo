// app/api/proxy/route.ts
import { NextRequest, NextResponse } from 'next/server';
import { proxyDownload } from '@/src/lib/server/proxyDownload';
import {
    buildProxyForwardHeaders,
    resolveAllowedProxyHosts,
    validateProxyTargetUrl,
} from '@/src/lib/server/proxy/requestPolicy';
import { serverDebug } from '@/src/lib/server/log';

// 让它运行在 Node 环境（而不是 Edge）
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

// 可选：用于调试
const LOG = process.env.NODE_ENV !== 'production';

function jsonError(message: string, status = 400) {
    return new NextResponse(JSON.stringify({ error: message }), {
        status,
        headers: { 'content-type': 'application/json; charset=utf-8' },
    });
}

// 仅支持 GET 下载
export async function GET(req: NextRequest) {
    const allowedHosts = resolveAllowedProxyHosts();

    const { searchParams } = new URL(req.url);
    const target = searchParams.get('url');
    if (!target) return jsonError('Missing ?url parameter', 422);

    // ⚠️ SSRF 防护：验证目标 URL
    const validation = validateProxyTargetUrl(target, allowedHosts);
    if (!validation.valid) {
        console.warn('[Proxy] Blocked request:', { url: target, error: validation.error });
        return jsonError(validation.error || 'Invalid URL', 403);
    }

    // 记录所有代理请求用于审计（生产环境建议使用专门的日志服务）
    serverDebug(LOG, '[Proxy] Request:', { url: target, userAgent: req.headers.get('user-agent') });

    const forwardHeaders = buildProxyForwardHeaders({
        requestHeaders: req.headers,
    });

    const res = await proxyDownload(
        target,
        {
            method: 'GET',
            headers: forwardHeaders,
            redirect: 'follow',
        },
        { debugLabel: 'proxy', log: LOG }
    );

    // NextResponse 以保证在 Next Route Handler 内表现一致（但 body/headers 直接复用）
    return new NextResponse(res.body, { status: res.status, headers: res.headers });
}
