import type { UserMessageSendRequest } from '@/src/lib/types';
import { CONFIG } from '@/src/lib/config/env';
import { debugFlagLog } from '@/src/lib/debug';
import { isDebugEnabled } from '@/src/lib/storage/localStorageManager';
import {
  SseConstants,
  HttpHeaders,
  ApiPaths,
} from '@/src/lib/config/constants/api';
import { createManagedRequestContext } from '@/src/lib/api/requestRuntime';
import { safeCleanup } from '@/src/lib/utils/errorHandler';

export type StreamCallbacks = {
    onMessage: (data: string) => void;
    onError?: (err: Error) => void;
    onDone?: () => void;
};

export type SseStreamErrorKind = 'streamError';

export class SseStreamError extends Error {
    readonly kind: SseStreamErrorKind;
    readonly eventName: string;
    readonly rawData: string;

    constructor(message: string, opts: {
        kind?: SseStreamErrorKind;
        eventName?: string;
        rawData?: string;
    } = {}) {
        super(message || 'SSE stream error');
        this.name = 'SseStreamError';
        this.kind = opts.kind ?? 'streamError';
        this.eventName = opts.eventName ?? 'error';
        this.rawData = opts.rawData ?? message;
    }
}

type PostSseOptions = {
    params?: Record<string, string | number | boolean | null | undefined>;
    body?: BodyInit | null | undefined;
    onMessage: (data: string) => void;
    onError?: (err: Error) => void;
    onDone?: () => void;
    doneToken?: string;
    signal?: AbortSignal;
};

function stripOneLeadingSpace(s: string) {
    return s.startsWith(' ') ? s.slice(1) : s;
}

export function isSseStreamError(error: unknown): error is SseStreamError {
    return error instanceof SseStreamError ||
        Boolean(error && typeof error === 'object' && (error as { name?: unknown }).name === 'SseStreamError');
}

function createSseStreamError(eventName: string, data: string): SseStreamError {
    const message = extractSseStreamErrorMessage(data) || 'SSE stream error';
    return new SseStreamError(message, {
        kind: 'streamError',
        eventName,
        rawData: data,
    });
}

function extractSseStreamErrorMessage(data: string): string | null {
    const trimmed = data.trim();
    if (!trimmed) {
        return null;
    }

    try {
        return extractStructuredErrorMessage(JSON.parse(trimmed)) ?? trimmed;
    } catch {
        return trimmed;
    }
}

function extractStructuredErrorMessage(value: unknown, depth = 0): string | null {
    if (depth > 3) {
        return null;
    }
    if (typeof value === 'string') {
        return value.trim() || null;
    }
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return null;
    }

    const record = value as Record<string, unknown>;
    for (const key of ['payload', 'message', 'msg', 'detail', 'error', 'data'] as const) {
        const message = extractStructuredErrorMessage(record[key], depth + 1);
        if (message) {
            return message;
        }
    }
    return null;
}

function dbg(...args: unknown[]) {
    debugFlagLog(isDebugEnabled.stream(), '[stream]', ...args);
}

function summarizeBody(body: BodyInit | null | undefined): unknown {
    if (!body) return null;
    if (typeof body === 'string') {
        const head = body.slice(0, 500);
        try {
            const obj = JSON.parse(body);
            if (obj && typeof obj === 'object') {
                return { type: 'json', keys: Object.keys(obj as Record<string, unknown>), head };
            }
        } catch {
        }
        return { type: 'string', len: body.length, head };
    }
    return { type: typeof body };
}

async function readSseStream(
    res: Response,
    cb: Pick<StreamCallbacks, 'onMessage' | 'onDone'>,
    doneToken: string,
) {
    const { onMessage, onDone } = cb;
    const reader = res.body!.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let currentEvent = 'message';
    const contentType = res.headers.get('content-type') || '';
    const isSse = contentType.includes('text/event-stream');
    dbg('connected', { status: res.status, contentType, isSse });

    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            if (!isSse && buffer.indexOf('\n') === -1 && buffer.length > SseConstants.DEFAULT_BUFFER_SIZE) {
                const chunk = buffer;
                buffer = '';
                dbg('flush.no_newline', { len: chunk.length, head: chunk.slice(0, SseConstants.DATA_SNIPPET_LENGTH) });
                onMessage(chunk);
                continue;
            }

            while (true) {
                const nl = buffer.indexOf('\n');
                if (nl === -1) break;
                let line = buffer.slice(0, nl);
                buffer = buffer.slice(nl + 1);
                if (line.endsWith('\r')) line = line.slice(0, -1);

                if (!line) {
                    if (currentEvent === 'error') {
                        throw createSseStreamError(currentEvent, '');
                    }
                    if (currentEvent === 'done' || currentEvent === 'abort') {
                        dbg('done.by_event_blankline');
                        onDone?.();
                        await safeCleanup(() => reader.cancel(), 'reader.cancel');
                        return;
                    }
                    currentEvent = 'message';
                    continue;
                }
                if (line.startsWith(':')) continue;

                if (line.startsWith('event:')) {
                    currentEvent = stripOneLeadingSpace(line.slice(SseConstants.FIELD_EVENT.length));
                    dbg('event', currentEvent);
                    if (currentEvent === 'done' && !isDebugEnabled.chatLegacyStreamDone()) {
                        dbg('done.by_event_line');
                        onDone?.();
                        await safeCleanup(() => reader.cancel(), 'reader.cancel');
                        return;
                    }
                    continue;
                }

                if (line.startsWith(SseConstants.FIELD_ID) || line.startsWith(SseConstants.FIELD_RETRY)) {
                    dbg('meta.ignore', line.slice(0, 40));
                    continue;
                }

                if (line.startsWith('data:')) {
                    const data = stripOneLeadingSpace(line.slice(SseConstants.FIELD_DATA.length));
                    if (isDebugEnabled.stream()) {
                        dbg('data', { len: data.length, head: data.slice(0, SseConstants.DATA_SNIPPET_LENGTH) });
                    }
                    if (currentEvent === 'error') {
                        dbg('error.by_event_data', { data: data.slice(0, SseConstants.DATA_SNIPPET_LENGTH) });
                        const err = createSseStreamError(currentEvent, data);
                        await safeCleanup(() => reader.cancel(), 'reader.cancel');
                        throw err;
                    }
                    if (!data && currentEvent !== 'done' && currentEvent !== 'abort') continue;
                    if (data === doneToken || currentEvent === 'done' || currentEvent === 'abort') {
                        dbg('done.by_token_or_event', { currentEvent, doneToken });
                        onDone?.();
                        await safeCleanup(() => reader.cancel(), 'reader.cancel');
                        return;
                    }
                    if (data) onMessage(data);
                    continue;
                }

                if (isSse) {
                    dbg('sse.unknown_ignored', { head: line.slice(0, 80) });
                    continue;
                }

                if (line.trim()) {
                    dbg('line.fallback', { len: line.length, head: line.slice(0, SseConstants.DATA_SNIPPET_LENGTH) });
                    onMessage(line);
                }
            }
        }

        const tail = buffer.trim();
        if (tail) {
            if (tail.startsWith('data:')) {
                const data = stripOneLeadingSpace(tail.slice(SseConstants.FIELD_DATA.length));
                if (currentEvent === 'error') throw createSseStreamError(currentEvent, data);
                if (data === doneToken || currentEvent === 'done') onDone?.();
                else if (data) onMessage(data);
            } else if (currentEvent === 'error') {
                throw createSseStreamError(currentEvent, tail);
            } else if (tail === doneToken || currentEvent === 'done' || currentEvent === 'abort') {
                onDone?.();
            } else {
                onMessage(tail);
            }
        }
        dbg('done.eof');
        onDone?.();
    } catch (e) {
        dbg('read.error', e);
        throw e instanceof Error ? e : new Error(String(e));
    }
}

async function postSse(path: string, opts: PostSseOptions): Promise<void> {
    const {
        params,
        body,
        onMessage,
        onError,
        onDone,
        doneToken = SseConstants.DONE_TOKEN,
        signal,
    } = opts;

    const pathOnly = path.startsWith('/') ? path.slice(1) : path;
    let url = `/sse/${pathOnly}`;
    if (params && Object.keys(params).length) {
        const qs = new URLSearchParams();
        for (const [k, v] of Object.entries(params)) {
            if (v !== undefined && v !== null) qs.append(k, String(v));
        }
        url += (url.includes('?') ? '&' : '?') + qs.toString();
    }

    const headers: Record<string, string> = {
        ...CONFIG.APP_HEADERS,
        Accept: HttpHeaders.ACCEPT_STREAM,
        'Content-Type': HttpHeaders.CONTENT_TYPE_JSON,
    };
    const managedRequest = createManagedRequestContext({
        path: url,
        method: 'POST',
        kind: 'stream',
        authSensitive: true,
        signal,
    });

    try {
        dbg('POST', url, { params, body: summarizeBody(body) });
        // Keep this hand-written: chat RPC streams are proxied through `/sse/*`
        // and must parse incremental event/data frames instead of JSON payloads.
        const res = await fetch(url, {
            method: 'POST',
            headers,
            body,
            cache: 'no-store',
            credentials: 'include',
            signal: managedRequest.signal,
        });

        if (!res.ok) {
            let msg = `HTTP ${res.status}`;
            let text = '';
            try {
                text = await res.text();
                if (text) msg += ` - ${text}`;
            } catch {
                // ignore response text read failure
            }
            dbg('http.not_ok', msg);
            const err = new Error(`SSE request failed: ${msg}`);
            throw err;
        }

        if (!res.body) {
            dbg('no_body');
            const err = new Error('SSE response has no body. It may have been intercepted by a proxy/runtime or blocked by CORS.');
            throw err;
        }

        await readSseStream(res, { onMessage, onDone }, doneToken);
    } catch (e) {
        dbg('fetch.error', e);
        const err = e instanceof Error ? e : new Error(String(e));
        onError?.(err);
        throw err;
    } finally {
        managedRequest.cleanup();
    }
}

export async function streamSendMessageRPC(
    sessionId: string | number,
    payload: UserMessageSendRequest,
    cb: StreamCallbacks,
    signal?: AbortSignal
): Promise<void> {
    dbg('streamSendMessageRPC', sessionId, payload);
    const path = ApiPaths.SESSION_SEND_RPC(sessionId);
    return postSse(path, {
        body: JSON.stringify(payload),
        onMessage: cb.onMessage,
        onError: cb.onError,
        onDone: cb.onDone,
        signal,
    });
}
