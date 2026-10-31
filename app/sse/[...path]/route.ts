import { SERVER_CONFIG } from '@/src/lib/config/env.server';
import { APP_HEADERS } from '@/src/lib/config/env';
import {
    buildApiProxyRejectionResponse,
    inspectSseProxyRequest,
    isRequestAbort,
    normalizeSseRequestHeaders,
} from '@/src/lib/server/apiProxyRequestPolicy';

export const runtime = 'nodejs';

function pickForwardHeaders(req: Request, cfg: { appHeaders: Record<string, string>; apiHost: string }): HeadersInit {
    const src = req.headers;
    const h = new Headers();

    const copy = (k: string) => {
        const v = src.get(k);
        if (v !== null) h.set(k, v);
    };

    copy('authorization');
    copy('cookie');
    copy('content-type');
    copy('accept');
    copy('origin');
    copy('referer');
    copy('x-app-platform');
    copy('x-app-version');

    normalizeSseRequestHeaders(h);

    const plat = cfg.appHeaders?.['X-App-Platform'];
    const ver = cfg.appHeaders?.['X-App-Version'];
    if (!h.has('x-app-platform') && plat) h.set('x-app-platform', plat);
    if (!h.has('x-app-version') && ver) h.set('x-app-version', ver);

    const host = (cfg.apiHost || '').trim();
    if (host) {
        h.set('host', host);
        h.set('x-forwarded-host', host);
    }
    if (!h.has('x-forwarded-proto')) {
        h.set('x-forwarded-proto', src.get('x-forwarded-proto') || 'https');
    }

    return h;
}

function buildTargetUrl(req: Request, params: { path?: string[] }, apiProxyTarget: string): string {
    const base = (apiProxyTarget || '').replace(/\/$/, '');
    if (!base) {
        throw new Error('API_PROXY_TARGET is not configured');
    }
    const pathParts = params.path?.join('/') ?? '';
    const url = `${base}/api/${pathParts}`;
    const qs = new URL(req.url).search;
    return qs ? `${url}${qs}` : url;
}

async function proxyStream(req: Request, params: { path?: string[] }) {
    try {
        const rejection = await inspectSseProxyRequest(req);
        if (rejection) {
            return buildApiProxyRejectionResponse(rejection);
        }

        const targetUrl = buildTargetUrl(req, params, SERVER_CONFIG.API_PROXY_TARGET);
        const headers = pickForwardHeaders(req, {
            appHeaders: APP_HEADERS,
            apiHost: SERVER_CONFIG.API_HOST,
        });
        const bodyText = await req.text();

        const upstream = await fetch(targetUrl, {
            method: 'POST',
            headers,
            body: bodyText,
            cache: 'no-store',
            signal: req.signal,
        });

        // 非 2xx：把上游的文本错误直接返回，便于定位
        if (!upstream.ok) {
            let text = '';
            try { text = await upstream.text(); } catch {
                // 忽略错误响应体读取失败
            }
            return new Response(text || `Upstream error ${upstream.status}`, {
                status: upstream.status,
                headers: { 'Content-Type': 'text/plain; charset=utf-8' },
            });
        }

        if (!upstream.body) {
            return new Response('Upstream has no body', { status: 502 });
        }

        // 直接转发上游的可读流，避免自定义 reader 造成异常
        const respHeaders = new Headers();
        respHeaders.set('Content-Type', 'text/event-stream; charset=utf-8');
        respHeaders.set('Cache-Control', 'no-store, no-transform');
        respHeaders.set('Connection', 'keep-alive');
        respHeaders.set('X-Accel-Buffering', 'no');

        return new Response(upstream.body, {
            status: upstream.status,
            headers: respHeaders,
        });
    } catch (e) {
        if (isRequestAbort(e, req.signal)) {
            return new Response(null, {
                status: 499,
                headers: {
                    'Cache-Control': 'no-store',
                    'X-Deckwise-Proxy-Error': 'CLIENT_CLOSED_REQUEST',
                },
            });
        }

        const msg = e instanceof Error ? e.message : String(e);
        return new Response(`Proxy error: ${msg}`, { status: 502, headers: { 'Content-Type': 'text/plain; charset=utf-8' } });
    }
}

export async function POST(
    req: Request,
    ctx: { params: Promise<{ path?: string[] }> }
) {
    const { path } = await ctx.params;
    return proxyStream(req, { path });
}

