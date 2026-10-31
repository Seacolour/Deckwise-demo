"use client";

import { useEffect, useEffectEvent, useRef } from "react";
import { ensureDailyCheckin } from "@/src/lib/checkin/dailyCheckin";

type UseAutoDailyCheckinOptions = {
  enabled: boolean;
  userKey: string | null;
};

export function useAutoDailyCheckin({
  enabled,
  userKey,
}: UseAutoDailyCheckinOptions): void {
  const lastStartedUserKeyRef = useRef<string | null>(null);

  const runCheckin = useEffectEvent(() => {
    if (!enabled || !userKey || userKey === "anon") {
      return;
    }

    void ensureDailyCheckin(userKey).catch(() => {
      // Retry is driven by the next app entry or visibility regain.
    });
  });

  useEffect(() => {
    if (!enabled || !userKey || userKey === "anon") {
      lastStartedUserKeyRef.current = null;
      return;
    }

    if (lastStartedUserKeyRef.current === userKey) {
      return;
    }

    lastStartedUserKeyRef.current = userKey;
    runCheckin();
  }, [enabled, userKey]);

  useEffect(() => {
    if (!enabled || !userKey || userKey === "anon") {
      return;
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState !== "visible") {
        return;
      }

      runCheckin();
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [enabled, userKey]);
}
