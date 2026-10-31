"use client";

import { AlertTriangle, ArrowLeft, Home, RefreshCw } from "lucide-react";
import { Button } from "@/src/components/_shared/ui/button";
import { HOME_PATH } from "@/src/lib/routing/projectRoutes";

export type RecoverableError = Error & {
  digest?: string;
};

type AppErrorFallbackProps = {
  backLabel?: string;
  error?: RecoverableError | null;
  homeHref?: string;
  homeLabel: string;
  message: string;
  onBack?: () => void;
  onRetry?: () => void;
  retryLabel?: string;
  title: string;
};

function getDeveloperErrorDetails(error?: RecoverableError | null): string | null {
  if (process.env.NODE_ENV === "production" || !error) {
    return null;
  }

  const lines = [
    error.digest ? `Digest: ${error.digest}` : null,
    error.stack || error.message,
  ].filter(Boolean);

  return lines.length > 0 ? lines.join("\n\n") : null;
}

export function AppErrorFallback({
  backLabel,
  error,
  homeHref = HOME_PATH,
  homeLabel,
  message,
  onBack,
  onRetry,
  retryLabel,
  title,
}: AppErrorFallbackProps) {
  const developerDetails = getDeveloperErrorDetails(error);

  return (
    <main
      className="flex min-h-dvh w-full items-center justify-center bg-[rgb(var(--workspace-canvas))] p-6 text-foreground"
      role="alert"
    >
      <section className="w-full max-w-md rounded-lg border border-border/70 bg-card p-6 shadow-xl">
        <div className="flex size-11 items-center justify-center rounded-full bg-destructive/10 text-destructive">
          <AlertTriangle className="size-5" aria-hidden="true" />
        </div>
        <h1 className="mt-5 text-lg font-semibold leading-7 text-foreground">{title}</h1>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">{message}</p>

        {developerDetails ? (
          <pre className="mt-4 max-h-56 overflow-auto rounded-lg border border-border/70 bg-[rgb(var(--surface-secondary-rgb))] p-3 text-xs leading-5 text-muted-foreground scrollbar-capsule">
            {developerDetails}
          </pre>
        ) : null}

        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          {onBack ? (
            <Button type="button" variant="outline" className="h-10 rounded-full px-4" onClick={onBack}>
              <ArrowLeft className="size-4" aria-hidden="true" />
              {backLabel}
            </Button>
          ) : null}
          <Button asChild variant={onRetry ? "outline" : "default"} className="h-10 rounded-full px-4">
            <a href={homeHref}>
              <Home className="size-4" aria-hidden="true" />
              {homeLabel}
            </a>
          </Button>
          {onRetry ? (
            <Button type="button" className="h-10 rounded-full px-4" onClick={onRetry}>
              <RefreshCw className="size-4" aria-hidden="true" />
              {retryLabel}
            </Button>
          ) : null}
        </div>
      </section>
    </main>
  );
}
