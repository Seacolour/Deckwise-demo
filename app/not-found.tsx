"use client";

import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppErrorFallback } from "@/src/components/_shared/AppErrorFallback";
import { getErrorRecoveryHomeHref } from "@/src/lib/routing/errorRecovery";

export default function NotFoundPage() {
  const pathname = usePathname();
  const tApp = useTranslations("app");
  const tNotFound = useTranslations("notFound");

  return (
    <AppErrorFallback
      title={tNotFound("title")}
      message={tNotFound("message")}
      homeLabel={tApp("backToHome")}
      homeHref={getErrorRecoveryHomeHref(pathname)}
    />
  );
}
