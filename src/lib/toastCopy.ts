export type ToastMessageKey =
  | "copiedDescription"
  | "documentNoLongerExists"
  | "fileUrlMissing"
  | "failedToLoadFiles"
  | "exportStarted";

export type ToastLocaleCopy = {
  messages: Record<ToastMessageKey, string>;
};

const TOAST_MESSAGE_KEYS: readonly ToastMessageKey[] = [
  "copiedDescription",
  "documentNoLongerExists",
  "fileUrlMissing",
  "failedToLoadFiles",
  "exportStarted",
];
const TOAST_MESSAGE_KEY_SET = new Set<ToastMessageKey>(TOAST_MESSAGE_KEYS);

const TOAST_FALLBACK_COPY: Record<"en" | "zh", ToastLocaleCopy> = {
  en: {
    messages: {
      copiedDescription: "Copied to clipboard",
      documentNoLongerExists: "Document no longer exists.",
      fileUrlMissing: "File URL is missing.",
      failedToLoadFiles: "Failed to load files, please try again later",
      exportStarted: "Export has started.",
    },
  },
  zh: {
    messages: {
      copiedDescription: "已复制到剪贴板",
      documentNoLongerExists: "文档不存在或已被删除",
      fileUrlMissing: "文件链接缺失",
      failedToLoadFiles: "文件加载失败，请稍后重试",
      exportStarted: "导出已开始。",
    },
  },
};

export const DEFAULT_TOAST_COPY = TOAST_FALLBACK_COPY.en;

const TOAST_MESSAGE_ALIASES: Record<ToastMessageKey, readonly string[]> = {
  copiedDescription: ["Copied", "已复制", "Copied to clipboard"],
  documentNoLongerExists: ["Document no longer exists."],
  fileUrlMissing: ["File URL is missing."],
  failedToLoadFiles: ["Failed to load files, please try again later"],
  exportStarted: [
    "Export has started.",
    "导出已开始。",
    "PowerPoint export has started.",
    "PowerPoint 导出已开始。",
  ],
};

function readStringRecord(value: unknown): Record<string, string> {
  if (!value || typeof value !== "object") {
    return {};
  }

  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, string] => (
      typeof entry[1] === "string"
    )),
  );
}

export function resolveToastMessageKey(message: string): ToastMessageKey | null {
  if (TOAST_MESSAGE_KEY_SET.has(message as ToastMessageKey)) {
    return message as ToastMessageKey;
  }

  const match = Object.entries(TOAST_MESSAGE_ALIASES).find(([, aliases]) =>
    aliases.includes(message),
  );
  if (!match) {
    return null;
  }

  const [key] = match as [ToastMessageKey, readonly string[]];
  return key;
}

export function resolveToastLocaleCopy(
  messages: unknown,
  locale: string,
): ToastLocaleCopy {
  const fallback =
    locale === "zh" ? TOAST_FALLBACK_COPY.zh : TOAST_FALLBACK_COPY.en;
  const root = messages && typeof messages === "object"
    ? messages as Record<string, unknown>
    : {};
  const toastRoot = root.toast && typeof root.toast === "object"
    ? root.toast as Record<string, unknown>
    : {};
  const messageCopy = readStringRecord(toastRoot.messages);

  return {
    messages: Object.fromEntries(
      TOAST_MESSAGE_KEYS.map((key) => [
        key,
        messageCopy[key] ?? fallback.messages[key],
      ]),
    ) as Record<ToastMessageKey, string>,
  };
}
