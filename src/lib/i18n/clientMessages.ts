import type { AbstractIntlMessages } from "next-intl";

const BASE_NAMESPACES = [
  "app",
  "common",
  "errorBoundary",
  "notFound",
] as const;

const LOGIN_NAMESPACES = [
  ...BASE_NAMESPACES,
  "login",
] as const;

const PRODUCT_NAMESPACES = [
  ...BASE_NAMESPACES,
  "billing",
  "chat",
  "chatInput",
  "codeBlock",
  "editor",
  "files",
  "deckwise",
  "home",
  "invitation",
  "middle",
  "personalization",
  "projects",
  "quote",
  "selectionToolbar",
  "shareSheet",
  "slidesPreview",
  "streamError",
  "systemContent",
  "toast",
  "tool",
  "user",
] as const;

export type ClientMessageScope = "base" | "login" | "product";
export type ClientMessageNamespace = (typeof PRODUCT_NAMESPACES)[number] | "login";

const NAMESPACES_BY_SCOPE: Record<
  ClientMessageScope,
  readonly ClientMessageNamespace[]
> = {
  base: BASE_NAMESPACES,
  login: LOGIN_NAMESPACES,
  product: PRODUCT_NAMESPACES,
};

export function getClientMessageNamespaces(
  scope: ClientMessageScope,
): readonly ClientMessageNamespace[] {
  return NAMESPACES_BY_SCOPE[scope];
}

export function pickClientMessages(
  messages: AbstractIntlMessages,
  scope: ClientMessageScope,
): AbstractIntlMessages {
  const picked: AbstractIntlMessages = {};

  for (const namespace of getClientMessageNamespaces(scope)) {
    const value = messages[namespace];
    if (value !== undefined) {
      picked[namespace] = value;
    }
  }

  return picked;
}
