"use client";

import { useCallback, useMemo } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

type NavigationMode = "push" | "replace";

const NAVIGATION_DEDUPE_WINDOW_MS = 1_500;

type RecentNavigation = {
  mode: NavigationMode;
  href: string;
  at: number;
};

let recentNavigation: RecentNavigation | null = null;

function normalizeAppHref(href: string, currentHref: string): string {
  const normalizedHref = href.trim();
  if (!normalizedHref) {
    return currentHref;
  }

  const base =
    typeof window !== "undefined"
      ? window.location.href
      : `https://deckwise.local${currentHref || "/"}`;
  const url = new URL(normalizedHref, base);
  return `${url.pathname}${url.search}${url.hash}`;
}

function shouldSkipNavigation(input: {
  mode: NavigationMode;
  currentHref: string;
  targetHref: string;
}): boolean {
  if (input.targetHref === input.currentHref) {
    return true;
  }

  if (!recentNavigation) {
    return false;
  }

  return (
    recentNavigation.mode === input.mode &&
    recentNavigation.href === input.targetHref &&
    Date.now() - recentNavigation.at < NAVIGATION_DEDUPE_WINDOW_MS
  );
}

function rememberNavigation(mode: NavigationMode, href: string) {
  recentNavigation = {
    mode,
    href,
    at: Date.now(),
  };
}

export function useSafeAppRouter() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const currentHref = useMemo(() => {
    const query = searchParams?.toString();
    return `${pathname ?? "/"}${query ? `?${query}` : ""}`;
  }, [pathname, searchParams]);

  const replace = useCallback(
    (href: string) => {
      const targetHref = normalizeAppHref(href, currentHref);
      // Pitfall record:
      // app route transitions can be triggered from several shells
      // (AuthGate, app-home redirect, project pages, menu actions). If any of
      // them re-issue the current route or rapidly re-issue the same target,
      // Next will keep re-fetching the same RSC payload. Centralize the guard
      // here so callers cannot accidentally self-refresh the route tree.
      if (shouldSkipNavigation({ mode: "replace", currentHref, targetHref })) {
        return false;
      }

      rememberNavigation("replace", targetHref);
      router.replace(targetHref);
      return true;
    },
    [currentHref, router],
  );

  const push = useCallback(
    (href: string) => {
      const targetHref = normalizeAppHref(href, currentHref);
      if (shouldSkipNavigation({ mode: "push", currentHref, targetHref })) {
        return false;
      }

      rememberNavigation("push", targetHref);
      router.push(targetHref);
      return true;
    },
    [currentHref, router],
  );

  const prefetch = useCallback(
    (href: string) => {
      const targetHref = normalizeAppHref(href, currentHref);
      router.prefetch(targetHref);
    },
    [currentHref, router],
  );

  return useMemo(
    () => ({
      prefetch,
      push,
      replace,
    }),
    [prefetch, push, replace],
  );
}
