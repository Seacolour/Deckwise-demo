"use client";

import { safeDispatchEvent } from "@/src/lib/utils/errorHandler";

const APP_CENTERED_SHEET_WILL_OPEN_EVENT = "deckwise:app-centered-sheet-will-open";

export function dispatchAppCenteredSheetWillOpen() {
  if (typeof window === "undefined") {
    return;
  }

  safeDispatchEvent(new Event(APP_CENTERED_SHEET_WILL_OPEN_EVENT));
}

export function subscribeAppCenteredSheetWillOpen(listener: () => void): () => void {
  if (typeof window === "undefined") {
    return () => {};
  }

  window.addEventListener(APP_CENTERED_SHEET_WILL_OPEN_EVENT, listener);
  return () => {
    window.removeEventListener(APP_CENTERED_SHEET_WILL_OPEN_EVENT, listener);
  };
}
