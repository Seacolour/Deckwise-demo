import { notFound } from "next/navigation";
import { SettingsCenterPage } from "@/src/components/features/settings/SettingsCenterSheet";
import {
  SETTINGS_CENTER_SECTIONS,
  isSettingsSheetSection,
  type SettingsSheetSection,
} from "@/src/lib/routing/settingsSheetRoutes";
import ChatWorkbenchClient from "@/src/features/workbench/ChatWorkbenchClient";

type SettingsSectionRouteProps = {
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

export default async function AppSettingsSectionPage({ params }: SettingsSectionRouteProps) {
  const { section } = await params;
  // Direct settings links show the conversation workspace beneath the sheet.
  return (
    <>
      <ChatWorkbenchClient />
      <SettingsCenterPage section={resolveSettingsSection(section)} />
    </>
  );
}
