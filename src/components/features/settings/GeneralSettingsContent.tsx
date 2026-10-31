"use client";

import { useState } from "react";
import { Check, ChevronDown, Monitor, Moon, Sun } from "lucide-react";
import { useTranslations } from "next-intl";
import { useThemePreference } from "@/src/components/_shared/theme/useThemePreference";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/src/components/_shared/ui/dropdown-menu";
import { useLocaleSwitcher } from "@/src/lib/i18n/useLocaleSwitcher";
import type { ThemePreference } from "@/src/lib/theme";
import { cn } from "@/src/lib/utils";

type ThemeOption = {
  icon: typeof Sun;
  labelKey: "themeLight" | "themeDark" | "themeSystem";
  value: ThemePreference;
};

const THEME_OPTIONS: ThemeOption[] = [
  { icon: Sun, labelKey: "themeLight", value: "light" },
  { icon: Moon, labelKey: "themeDark", value: "dark" },
  { icon: Monitor, labelKey: "themeSystem", value: "system" },
];

const communicationPreferenceKeys = [
  "browserNotifications",
  "receiveProductUpdates",
  "emailQueuedTaskStarts",
  "adsAboutDeckwise",
] as const;

type CommunicationPreferenceKey = typeof communicationPreferenceKeys[number];

const defaultCommunicationPreferences: Record<CommunicationPreferenceKey, boolean> = {
  browserNotifications: false,
  receiveProductUpdates: false,
  emailQueuedTaskStarts: false,
  adsAboutDeckwise: false,
};

export function GeneralSettingsContent() {
  const tUser = useTranslations("user");
  const { themePreference, setThemePreference } = useThemePreference();
  const [communicationPreferences, setCommunicationPreferences] = useState(defaultCommunicationPreferences);
  const {
    activeLocale,
    isLocalePending,
    languageLabel,
    localeOptions,
    selectLocale,
  } = useLocaleSwitcher();
  const activeLocaleLabel = localeOptions.find((option) => option.value === activeLocale)?.label ?? activeLocale;
  const languageTriggerLabel = `${languageLabel}: ${activeLocaleLabel}`;

  function toggleCommunicationPreference(key: CommunicationPreferenceKey) {
    setCommunicationPreferences((current) => ({
      ...current,
      [key]: !current[key],
    }));
  }

  return (
    <div className="mt-6 max-w-[680px]">
      <section>
        <h2 className="text-[14px] font-semibold leading-5 text-foreground">{tUser("appearance")}</h2>

        <div className="mt-5">
          <div className="block max-w-[280px]">
            <div className="mb-2 block text-[14px] font-medium leading-5 text-foreground">{languageLabel}</div>
            <DropdownMenu>
              <DropdownMenuTrigger asChild disabled={isLocalePending}>
                <button
                  type="button"
                  aria-label={languageTriggerLabel}
                  className="flex h-9 w-full items-center justify-between gap-3 rounded-[8px] border-0 bg-foreground/[0.055] px-3 text-left text-[14px] font-medium text-foreground outline-none transition-colors hover:bg-foreground/[0.075] focus:bg-card focus:ring-2 focus:ring-foreground/[0.12] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <span className="truncate">{activeLocaleLabel}</span>
                  <ChevronDown className="size-4 shrink-0 text-icon-muted" strokeWidth={2} />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="w-[280px]">
                {localeOptions.map((option) => (
                  <DropdownMenuItem
                    key={option.value}
                    disabled={isLocalePending}
                    onSelect={() => {
                      selectLocale(option.value);
                    }}
                  >
                    <span>{option.label}</span>
                    {activeLocale === option.value ? (
                      <Check className="ml-auto size-4 text-icon-strong" strokeWidth={2} />
                    ) : null}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>

        <div className="mt-7">
          <div className="mb-2 text-[14px] font-medium leading-5 text-foreground">{tUser("theme")}</div>
          <div className="grid grid-cols-3 gap-2.5">
            {THEME_OPTIONS.map((option) => {
              const Icon = option.icon;
              const selected = themePreference === option.value;
              return (
                <button
                  type="button"
                  className={cn(
                    "flex h-[62px] min-w-0 flex-col items-center justify-center gap-1 rounded-[8px] border bg-card text-[14px] font-medium leading-5 text-foreground transition-colors hover:bg-accent/55",
                    selected ? "border-foreground ring-1 ring-foreground" : "border-border",
                  )}
                  aria-pressed={selected}
                  key={option.value}
                  onClick={() => setThemePreference(option.value)}
                >
                  <Icon className="size-4 text-icon-muted" strokeWidth={2} />
                  <span>{tUser(option.labelKey)}</span>
                </button>
              );
            })}
          </div>
        </div>
      </section>

      <section className="mt-8 border-t border-border pt-6">
        <h2 className="text-[14px] font-semibold leading-5 text-foreground">{tUser("communicationPreferences")}</h2>

        <div className="mt-4 space-y-5">
          {communicationPreferenceKeys.map((key) => (
            <div className="flex items-start justify-between gap-5" key={key}>
              <div className="min-w-0">
                <div className="text-[14px] font-medium leading-5 text-foreground">
                  {tUser(key)}
                </div>
                <p className="mt-1 max-w-[520px] text-[13px] leading-5 text-muted-foreground">
                  {tUser(`${key}Description`)}
                </p>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={communicationPreferences[key]}
                aria-label={tUser(key)}
                className={`relative mt-0.5 inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors ${
                  communicationPreferences[key] ? "bg-primary" : "bg-foreground/[0.14]"
                }`}
                onClick={() => toggleCommunicationPreference(key)}
              >
                <span
                  className={`size-4 rounded-full bg-card transition-transform ${
                    communicationPreferences[key] ? "translate-x-[18px]" : "translate-x-0.5"
                  }`}
                />
              </button>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
