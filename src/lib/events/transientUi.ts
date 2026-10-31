export const TRANSIENT_UI_EVENTS = {
  DISMISS_REQUESTED: "deckwise:transient-ui-dismiss-request",
} as const;

export type TransientUiDismissDetail = {
  source?: string;
  reason?: string;
};

const openTransientUiIds = new Set<string>();
const openListeners = new Set<() => void>();

function notifyOpenListeners() {
  openListeners.forEach((listener) => {
    listener();
  });
}

export function registerTransientUiOpen(id: string) {
  const previousSize = openTransientUiIds.size;
  openTransientUiIds.add(id);
  if (openTransientUiIds.size !== previousSize) {
    notifyOpenListeners();
  }
}

export function unregisterTransientUiOpen(id: string) {
  if (openTransientUiIds.delete(id)) {
    notifyOpenListeners();
  }
}

export function subscribeTransientUiOpen(listener: () => void) {
  openListeners.add(listener);
  return () => {
    openListeners.delete(listener);
  };
}

export function getTransientUiOpenSnapshot() {
  return openTransientUiIds.size > 0;
}

export function dispatchTransientUiDismissRequest(
  detail: TransientUiDismissDetail = {},
) {
  try {
    window.dispatchEvent(
      new CustomEvent<TransientUiDismissDetail>(
        TRANSIENT_UI_EVENTS.DISMISS_REQUESTED,
        { detail },
      ),
    );
  } catch {
    // Dismiss requests are best-effort UI cleanup and should never block input.
  }
}
