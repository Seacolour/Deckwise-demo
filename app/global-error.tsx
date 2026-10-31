"use client";

import "./globals.css";

import { useEffect } from "react";
import { AppErrorFallback, type RecoverableError } from "@/src/components/_shared/AppErrorFallback";
import { ThemeController } from "@/src/components/_shared/theme/ThemeController";
import { getBrowserGlobalErrorCopy } from "@/src/lib/i18n/globalErrorCopy";
import { reportErrorBoundaryError } from "@/src/lib/monitoring/reportErrorBoundaryError";
import { createThemeInitScript } from "@/src/lib/theme";

type GlobalErrorPageProps = {
  error: RecoverableError;
  reset: () => void;
  unstable_retry?: () => void;
};

export default function GlobalErrorPage({
  error,
  reset,
  unstable_retry,
}: GlobalErrorPageProps) {
  const copy = getBrowserGlobalErrorCopy();

  useEffect(() => {
    reportErrorBoundaryError(error, {
      boundary: "app/global-error",
    });
  }, [error]);

  return (
    <html
      lang={copy.htmlLang}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: createThemeInitScript() }} />
      </head>
      <body className="font-sans antialiased" style={{ margin: 0 }} suppressHydrationWarning>
        <ThemeController />
        <AppErrorFallback
          error={error}
          title={copy.title}
          message={copy.message}
          retryLabel={copy.retryLabel}
          homeLabel={copy.homeLabel}
          homeHref="/"
          onRetry={unstable_retry ?? reset}
        />
      </body>
    </html>
  );
}
