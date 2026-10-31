import { cookies, headers } from "next/headers";
import { getRequestConfig } from "next-intl/server";
import {
  defaultLocale,
  isAppLocale,
  LOCALE_COOKIE,
  type AppLocale,
} from "@/src/lib/i18n";
import { resolveLocaleFromAcceptLanguageHeader } from "@/src/lib/i18n/acceptLanguage";
const REQUEST_LOCALE_HEADER = "x-deckwise-locale";

export { appLocales as locales } from "@/src/lib/i18n";
export type { AppLocale } from "@/src/lib/i18n";

function isI18nDebugEnabled(): boolean {
  return (
    process.env.NODE_ENV !== "production" &&
    (process.env.DECKWISE_I18N_DEBUG === "1" || process.env.NEXT_PUBLIC_DEBUG === "true")
  );
}

async function resolveLocale(explicitLocale?: string): Promise<AppLocale> {
  if (isAppLocale(explicitLocale)) return explicitLocale;

  const [cookieStore, headerStore] = await Promise.all([cookies(), headers()]);
  const requestLocale = headerStore.get(REQUEST_LOCALE_HEADER);
  const acceptLanguage = headerStore.get("accept-language");
  const browserLocale = resolveLocaleFromAcceptLanguageHeader(acceptLanguage);
  const c = cookieStore.get(LOCALE_COOKIE)?.value;
  if (isI18nDebugEnabled()) {
    // Debug: help diagnose why UI locale doesn't match expectation.
    console.log("[i18n] resolveLocale()", { browserLocale, cookie_NEXT_LOCALE: c, defaultLocale, requestLocale });
  }

  if (isAppLocale(requestLocale)) return requestLocale;
  if (isAppLocale(c)) return c;
  if (browserLocale) return browserLocale;

  return defaultLocale;
}

export default getRequestConfig(async ({ locale: localeOverride, requestLocale }) => {
  const routedLocale = isAppLocale(localeOverride)
    ? localeOverride
    : await requestLocale;
  const locale = await resolveLocale(routedLocale);
  const messages = (await import(`../messages/${locale}.json`)).default;

  if (isI18nDebugEnabled()) {
    console.log("[i18n] getRequestConfig()", { locale });
  }

  return { locale, messages };
});
