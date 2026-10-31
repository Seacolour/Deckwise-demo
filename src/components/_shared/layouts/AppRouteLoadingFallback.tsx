"use client";

import { CenteredLoader } from "@/src/components/_shared/ui/loader";

export function AppRouteLoadingFallback() {
  return (
    <div className="flex h-dvh min-h-0 items-center justify-center">
      <CenteredLoader />
    </div>
  );
}
