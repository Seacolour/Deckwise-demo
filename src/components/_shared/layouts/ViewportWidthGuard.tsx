"use client";

import type { ReactNode } from "react";

type ViewportWidthGuardProps = {
  children: ReactNode;
};

export function ViewportWidthGuard({ children }: ViewportWidthGuardProps) {
  return (
    <div className="app-viewport-width-shell h-dvh w-full">
      <div className="app-viewport-width-content h-full w-full">
        {children}
      </div>
    </div>
  );
}
