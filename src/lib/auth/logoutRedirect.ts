export const WEB_LOGOUT_REDIRECT_PATH = "/login";

export function resolveLogoutRedirectPath(): string {
  return WEB_LOGOUT_REDIRECT_PATH;
}

export function resolveExplicitLogoutRedirectPath(value: string | null): string | null {
  if (value === WEB_LOGOUT_REDIRECT_PATH) {
    return value;
  }
  return null;
}

export function isLogoutLandingPath(pathname: string): boolean {
  return pathname === WEB_LOGOUT_REDIRECT_PATH;
}
