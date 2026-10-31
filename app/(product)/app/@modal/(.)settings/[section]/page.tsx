import { notFound } from "next/navigation";
import { SettingsCenterRouteModalPage } from "@/src/components/features/settings/SettingsCenterSheet";
import {
  SETTINGS_CENTER_SECTIONS,
  isSettingsSheetSection,
  type SettingsSheetSection,
} from "@/src/lib/routing/settingsSheetRoutes";

type InterceptedSettingsSectionRouteProps = {
  params: Promise<{
    section: string;
  }>;
};

export function generateStaticParams() {
  return SETTINGS_CENTER_SECTIONS.map((section) => ({ section }));
}

function resolveSettingsSection(section: string): SettingsSheetSection {
  if (!isSettingsSheetSection(section)) {
    notFound();
  }

  return section;
}

export default async function InterceptedSettingsSectionPage({ params }: InterceptedSettingsSectionRouteProps) {
  const { section } = await params;
  // Normal in-app entry points use `#settings/...`; this intercepted route is a
  // fallback for direct `/app/settings/...` links from the app home. Keep future
  // sections in the same Settings Center instead of adding standalone modals.
  return <SettingsCenterRouteModalPage section={resolveSettingsSection(section)} />;
}
