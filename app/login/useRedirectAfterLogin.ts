"use client";

import { useCallback } from "react";
import { useSafeAppRouter } from "@/src/lib/routing/navigation";
import {
  resolveLoginRedirectTarget,
  RETURN_TO_STORAGE_KEY,
} from "@/src/lib/auth/loginRedirect";

export function useRedirectAfterLogin() {
  const router = useSafeAppRouter();

  return useCallback(async () => {
    try {
      const returnTo = sessionStorage.getItem(RETURN_TO_STORAGE_KEY);
      sessionStorage.removeItem(RETURN_TO_STORAGE_KEY);
      const target = resolveLoginRedirectTarget({
        search: window.location.search,
        storedReturnTo: returnTo,
        currentOrigin: window.location.origin,
      });
      router.replace(target);
    } catch {
      router.replace(resolveLoginRedirectTarget({}));
    }
  }, [router]);
}
