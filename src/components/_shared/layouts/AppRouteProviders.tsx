"use client";

import type { ReactNode } from "react";
import AuthGate from "@/app/gate/AuthGate";
import { ErrorBoundary } from "@/src/components/_shared/ErrorBoundary";
import AppShell from "@/src/components/_shared/layouts/AppShell";
import { ViewportWidthGuard } from "@/src/components/_shared/layouts/ViewportWidthGuard";

type AppRouteProvidersProps = {
  children: ReactNode;
};

export function AppRouteProviders({ children }: AppRouteProvidersProps) {
  return (
    <>
      <ViewportWidthGuard>
        <ErrorBoundary>
          <AppShell>
            <AuthGate>{children}</AuthGate>
          </AppShell>
        </ErrorBoundary>
      </ViewportWidthGuard>
    </>
  );
}
