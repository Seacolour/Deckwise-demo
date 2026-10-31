"use client";

import { useCallback, useTransition } from "react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import {
  appLocaleOptions,
  defaultLocale,
  isAppLocale,
  persistLocalePreference,
  resolveHtmlLang,
  type AppLocale,
  type AppLocaleOption,
} from "@/src/lib/i18n";

export type LocaleSwitcherOption = AppLocaleOption;

export function useLocaleSwitcher() {
  const router = useRouter();
  const locale = useLocale();
  const tUser = useTranslations("user");
  const activeLocale = isAppLocale(locale) ? locale : defaultLocale;
  const [isLocalePending, startLocaleTransition] = useTransition();

  const selectLocale = useCallback((nextLocale: AppLocale, onChanged?: () => void) => {
    if (nextLocale === activeLocale) {
      return false;
    }

    persistLocalePreference(nextLocale);
    document.documentElement.lang = resolveHtmlLang(nextLocale);
    onChanged?.();
    startLocaleTransition(() => {
      router.refresh();
    });
    return true;
  }, [activeLocale, router]);

  return {
    activeLocale,
    isLocalePending,
    languageLabel: tUser("language"),
    localeOptions: appLocaleOptions,
    selectLocale,
  };
}
