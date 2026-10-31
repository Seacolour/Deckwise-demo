"use client";

import { useEffect, useMemo } from "react";
import { X } from "lucide-react";
import { useLocale, useMessages } from "next-intl";
import { Toaster } from "sonner";
import { useThemePreference } from "@/src/components/_shared/theme/useThemePreference";
import { configureToastCopy } from "@/src/lib/toast";
import { resolveToastLocaleCopy } from "@/src/lib/toastCopy";

export function AppToaster() {
  const { resolvedTheme } = useThemePreference();
  const locale = useLocale();
  const messages = useMessages();
  const toastCopy = useMemo(
    () => resolveToastLocaleCopy(messages, locale),
    [locale, messages],
  );

  useEffect(() => {
    configureToastCopy(toastCopy);
  }, [toastCopy]);

  return (
    <Toaster
      position="top-right"
      closeButton
      duration={3000}
      offset={{ top: "80px", right: "12px" }}
      mobileOffset={{ top: "80px", right: "12px", left: "12px" }}
      swipeDirections={[]}
      theme={resolvedTheme}
      icons={{
        close: (
          <X
            aria-hidden="true"
            className="deckwise-app-toast__close-icon"
            strokeWidth={2}
          />
        ),
      }}
      toastOptions={{
        className: "deckwise-app-toast",
        style: {
          borderRadius: 0,
        },
      }}
    />
  );
}
