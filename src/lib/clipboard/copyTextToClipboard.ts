import { dispatchClipboardCopySuccess } from "@/src/lib/events/clipboard";

type CopyTextToClipboardOptions = {
  preferSynchronous?: boolean;
};

function copyTextWithHiddenTextarea(text: string, activeElement: Element | null) {
  try {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.setAttribute("readonly", "true");
    textarea.style.position = "fixed";
    textarea.style.left = "-9999px";
    textarea.style.top = "0";
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand("copy");
    document.body.removeChild(textarea);
    if (copied) {
      dispatchClipboardCopySuccess({
        activeElement,
        method: "exec-command",
        textLength: text.length,
      });
    }
    return copied;
  } catch {
    return false;
  }
}

export async function copyTextToClipboard(
  text: string,
  options: CopyTextToClipboardOptions = {},
): Promise<boolean> {
  const activeElement =
    typeof document !== "undefined" && document.activeElement instanceof Element
      ? document.activeElement
      : null;

  if (options.preferSynchronous) {
    if (copyTextWithHiddenTextarea(text, activeElement)) {
      return true;
    }
  }

  const canUseClipboardApi =
    typeof navigator !== "undefined" &&
    Boolean(navigator.clipboard) &&
    (typeof window === "undefined" || window.isSecureContext);

  if (canUseClipboardApi) {
    try {
      await navigator.clipboard.writeText(text);
      dispatchClipboardCopySuccess({
        activeElement,
        method: "clipboard-api",
        textLength: text.length,
      });
      return true;
    } catch {
      // Fall through to the legacy textarea path.
    }
  }

  return copyTextWithHiddenTextarea(text, activeElement);
}
