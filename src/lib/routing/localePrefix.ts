import {
  defaultLocale,
  isAppLocale,
  type AppLocale,
} from "@/src/lib/i18n";

type LocalePathParts = {
  locale: AppLocale | null;
  pathname: string;
};

function normalizePathname(pathname: string | null | undefined): string {
  if (!pathname) return "/";
  const [pathOnly] = pathname.split(/[?#]/, 1);
  if (!pathOnly || pathOnly === "/") return "/";
  return pathOnly.startsWith("/") ? pathOnly : `/${pathOnly}`;
}

export function splitLocalePrefix(pathname: string | null | undefined): LocalePathParts {
  const normalizedPathname = normalizePathname(pathname);
  const [, firstSegment = "", ...restSegments] = normalizedPathname.split("/");

  if (!isAppLocale(firstSegment)) {
    return {
      locale: null,
      pathname: normalizedPathname,
    };
  }

  const strippedPathname = restSegments.length > 0 ? `/${restSegments.join("/")}` : "/";

  return {
    locale: firstSegment,
    pathname: strippedPathname,
  };
}

export function getLocaleFromPathname(pathname: string | null | undefined): AppLocale | null {
  return splitLocalePrefix(pathname).locale;
}

export function stripLocalePrefix(pathname: string | null | undefined): string {
  return splitLocalePrefix(pathname).pathname;
}

export function withLocalePrefix(pathname: string, locale: AppLocale): string {
  const strippedPathname = stripLocalePrefix(pathname);
  if (locale === defaultLocale) return strippedPathname;
  return strippedPathname === "/" ? `/${locale}` : `/${locale}${strippedPathname}`;
}
