"use client";

import { createElement } from "react";
import { CheckCircle2, Copy, Download, TriangleAlert } from "lucide-react";
import { toast } from "sonner";
import {
  DEFAULT_TOAST_COPY,
  resolveToastMessageKey,
  type ToastLocaleCopy,
} from "@/src/lib/toastCopy";

type ToastTone = "neutral" | "error";
type ToastIcon = "alert" | "check" | "copy" | "download";

type ToastCopy = {
  message: string;
  tone: ToastTone;
  icon: ToastIcon;
};

let toastLocaleCopy = DEFAULT_TOAST_COPY;

export function configureToastCopy(copy: ToastLocaleCopy) {
  toastLocaleCopy = copy;
}

function resolveToastMessage(message: string) {
  const key = resolveToastMessageKey(message);
  if (!key) {
    return message;
  }

  return toastLocaleCopy.messages[key];
}

function normalizeToastMessage(message: string) {
  const normalized = message
    .replace(/([\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}])[\s\u00a0]+(?=[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}])/gu, "$1")
    .replace(/[\s\u00a0]+([.,:;!?])/g, "$1")
    .replace(/[\s\u00a0]{2,}/g, " ")
    .trim();

  return normalized;
}

function resolveToastIcon(icon: ToastIcon) {
  const Icon = icon === "copy"
    ? Copy
    : icon === "download"
      ? Download
      : icon === "alert"
        ? TriangleAlert
        : CheckCircle2;
  return createElement(Icon, {
    "aria-hidden": true,
    className: "deckwise-app-toast__icon",
    strokeWidth: 2,
  });
}

function showToast({ message, tone, icon }: ToastCopy) {
  const toastMessage = normalizeToastMessage(message);
  if (!toastMessage) return;

  toast(toastMessage, {
    icon: resolveToastIcon(icon),
    className: `deckwise-app-toast--${tone} deckwise-app-toast--icon-${icon}`,
  });
}

export const notify = {
  info(message: string) {
    showToast({
      message: resolveToastMessage(message),
      tone: "neutral",
      icon: "check",
    });
  },
  download(message: string) {
    showToast({
      message: resolveToastMessage(message),
      tone: "neutral",
      icon: "download",
    });
  },
  success(message: string) {
    const messageKey = resolveToastMessageKey(message);
    const isCopiedToast = messageKey === "copiedDescription";

    showToast({
      message: isCopiedToast
        ? toastLocaleCopy.messages.copiedDescription
        : resolveToastMessage(message),
      tone: "neutral",
      icon: isCopiedToast ? "copy" : "check",
    });
  },
  error(message: string) {
    showToast({
      message: resolveToastMessage(message),
      tone: "error",
      icon: "alert",
    });
  },
};
