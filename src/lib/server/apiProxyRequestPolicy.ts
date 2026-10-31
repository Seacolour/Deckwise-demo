import { WEB_SESSION_COOKIE_NAME } from '@/src/lib/auth/webSessionCookie';

const JSON_ACCEPT = 'application/json';
const SSE_ACCEPT = 'text/event-stream, application/json';

const KNOWN_JSON_RESPONSE_PATHS = new Set([
  'models/available',
  'user/login',
  'user/login/email-code',
]);

const KNOWN_JSON_BODY_PATHS = new Set([
  'user/login',
  'user/login/email-code',
]);

const KNOWN_PROTECTED_PATHS = new Set([
  'models/available',
]);

export type ApiProxyRequestRejection = {
  code: number | string;
  errorCode: string;
  message: string;
  status: number;
};

export function normalizeKnownApiRequestHeaders(pathParts: string[], headers: Headers): void {
  const path = pathParts.join('/');
  if (!KNOWN_JSON_RESPONSE_PATHS.has(path)) {
    return;
  }

  headers.set(
    'accept',
    normalizeAcceptHeader(headers.get('accept'), JSON_ACCEPT, [JSON_ACCEPT]),
  );
  if (KNOWN_JSON_BODY_PATHS.has(path)) {
    headers.set('content-type', JSON_ACCEPT);
  }
}

export function normalizeSseRequestHeaders(headers: Headers): void {
  headers.set('accept', normalizeAcceptHeader(headers.get('accept'), SSE_ACCEPT, [
    'text/event-stream',
    JSON_ACCEPT,
  ]));
  headers.set('content-type', JSON_ACCEPT);
}

export async function inspectKnownApiProxyRequest(
  request: Request,
  pathParts: string[],
): Promise<ApiProxyRequestRejection | null> {
  const path = pathParts.join('/');

  if (KNOWN_PROTECTED_PATHS.has(path) && !hasWebSessionCredential(request.headers)) {
    return {
      code: 40102,
      errorCode: 'AUTH_REQUIRED',
      message: 'Authentication required.',
      status: 401,
    };
  }

  if (!KNOWN_JSON_BODY_PATHS.has(path)) {
    return null;
  }

  if (!await readJsonRecord(request)) {
    return invalidRequest('INVALID_JSON_BODY', 'A valid JSON request body is required.', 400);
  }
  return null;
}

export async function inspectSseProxyRequest(
  request: Request,
): Promise<ApiProxyRequestRejection | null> {
  if (!hasWebSessionCredential(request.headers)) {
    return {
      code: 40102,
      errorCode: 'AUTH_REQUIRED',
      message: 'Authentication required.',
      status: 401,
    };
  }

  if (!await readJsonRecord(request)) {
    return invalidRequest('INVALID_JSON_BODY', 'A valid JSON request body is required.', 400);
  }

  return null;
}

export function buildApiProxyRejectionResponse(rejection: ApiProxyRequestRejection): Response {
  return Response.json(
    {
      code: rejection.code,
      errorCode: rejection.errorCode,
      message: rejection.message,
    },
    {
      status: rejection.status,
      headers: {
        'cache-control': 'no-store',
        'x-deckwise-proxy-filter': rejection.errorCode,
      },
    },
  );
}

export function isRequestAbort(error: unknown, signal?: AbortSignal): boolean {
  if (signal?.aborted) {
    return true;
  }

  return error instanceof Error && error.name === 'AbortError';
}

function normalizeAcceptHeader(
  value: string | null,
  fallback: string,
  requiredMediaTypes: string[] = [],
): string {
  const concreteMediaTypes = (value ?? '')
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .filter((entry) => !isWildcardMediaRange(entry));

  for (const requiredMediaType of requiredMediaTypes) {
    if (!concreteMediaTypes.some((entry) => mediaTypeOf(entry) === requiredMediaType)) {
      concreteMediaTypes.push(requiredMediaType);
    }
  }

  return concreteMediaTypes.length > 0 ? concreteMediaTypes.join(', ') : fallback;
}

function isWildcardMediaRange(value: string): boolean {
  const mediaType = mediaTypeOf(value);
  return mediaType === '*/*' || mediaType.endsWith('/*');
}

function mediaTypeOf(value: string): string {
  return (value.split(';', 1)[0] ?? '').trim().toLowerCase();
}

function hasWebSessionCredential(headers: Headers): boolean {
  if (headers.get('authorization')?.trim()) {
    return true;
  }

  const cookieHeader = headers.get('cookie');
  if (!cookieHeader) {
    return false;
  }

  return cookieHeader.split(';').some((cookie) => {
    const [rawName, ...rawValueParts] = cookie.trim().split('=');
    return rawName === WEB_SESSION_COOKIE_NAME && rawValueParts.join('=').trim().length > 0;
  });
}

async function readJsonRecord(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const value: unknown = await request.clone().json();
    return isRecord(value) ? value : null;
  } catch {
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function invalidRequest(
  errorCode: string,
  message: string,
  status: number,
): ApiProxyRequestRejection {
  return {
    code: errorCode,
    errorCode,
    message,
    status,
  };
}
