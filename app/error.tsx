"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppErrorFallback, type RecoverableError } from "@/src/components/_shared/AppErrorFallback";
import { reportErrorBoundaryError } from "@/src/lib/monitoring/reportErrorBoundaryError";
import { getErrorRecoveryHomeHref } from "@/src/lib/routing/errorRecovery";

type RouteErrorPageProps = {
  error: RecoverableError;
  reset: () => void;
  unstable_retry?: () => void;
};

export default function RouteErrorPage({
  error,
  reset,
  unstable_retry,
}: RouteErrorPageProps) {
  const pathname = usePathname();
  const tApp = useTranslations("app");
  const tCommon = useTranslations("common");
  const tErrorBoundary = useTranslations("errorBoundary");

  useEffect(() => {
    reportErrorBoundaryError(error, {
      boundary: "app/error",
      pathname,
    });
  }, [error, pathname]);

  return (
    <AppErrorFallback
      error={error}
      title={tErrorBoundary("title")}
      message={tErrorBoundary("message")}
      retryLabel={tCommon("retry")}
      homeLabel={tApp("backToHome")}
      homeHref={getErrorRecoveryHomeHref(pathname)}
      onRetry={unstable_retry ?? reset}
    />
  );
}
