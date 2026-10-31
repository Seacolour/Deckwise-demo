export const appLocales = ["en", "vi", "zh", "zh-Hant", "ja", "ko", "es", "pt-BR"] as const;
export type AppLocale = (typeof appLocales)[number];
export type AppLocaleOption = {
  label: string;
  value: AppLocale;
};

export const defaultLocale: AppLocale = "en";
export const LOCALE_COOKIE = "NEXT_LOCALE";
export const LOCALE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;
const appLocaleSet = new Set<string>(appLocales);

const htmlLangByLocale: Record<AppLocale, string> = {
  en: "en",
  vi: "vi",
  zh: "zh-CN",
  "zh-Hant": "zh-Hant",
  ja: "ja",
  ko: "ko",
  es: "es",
  "pt-BR": "pt-BR",
};

export const appLocaleOptions: readonly AppLocaleOption[] = [
  { value: "en", label: "English" },
  { value: "vi", label: "Tiếng Việt" },
  { value: "zh", label: "简体中文" },
  { value: "zh-Hant", label: "繁體中文" },
  { value: "ja", label: "日本語" },
  { value: "ko", label: "한국어" },
  { value: "es", label: "Español" },
  { value: "pt-BR", label: "Português" },
];

export function isAppLocale(value: string | null | undefined): value is AppLocale {
  return Boolean(value && appLocaleSet.has(value));
}

export function resolveHtmlLang(locale: AppLocale): string {
  return htmlLangByLocale[locale];
}

export function persistLocalePreference(locale: AppLocale) {
  if (typeof document === "undefined") {
    return;
  }

  document.cookie = [
    `${LOCALE_COOKIE}=${locale}`,
    "Path=/",
    `Max-Age=${LOCALE_COOKIE_MAX_AGE_SECONDS}`,
    "SameSite=Lax",
  ].join("; ");
}
