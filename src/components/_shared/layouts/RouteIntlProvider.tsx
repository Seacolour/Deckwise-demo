import type { ReactNode } from "react";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import {
  pickClientMessages,
  type ClientMessageScope,
} from "@/src/lib/i18n/clientMessages";

type RouteIntlProviderProps = {
  children: ReactNode;
  scope: Exclude<ClientMessageScope, "base">;
};

/**
 * A nested provider is required for route-specific catalogs because the root
 * layout persists during client navigation. Putting route selection only in
 * the root provider would leave stale messages after auth/product
 * transitions.
 */
export async function RouteIntlProvider({
  children,
  scope,
}: RouteIntlProviderProps) {
  const [locale, allMessages] = await Promise.all([getLocale(), getMessages()]);

  return (
    <NextIntlClientProvider
      locale={locale}
      messages={pickClientMessages(allMessages, scope)}
    >
      {children}
    </NextIntlClientProvider>
  );
}
