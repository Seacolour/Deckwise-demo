import "../globals.css";

import type { AbstractIntlMessages } from "next-intl";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import type { ReactNode } from "react";
import { WebVitalsReporter } from "@/src/components/_shared/monitoring/WebVitalsReporter";
import { DesktopWindowFrame } from "@/src/components/_shared/layouts/DesktopWindowFrame";
import { ExplicitIntlProvider } from "@/src/components/_shared/layouts/ExplicitIntlProvider";
import { ThemeController } from "@/src/components/_shared/theme/ThemeController";
import {
  defaultLocale,
  isAppLocale,
  resolveHtmlLang,
  type AppLocale,
} from "@/src/lib/i18n";
import {
  pickClientMessages,
} from "@/src/lib/i18n/clientMessages";
import { createThemeInitScript } from "@/src/lib/theme";

type RootDocumentProps = {
  children: ReactNode;
  explicitIntl?: boolean;
  locale: AppLocale;
  messages: AbstractIntlMessages;
  themePolicy?: "static-light" | "user";
  timeZone?: string;
};

function RootDocument({
  children,
  explicitIntl = false,
  locale,
  messages,
  themePolicy = "user",
  timeZone,
}: RootDocumentProps) {
  const content = (
    <DesktopWindowFrame>
      {/* Keep system-theme changes and cross-tab preferences live on every route. */}
      {themePolicy === "user" ? <ThemeController /> : null}
      <WebVitalsReporter />
      {children}
    </DesktopWindowFrame>
  );

  return (
    <html
      lang={resolveHtmlLang(locale)}
      suppressHydrationWarning
    >
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: createThemeInitScript({
              forcedPreference: themePolicy === "static-light" ? "light" : undefined,
            }),
          }}
        />
      </head>
      <body className="font-sans antialiased" suppressHydrationWarning>
        {explicitIntl ? (
          <ExplicitIntlProvider
            locale={locale}
            messages={messages}
            timeZone={timeZone}
          >
            {content}
          </ExplicitIntlProvider>
        ) : (
          <NextIntlClientProvider
            locale={locale}
            messages={messages}
            timeZone={timeZone}
          >
            {content}
          </NextIntlClientProvider>
        )}
      </body>
    </html>
  );
}
export async function RuntimeRootDocument({
  children,
  themePolicy = "user",
}: {
  children: ReactNode;
  themePolicy?: "static-light" | "user";
}) {
  const [locale, allMessages] = await Promise.all([getLocale(), getMessages()]);
  const appLocale = isAppLocale(locale) ? locale : defaultLocale;

  return (
    <RootDocument
      locale={appLocale}
      messages={pickClientMessages(allMessages, "base")}
      themePolicy={themePolicy}
    >
      {children}
    </RootDocument>
  );
}
