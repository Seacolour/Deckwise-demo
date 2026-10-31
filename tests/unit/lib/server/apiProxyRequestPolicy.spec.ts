import { describe, expect, it } from 'vitest';
import {
  inspectKnownApiProxyRequest,
  inspectSseProxyRequest,
  normalizeKnownApiRequestHeaders,
  normalizeSseRequestHeaders,
} from '@/src/lib/server/apiProxyRequestPolicy';

function jsonRequest(
  path: string,
  body: unknown,
  headers: HeadersInit = {},
): Request {
  return new Request(`https://deckwise.test/api/${path}`, {
    method: 'POST',
    body: JSON.stringify(body),
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
  });
}

describe('apiProxyRequestPolicy', () => {
  it('replaces wildcard negotiation headers on known JSON endpoints', () => {
    const headers = new Headers({
      Accept: '*/*',
      'Content-Type': '*/*',
    });

    normalizeKnownApiRequestHeaders(['user', 'login', 'email-code'], headers);

    expect(headers.get('accept')).toBe('application/json');
    expect(headers.get('content-type')).toBe('application/json');
  });

  it('keeps unrelated binary API negotiation untouched', () => {
    const headers = new Headers({ Accept: 'image/*' });

    normalizeKnownApiRequestHeaders(['files', 'download'], headers);

    expect(headers.get('accept')).toBe('image/*');
  });

  it('adds JSON error fallback to SSE requests and removes wildcard ranges', () => {
    const headers = new Headers({ Accept: 'text/event-stream, */*' });

    normalizeSseRequestHeaders(headers);

    expect(headers.get('accept')).toBe('text/event-stream, application/json');
    expect(headers.get('content-type')).toBe('application/json');
  });

  it('blocks known protected requests before they reach the backend without a session', async () => {
    const request = jsonRequest('models/available', {});

    await expect(inspectKnownApiProxyRequest(
      request,
      ['models', 'available'],
    )).resolves.toMatchObject({
      errorCode: 'AUTH_REQUIRED',
      status: 401,
    });
  });

  it('accepts protected requests with the web session cookie', async () => {
    const request = jsonRequest('models/available', {}, {
      Cookie: 'analytics_id=1; deckwise_session=session-token',
    });

    await expect(inspectKnownApiProxyRequest(
      request,
      ['models', 'available'],
    )).resolves.toBeNull();
  });

  it('blocks malformed email login JSON', async () => {
    const request = new Request('https://deckwise.test/api/user/login', {
      method: 'POST',
      body: 'not-json',
      headers: { 'Content-Type': 'application/json' },
    });

    await expect(inspectKnownApiProxyRequest(
      request,
      ['user', 'login'],
    )).resolves.toMatchObject({
      errorCode: 'INVALID_JSON_BODY',
      status: 400,
    });
  });

  it('does not special-case retired slide generation endpoints', async () => {
    const request = new Request('https://deckwise.test/api/slides/generation/deck', {
      method: 'POST',
      body: '--incomplete-boundary',
      headers: {
        'Content-Type': 'multipart/form-data; boundary=incomplete-boundary',
      },
    });

    await expect(inspectKnownApiProxyRequest(
      request,
      ['slides', 'generation', 'deck'],
    )).resolves.toBeNull();
  });

  it('blocks unauthenticated or malformed SSE requests locally', async () => {
    const unauthenticated = jsonRequest('chat/stream', {});
    await expect(inspectSseProxyRequest(unauthenticated)).resolves.toMatchObject({
      errorCode: 'AUTH_REQUIRED',
      status: 401,
    });

    const malformed = new Request('https://deckwise.test/sse/chat/stream', {
      method: 'POST',
      body: 'not-json',
      headers: { Cookie: 'deckwise_session=session-token' },
    });
    await expect(inspectSseProxyRequest(malformed)).resolves.toMatchObject({
      errorCode: 'INVALID_JSON_BODY',
      status: 400,
    });
  });
});
