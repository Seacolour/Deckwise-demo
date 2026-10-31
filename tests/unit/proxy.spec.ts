import type { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { proxy } from "@/proxy";
import { LOCALE_COOKIE } from "@/src/lib/i18n";
import { WEB_SESSION_COOKIE_NAME } from "@/src/lib/auth/webSessionCookie";

const ORIGIN = "https://example.test";
const REQUEST_LOCALE_HEADER = "x-deckwise-locale";

function request(path: string, cookies: Record<string, string> = {}, headers: Record<string, string> = {}): NextRequest {
  const url = new URL(path, ORIGIN);
  return {
    cookies: { get: (name: string) => cookies[name] ? { name, value: cookies[name] } : undefined },
    headers: new Headers(headers),
    nextUrl: url,
    url: url.toString(),
  } as unknown as NextRequest;
}

function localeHeader(response: Response): string | null {
  return response.headers.get(`x-middleware-request-${REQUEST_LOCALE_HEADER}`);
}

describe("web-only proxy", () => {
  it("sends signed-out root visitors to login", () => {
    const response = proxy(request("/"));
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(`${ORIGIN}/login`);
  });

  it("sends signed-in root visitors to the app", () => {
    const response = proxy(request("/", { [WEB_SESSION_COOKIE_NAME]: "session" }));
    expect(response.headers.get("location")).toBe(`${ORIGIN}/app`);
  });

  it("rewrites localized app paths and persists the language", () => {
    const response = proxy(request("/es/app?project=1"));
    expect(response.headers.get("x-middleware-rewrite")).toBe(`${ORIGIN}/app?project=1`);
    expect(localeHeader(response)).toBe("es");
    expect(response.headers.get("set-cookie")).toContain(`${LOCALE_COOKIE}=es`);
  });

  it("honors the saved language on unprefixed app routes", () => {
    const response = proxy(request("/app", { [LOCALE_COOKIE]: "zh" }, { "accept-language": "ja-JP" }));
    expect(localeHeader(response)).toBe("zh");
    expect(response.headers.get("set-cookie")).toBeNull();
  });

  it("does not inject UI locale headers or cookies into API requests", () => {
    const response = proxy(request("/ja/api/user/profile"));
    expect(response.headers.get("x-middleware-rewrite")).toBe(`${ORIGIN}/api/user/profile`);
    expect(localeHeader(response)).toBeNull();
    expect(response.headers.get("set-cookie")).toBeNull();
  });
});
