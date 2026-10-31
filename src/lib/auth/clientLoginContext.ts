export type ClientLoginContext = {
  locale?: string;
  country?: string;
  timeZone?: string;
};

type ClientLoginContextSource = {
  appLocale?: string | null;
  browserLocale?: unknown;
  browserLocales?: readonly unknown[] | null;
  timeZone?: unknown;
};

function pickString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0
    ? value.trim()
    : undefined;
}

function normalizeLocale(value: unknown): string | undefined {
  return pickString(value)?.replace(/_/g, "-");
}

function resolveCountry(locale: string | undefined): string | undefined {
  if (!locale) return undefined;
  const match = locale.match(/[-_]([a-z]{2}|\d{3})(?:[-_].*)?$/i);
  return match?.[1]?.toUpperCase();
}

function resolveBrowserTimeZone(): string | undefined {
  try {
    return pickString(Intl.DateTimeFormat().resolvedOptions().timeZone);
  } catch {
    return undefined;
  }
}

function readBrowserLocaleSource(): Pick<ClientLoginContextSource, "browserLocale" | "browserLocales"> {
  if (typeof navigator === "undefined") {
    return {};
  }

  return {
    browserLocale: navigator.language,
    browserLocales: Array.isArray(navigator.languages) ? navigator.languages : undefined,
  };
}

export function buildClientLoginContextFromSource(
  source: ClientLoginContextSource = {},
): ClientLoginContext {
  const locale = [
    ...(Array.isArray(source.browserLocales) ? source.browserLocales : []),
    source.browserLocale,
    source.appLocale,
  ]
    .map(normalizeLocale)
    .find((candidate): candidate is string => Boolean(candidate));
  const country = resolveCountry(locale);
  const timeZone = pickString(source.timeZone);

  return {
    ...(locale ? { locale } : {}),
    ...(country ? { country } : {}),
    ...(timeZone ? { timeZone } : {}),
  };
}

export function buildClientLoginContext(appLocale?: string | null): ClientLoginContext {
  return buildClientLoginContextFromSource({
    ...readBrowserLocaleSource(),
    appLocale,
    timeZone: resolveBrowserTimeZone(),
  });
}
