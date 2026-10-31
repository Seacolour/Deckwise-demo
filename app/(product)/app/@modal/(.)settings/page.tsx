import { redirect } from "next/navigation";
import { SETTINGS_DEFAULT_ROUTE_PATH } from "@/src/lib/routing/settingsSheetRoutes";

export default function InterceptedSettingsIndexPage() {
  // This intercepted route only supports direct `/app/settings` navigation from
  // the app home. Normal menu shortcuts use `#settings/...` for no-unmount UI.
  redirect(SETTINGS_DEFAULT_ROUTE_PATH);
}
