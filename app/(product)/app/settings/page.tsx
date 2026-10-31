import { redirect } from "next/navigation";
import { SETTINGS_DEFAULT_ROUTE_PATH } from "@/src/lib/routing/settingsSheetRoutes";

export default function AppSettingsPage() {
  // Real settings routes are fallback/deep-link boundaries. Normal in-app
  // shortcuts use `#settings/...` so the active surface remains mounted.
  redirect(SETTINGS_DEFAULT_ROUTE_PATH);
}
