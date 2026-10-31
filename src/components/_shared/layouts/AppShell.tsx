"use client";

import dynamic from "next/dynamic";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { AppToaster } from "@/src/components/_shared/ui/toaster";
import {
  isSettingsRoutePath,
  resolveSettingsSheetSectionFromHash,
  type SettingsSheetSection,
} from "@/src/lib/routing/settingsSheetRoutes";

const LazySettingsCenterSheet = dynamic(
  () => import("@/src/components/features/settings/SettingsCenterSheet").then((mod) => mod.SettingsCenterSheet),
  { loading: () => null, ssr: false },
);

/** Shared product shell: workspace pages now own their own navigation and composer. */
export default function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const settingsRouteOpen = isSettingsRoutePath(pathname);
  const [settingsSection, setSettingsSection] = useState<SettingsSheetSection | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const closeTimerRef = useRef<number | null>(null);

  const clearCloseTimer = useCallback(() => {
    if (closeTimerRef.current !== null) {
      window.clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
  }, []);

  const closeSettings = useCallback((syncUrl = false) => {
    clearCloseTimer();
    setSettingsOpen(false);
    if (syncUrl) {
      window.history.replaceState(window.history.state, "", `${window.location.pathname}${window.location.search}`);
    }
    closeTimerRef.current = window.setTimeout(() => {
      setSettingsSection(null);
      closeTimerRef.current = null;
    }, 220);
  }, [clearCloseTimer]);

  useEffect(() => {
    const syncFromHash = () => {
      const section = resolveSettingsSheetSectionFromHash(window.location.hash);
      if (section) {
        clearCloseTimer();
        setSettingsSection(section);
        setSettingsOpen(true);
      } else {
        closeSettings();
      }
    };
    syncFromHash();
    window.addEventListener("hashchange", syncFromHash);
    return () => {
      window.removeEventListener("hashchange", syncFromHash);
      clearCloseTimer();
    };
  }, [clearCloseTimer, closeSettings]);

  return (
    <div className="app-persistent-shell h-dvh overflow-hidden text-foreground">
      <AppToaster />
      {children}
      {!settingsRouteOpen && settingsSection && (
        <LazySettingsCenterSheet
          key={settingsSection}
          open={settingsOpen}
          section={settingsSection}
          urlMode="hash"
          onOpenChange={(open) => {
            if (open) setSettingsOpen(true);
            else closeSettings(true);
          }}
        />
      )}
    </div>
  );
}
