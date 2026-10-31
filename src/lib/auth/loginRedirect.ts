import { HOME_PATH } from "@/src/lib/routing/projectRoutes";

export const LOGIN_REDIRECT_PARAM = "redirectUrl";
export const RETURN_TO_STORAGE_KEY = "returnTo";

type RedirectOptions = {
  currentOrigin?: string | null;
};

function getBrowserOrigin(): string | null {
  if (typeof window === "undefined" || !window.location) {
    return null;
  }

  return window.location.origin || null;
}

function normalizeOrigin(origin: string | null | undefined): string | null {
  const trimmed = origin?.trim();
  if (!trimmed) {
    return null;
  }

  try {
    return new URL(trimmed).origin;
  } catch {
    return null;
  }
}

export function resolveSafeRedirectTarget(
  value: string | null | undefined,
  options: RedirectOptions = {},
): string | null {
  const raw = value?.trim();
  if (!raw) {
    return null;
  }

  if (raw.startsWith("/") && !raw.startsWith("//")) {
    return raw;
  }

  const currentOrigin = normalizeOrigin(options.currentOrigin ?? getBrowserOrigin());
  if (!currentOrigin) {
    return null;
  }

  try {
    const url = new URL(raw);
    if (url.origin !== currentOrigin) {
      return null;
    }

    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return null;
  }
}

export function buildLoginRedirectPath(
  returnTo: string | null | undefined,
  options: RedirectOptions = {},
): string {
  const target = resolveSafeRedirectTarget(returnTo, options);
  if (!target) {
    return "/login";
  }

  const params = new URLSearchParams({
    [LOGIN_REDIRECT_PARAM]: target,
  });
  return `/login?${params.toString()}`;
}

export function resolveLoginRedirectTarget(input: {
  search?: string | null;
  storedReturnTo?: string | null;
  currentOrigin?: string | null;
}): string {
  const params = new URLSearchParams(input.search ?? "");
  const fromUrl = resolveSafeRedirectTarget(params.get(LOGIN_REDIRECT_PARAM), {
    currentOrigin: input.currentOrigin,
  });
  if (fromUrl) {
    return fromUrl;
  }

  return (
    resolveSafeRedirectTarget(input.storedReturnTo, {
      currentOrigin: input.currentOrigin,
    }) ?? HOME_PATH
  );
}
