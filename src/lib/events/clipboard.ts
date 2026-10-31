export const CLIPBOARD_EVENTS = {
  COPY_SUCCESS: "deckwise:clipboard-copy-success",
} as const;

export type ClipboardCopySuccessDetail = {
  activeElement: Element | null;
  method: "clipboard-api" | "exec-command";
  textLength: number;
};

export type ClipboardCopySuccessEvent = CustomEvent<ClipboardCopySuccessDetail>;

export type ClipboardCopyAcknowledgeEvent =
  | ClipboardEvent
  | ClipboardCopySuccessEvent;

export function dispatchClipboardCopySuccess(
  detail: ClipboardCopySuccessDetail,
) {
  if (typeof document === "undefined") {
    return;
  }

  document.dispatchEvent(
    new CustomEvent<ClipboardCopySuccessDetail>(
      CLIPBOARD_EVENTS.COPY_SUCCESS,
      { detail },
    ),
  );
}
