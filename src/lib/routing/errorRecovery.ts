import { HOME_PATH } from "@/src/lib/routing/projectRoutes";

export function getErrorRecoveryHomeHref(pathname?: string | null): string {
  if (
    !pathname ||
    pathname === "/login" ||
    pathname === "/"
  ) {
    return "/login";
  }

  return HOME_PATH;
}
