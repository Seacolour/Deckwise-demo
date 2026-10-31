"use client";

import { useCallback, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import {
  Database,
  Mail,
  Shapes,
  SlidersHorizontal,
  UserRound,
} from "lucide-react";
import { AppCenteredSheet } from "@/src/components/_shared/ui/AppCenteredSheet";
import { AccountSettingsContent } from "@/src/components/features/settings/AccountSettingsContent";
import { GeneralSettingsContent } from "@/src/components/features/settings/GeneralSettingsContent";
import { ModelProviderSettingsContent } from "@/src/components/features/settings/ModelProviderSettingsContent";
import { PersonalizationSettingsContent } from "@/src/components/features/settings/PersonalizationSettingsContent";
import {
  resolveUserDisplayName,
} from "@/src/lib/auth/userProfile";
import {
  getSettingsSheetHash,
  getSettingsSheetPath,
  type SettingsSheetSection,
} from "@/src/lib/routing/settingsSheetRoutes";
import { FEEDBACK_HREF } from "@/src/lib/routing/helpCenterRoutes";
import { HOME_PATH } from "@/src/lib/routing/projectRoutes";
import { cn } from "@/src/lib/utils";
import { safeExecute } from "@/src/lib/utils/errorHandler";
import { useAuthSnapshot } from "@/src/stores/auth";
import styles from "./SettingsCenterSheet.module.css";

type SettingsCenterSheetProps = {
  onClose?: () => void;
  open?: boolean;
  section: SettingsSheetSection;
  onOpenChange?: (open: boolean) => void;
  urlMode?: "hash" | "route";
};

export function SettingsCenterPage({ section }: { section: SettingsSheetSection }) {
  return <SettingsCenterSheet key={section} open section={section} />;
}

export function SettingsCenterRouteModalPage({ section }: { section: SettingsSheetSection }) {
  const router = useRouter();
  const handleClose = useCallback(() => {
    router.back();
  }, [router]);

  // Direct `/app/settings/...` links still render this same shell. Normal app
  // entry points use `#settings/...` from AppShell so Settings can open above
  // any surface without unloading it.
  return <SettingsCenterSheet key={section} open section={section} onClose={handleClose} />;
}

export function SettingsCenterSheet({
  onClose,
  open = true,
  onOpenChange,
  section,
  urlMode = "route",
}: SettingsCenterSheetProps) {
  const router = useRouter();
  const t = useTranslations("personalization");
  const tCommon = useTranslations("common");
  const tUser = useTranslations("user");
  const authSnapshot = useAuthSnapshot();
  const profileName = useMemo(
    () => resolveUserDisplayName(authSnapshot.rawUser, tUser("notSignedIn")),
    [authSnapshot.rawUser, tUser],
  );
  const [uncontrolledSection, setUncontrolledSection] = useState<SettingsSheetSection>(section);
  const [accountMounted, setAccountMounted] = useState(section === "account");
  const activeSection = uncontrolledSection;
  const shouldRenderAccount = accountMounted || activeSection === "account";

  const handleOpenChange = useCallback((nextOpen: boolean) => {
    if (nextOpen) {
      onOpenChange?.(true);
      return;
    }

    if (onOpenChange) {
      onOpenChange(false);
      onClose?.();
      return;
    }

    if (onClose) {
      onClose();
      return;
    }
    router.push(HOME_PATH);
  }, [onClose, onOpenChange, router]);

  const handleSectionSelect = useCallback((nextSection: SettingsSheetSection) => {
    setUncontrolledSection(nextSection);
    if (nextSection === "account") {
      setAccountMounted(true);
    }

    // Settings can be opened either as a route page or as a hash overlay from
    // any product surface. Replace, instead of push, so section changes do not
    // stack up extra Back/Close steps.
    const nextUrl = urlMode === "hash"
      ? `${window.location.pathname}${window.location.search}${getSettingsSheetHash(nextSection)}`
      : getSettingsSheetPath(nextSection);
    window.history.replaceState(window.history.state, "", nextUrl);
  }, [urlMode]);

  const handleContactUs = useCallback(() => {
    safeExecute(
      () => {
        window.open(FEEDBACK_HREF, "_blank", "noopener,noreferrer");
      },
      { source: "settings-contact-us" },
    );
  }, []);

  const sectionTitle = useMemo(() => {
    switch (activeSection) {
      case "account":
        return t("account");
      case "general":
        return t("general");
      case "models":
        return t("models");
      case "personalization":
        return t("title");
    }
  }, [activeSection, t]);

  const sectionSubtitle = useMemo(() => {
    switch (activeSection) {
      case "account":
        return t("accountSubtitle");
      case "general":
        return t("generalSubtitle");
      case "models":
        return "";
      case "personalization":
        return t("subtitle");
    }
  }, [activeSection, t]);

  const sidebarItems = [
    { icon: UserRound, label: t("account"), section: "account" as const },
    { icon: SlidersHorizontal, label: t("general"), section: "general" as const },
    { icon: Database, label: t("models"), section: "models" as const },
    { icon: Shapes, label: t("title"), section: "personalization" as const },
  ];

  return (
    <AppCenteredSheet
      open={open}
      onOpenChange={handleOpenChange}
      title={tUser("settings")}
      description={sectionSubtitle}
      closeLabel={tCommon("close")}
      dialogClassName={styles.dialog}
      overlayClassName={styles.overlay}
      contentClassName={styles.sheet}
      onOpenAutoFocus={(event) => {
        event.preventDefault();
      }}
    >
        <div className={styles.header}>{tUser("settings")}</div>
        <div className={styles.body}>
          <aside className={styles.sidebar}>
            <nav className={styles.navigation} aria-label={tUser("settings")}>
              {sidebarItems.map((item) => {
                const Icon = item.icon;
                return (
                  <button type="button" key={item.section}
                    className={cn(styles.navItem, item.section === activeSection && styles.navItemActive)}
                    aria-current={item.section === activeSection ? "page" : undefined}
                    onClick={() => handleSectionSelect(item.section)}>
                    <Icon size={17} strokeWidth={1.9} aria-hidden="true" />
                    <span>{item.label}</span>
                  </button>
                );
              })}
            </nav>
            <button type="button" className={styles.helpLink} onClick={handleContactUs}>
              <Mail size={17} strokeWidth={1.9} aria-hidden="true" />
              <span>{tUser("contactUs")}</span>
            </button>
          </aside>

          <main className={styles.surface}>
            <div className={styles.content}>
              <header className={styles.contentHeader}>
                <h1>{sectionTitle}</h1>
                {sectionSubtitle ? <p>{sectionSubtitle}</p> : null}
              </header>
              {shouldRenderAccount ? (
                <div hidden={activeSection !== "account"}>
                  <AccountSettingsContent
                    profileName={profileName}
                    rawUser={authSnapshot.rawUser}
                  />
                </div>
              ) : null}
              {activeSection !== "account" ? (
                <>
                  {activeSection === "general" ? (
                    <GeneralSettingsContent />
                  ) : activeSection === "models" ? (
                    <ModelProviderSettingsContent />
                  ) : activeSection === "personalization" ? (
                    <PersonalizationSettingsContent
                      profileName={profileName}
                      rawUser={authSnapshot.rawUser}
                    />
                  ) : (
                    <section className="mt-6 rounded-[8px] border border-border bg-card px-5 py-5">
                      <p className="text-[14px] leading-6 text-muted-foreground">{sectionSubtitle}</p>
                    </section>
                  )}
                </>
              ) : null}
            </div>
          </main>
        </div>
    </AppCenteredSheet>
  );
}
