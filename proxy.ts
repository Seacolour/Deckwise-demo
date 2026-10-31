import { NextResponse, type NextRequest } from "next/server";
import {
  defaultLocale,
  isAppLocale,
  LOCALE_COOKIE,
  LOCALE_COOKIE_MAX_AGE_SECONDS,
  type AppLocale,
} from "@/src/lib/i18n";
import { resolveLocaleFromAcceptLanguageHeader } from "@/src/lib/i18n/acceptLanguage";
import { WEB_SESSION_COOKIE_NAME } from "@/src/lib/auth/webSessionCookie";
import { splitLocalePrefix } from "@/src/lib/routing/localePrefix";
import { HOME_PATH } from "@/src/lib/routing/projectRoutes";

const REQUEST_LOCALE_HEADER = "x-deckwise-locale";

function isBackendProxyRoute(pathname: string): boolean {
  return pathname === "/api" || pathname.startsWith("/api/") ||
    pathname === "/sse" || pathname.startsWith("/sse/");
}

function persistLocaleCookie(response: NextResponse, locale: AppLocale): void {
  response.cookies.set(LOCALE_COOKIE, locale, {
    maxAge: LOCALE_COOKIE_MAX_AGE_SECONDS,
    path: "/",
    sameSite: "lax",
  });
}

export function proxy(request: NextRequest) {
  const { locale: pathLocale, pathname } = splitLocalePrefix(request.nextUrl.pathname);
  const cookieValue = request.cookies.get(LOCALE_COOKIE)?.value;
  const cookieLocale = isAppLocale(cookieValue) ? cookieValue : null;
  const browserLocale = resolveLocaleFromAcceptLanguageHeader(request.headers.get("accept-language"));
  const locale = pathLocale ?? cookieLocale ?? browserLocale ?? defaultLocale;
  const backendProxyRoute = isBackendProxyRoute(pathname);

  if (pathname === "/" && !backendProxyRoute) {
    const hasSession = Boolean(request.cookies.get(WEB_SESSION_COOKIE_NAME)?.value);
    const response = NextResponse.redirect(new URL(hasSession ? HOME_PATH : "/login", request.url));
    if (pathLocale) persistLocaleCookie(response, pathLocale);
    return response;
  }

  const headers = new Headers(request.headers);
  if (!backendProxyRoute) headers.set(REQUEST_LOCALE_HEADER, locale);
  const response = pathLocale
    ? NextResponse.rewrite(new URL(`${pathname}${request.nextUrl.search}`, request.url), {
        request: { headers },
      })
    : NextResponse.next({ request: { headers } });
  if (pathLocale && !backendProxyRoute) persistLocaleCookie(response, pathLocale);
  return response;
}

export const config = {
  matcher: [
    "/((?!api/health|_next/static|_next/image|favicon.ico|icon.png|apple-icon.png|robots.txt|sitemap.xml).*)",
  ],
};
