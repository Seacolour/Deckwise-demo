import {
  appLocales,
  type AppLocale,
} from "@/src/lib/i18n";

const appLocaleByLowercaseTag = new Map<string, AppLocale>(
  appLocales.map((locale) => [locale.toLowerCase(), locale]),
);

type AcceptedLanguage = {
  index: number;
  q: number;
  tag: string;
};

function parseQuality(value: string | undefined): number {
  if (!value) return 1;

  const quality = Number(value);
  if (!Number.isFinite(quality)) return 0;

  return Math.max(0, Math.min(1, quality));
}

function parseAcceptedLanguages(header: string | null | undefined): AcceptedLanguage[] {
  if (!header) return [];

  return header
    .split(",")
    .map((entry, index): AcceptedLanguage | null => {
      const [rawTag, ...rawParams] = entry.trim().split(";");
      const tag = rawTag?.trim().replace(/_/g, "-");
      if (!tag || tag === "*") return null;

      const qParam = rawParams
        .map((param) => param.trim())
        .find((param) => param.toLowerCase().startsWith("q="));
      const q = parseQuality(qParam?.slice(2));
      if (q <= 0) return null;

      return { index, q, tag };
    })
    .filter((entry): entry is AcceptedLanguage => Boolean(entry))
    .sort((left, right) => right.q - left.q || left.index - right.index);
}

export function resolveLocaleFromLanguageTag(language: string | null | undefined): AppLocale | null {
  const normalized = language?.trim().replace(/_/g, "-");
  if (!normalized) return null;

  const lower = normalized.toLowerCase();
  const exactLocale = appLocaleByLowercaseTag.get(lower);
  if (exactLocale) return exactLocale;

  if (lower === "zh-tw" || lower === "zh-hk" || lower === "zh-mo" || lower.startsWith("zh-hant")) {
    return "zh-Hant";
  }

  if (lower === "zh" || lower === "zh-cn" || lower === "zh-sg" || lower.startsWith("zh-hans")) {
    return "zh";
  }

  const primary = lower.split("-")[0];
  if (primary === "pt") return "pt-BR";

  return appLocaleByLowercaseTag.get(primary) ?? null;
}

export function resolveLocaleFromAcceptLanguageHeader(header: string | null | undefined): AppLocale | null {
  for (const { tag } of parseAcceptedLanguages(header)) {
    const locale = resolveLocaleFromLanguageTag(tag);
    if (locale) return locale;
  }

  return null;
}
