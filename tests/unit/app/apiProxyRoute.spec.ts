import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/src/lib/config/env.server', () => ({
  SERVER_CONFIG: {
    API_HOST: 'api.deckwise.test',
    API_PROXY_TARGET: 'https://api.deckwise.test',
    UPLOAD_PROXY_TARGET: 'https://upload.deckwise.test',
  },
}));

vi.mock('@/src/lib/config/env', () => ({
  APP_HEADERS: {
    'X-App-Platform': 'web',
    'X-App-Version': '2025',
  },
}));

vi.mock('@/src/lib/server/log', () => ({
  serverInfo: vi.fn(),
}));

import { POST as apiPost } from '@/app/api/[...path]/route';
import { POST as ssePost } from '@/app/sse/[...path]/route';

const sessionCookie = 'deckwise_session=session-token';

describe('API proxy route request hardening', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ code: 0, data: {} })));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('normalizes known JSON requests and propagates client cancellation upstream', async () => {
    const request = new Request('https://deckwise.test/api/user/login/email-code', {
      method: 'POST',
      body: JSON.stringify({ email: 'me@example.com' }),
      headers: {
        Accept: '*/*',
        Cookie: sessionCookie,
        'Content-Type': '*/*',
        'X-API-Contract': 'browser-override',
      },
    });

    const response = await apiPost(request, {
      params: Promise.resolve({ path: ['user', 'login', 'email-code'] }),
    });

    expect(response.status).toBe(200);
    expect(fetch).toHaveBeenCalledTimes(1);
    const [, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get('accept')).toBe('application/json');
    expect(headers.get('content-type')).toBe('application/json');
    expect(headers.get('x-api-contract')).toBeNull();
    expect(init?.signal).toBe(request.signal);
  });

  it('adds JSON fallback to SSE negotiation and propagates client cancellation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('event: done\ndata: {}\n\n', {
      headers: { 'Content-Type': 'text/event-stream' },
    })));
    const request = new Request('https://deckwise.test/sse/chat/stream', {
      method: 'POST',
      body: JSON.stringify({ topic: 'Launch' }),
      headers: {
        Accept: 'text/event-stream',
        Cookie: sessionCookie,
        'Content-Type': 'application/json',
        'X-API-Contract': 'browser-override',
      },
    });

    const response = await ssePost(request, {
      params: Promise.resolve({ path: ['chat', 'stream'] }),
    });

    expect(response.status).toBe(200);
    const [, init] = vi.mocked(fetch).mock.calls[0] ?? [];
    const headers = new Headers(init?.headers);
    expect(headers.get('accept')).toBe('text/event-stream, application/json');
    expect(headers.get('content-type')).toBe('application/json');
    expect(headers.get('x-api-contract')).toBeNull();
    expect(init?.signal).toBe(request.signal);
  });

});
