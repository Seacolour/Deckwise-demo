"use client";

import { useEffect, type MouseEvent, type ReactNode } from "react";
import { ArrowLeft, ArrowRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { BrandLogo } from "@/src/components/_shared/brand/BrandLogo";
import {
  getDesktopBridge,
  type DesktopMenuId,
} from "@/src/lib/desktop/desktopBridge";

const DESKTOP_MENUS: readonly DesktopMenuId[] = [
  "file",
  "edit",
  "view",
  "window",
  "help",
];

function DesktopWindowChrome() {
  const t = useTranslations("common.desktopWindow");

  useEffect(() => {
    const bridge = getDesktopBridge();
    if (!bridge) {
      return;
    }

    const root = document.documentElement;
    root.dataset.deckwiseDesktop = "true";
    root.dataset.deckwisePlatform = bridge.platform ?? "unknown";
    const setTitleBarTheme = bridge.setDesktopTitleBarTheme;
    if (!setTitleBarTheme) {
      return;
    }

    let lastTheme: "light" | "dark" | null = null;
    const syncTheme = () => {
      const nextTheme = root.dataset.themeResolved === "dark" ? "dark" : "light";
      if (nextTheme === lastTheme) {
        return;
      }

      lastTheme = nextTheme;
      void setTitleBarTheme(nextTheme).catch(() => {});
    };

    syncTheme();
    const observer = new MutationObserver(syncTheme);
    observer.observe(root, {
      attributes: true,
      attributeFilter: ["class", "data-theme-resolved"],
    });

    return () => observer.disconnect();
  }, []);

  const openMenu = (
    event: MouseEvent<HTMLButtonElement>,
    menuId: DesktopMenuId,
  ) => {
    const bridge = getDesktopBridge();
    if (!bridge?.openDesktopMenu) {
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    void bridge.openDesktopMenu(menuId, {
      x: rect.left,
      y: rect.bottom,
    }).catch(() => {});
  };

  return (
    <header className="desktop-window-chrome" data-testid="desktop-window-chrome">
      <div className="desktop-window-controls-cluster">
        <div className="desktop-window-brand" aria-label="Deckwise">
          <BrandLogo className="size-[18px]" aria-hidden="true" />
        </div>

        <button
          type="button"
          className="desktop-window-icon-button"
          aria-label={t("back")}
          title={t("back")}
          onClick={() => window.history.back()}
        >
          <ArrowLeft aria-hidden="true" />
        </button>
        <button
          type="button"
          className="desktop-window-icon-button"
          aria-label={t("forward")}
          title={t("forward")}
          onClick={() => window.history.forward()}
        >
          <ArrowRight aria-hidden="true" />
        </button>

        <nav className="desktop-window-menu-bar" aria-label={t("menuBar")}>
          {DESKTOP_MENUS.map((menuId) => (
            <button
              key={menuId}
              type="button"
              className="desktop-window-menu-button"
              data-desktop-menu-id={menuId}
              onClick={(event) => openMenu(event, menuId)}
            >
              {t(menuId)}
            </button>
          ))}
        </nav>
      </div>

      <div className="desktop-window-drag-region" aria-hidden="true" />
    </header>
  );
}

export function DesktopWindowFrame({ children }: { children: ReactNode }) {
  return (
    <div className="desktop-window-frame">
      <DesktopWindowChrome />
      <div className="desktop-window-content">{children}</div>
    </div>
  );
}
