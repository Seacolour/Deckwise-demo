"use client";

import { useCallback, useEffect, useMemo, useState, type KeyboardEvent } from "react";
import { useTranslations } from "next-intl";
import {
  Check,
  Loader2,
  UserRound,
} from "lucide-react";
import ConfirmDropdown from "@/src/components/_shared/components/ConfirmDropdown";
import { deleteCurrentUser as deleteCurrentUserRequest } from "@/src/lib/api/generated/user-account";
import { saveCurrentUserName } from "@/src/lib/api/userPersonalization";
import {
  extractPersistedAuthUser,
} from "@/src/lib/auth/userProfile";
import { logoutCurrentSession } from "@/src/lib/auth/logout";
import { resolveLogoutRedirectPath } from "@/src/lib/auth/logoutRedirect";
import type { UserDTO } from "@/src/lib/types/all/userDTO";
import { isRecord } from "@/src/lib/types/guards";
import {
  LOGOUT_REDIRECT_STORAGE_KEY,
  saveAuth,
} from "@/src/stores/auth";
import { SettingsButton } from "@/src/components/features/settings/SettingsButton";

type AccountSettingsContentProps = {
  profileName: string;
  rawUser: unknown;
};

function readFirstString(record: Record<string, unknown> | null | undefined, keys: string[]): string | null {
  if (!record) {
    return null;
  }

  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string") {
      const trimmed = value.trim();
      if (trimmed) {
        return trimmed;
      }
    }
    if (typeof value === "number" && Number.isFinite(value)) {
      return String(value);
    }
  }

  return null;
}

function mergePersistedAuthUser(rawUser: unknown, updatedUser: UserDTO | null) {
  if (!updatedUser || !isRecord(rawUser) || !isRecord(updatedUser)) {
    return;
  }

  if (isRecord(rawUser.user)) {
    saveAuth({
      ...rawUser,
      user: {
        ...rawUser.user,
        ...updatedUser,
      },
    });
    return;
  }

  saveAuth({
    ...rawUser,
    ...updatedUser,
  });
}

export function AccountSettingsContent({
  profileName,
  rawUser,
}: AccountSettingsContentProps) {
  const tCommon = useTranslations("common");
  const tUser = useTranslations("user");
  const user = useMemo(() => extractPersistedAuthUser(rawUser), [rawUser]);
  const [fullName, setFullName] = useState(profileName);
  const [savedName, setSavedName] = useState(profileName);
  const [nameSaving, setNameSaving] = useState(false);
  const [nameStatus, setNameStatus] = useState<"saved" | null>(null);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteAccountConfirmOpen, setDeleteAccountConfirmOpen] = useState(false);

  useEffect(() => {
    setFullName(profileName);
    setSavedName(profileName);
  }, [profileName]);

  const email = readFirstString(user, ["email"]);

  const saveNameIfChanged = useCallback(async () => {
    const trimmedName = fullName.trim();
    if (!trimmedName || trimmedName === savedName || nameSaving) {
      return;
    }

    setNameSaving(true);
    setNameStatus(null);
    setAccountError(null);
    try {
      const updatedUser = await saveCurrentUserName(trimmedName);
      mergePersistedAuthUser(rawUser, updatedUser);
      setSavedName(trimmedName);
      setNameStatus("saved");
    } catch {
      setAccountError(tUser("accountNameSaveFailed"));
    } finally {
      setNameSaving(false);
    }
  }, [fullName, nameSaving, rawUser, savedName, tUser]);

  const handleNameKeyDown = useCallback((event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.currentTarget.blur();
    }
  }, []);

  const handleLogout = useCallback(() => {
    const redirectPath = resolveLogoutRedirectPath();
    try {
      sessionStorage.setItem(LOGOUT_REDIRECT_STORAGE_KEY, redirectPath);
    } catch {
      // best effort
    }
    void logoutCurrentSession().finally(() => {
      window.location.replace(redirectPath);
    });
  }, []);

  const handleDeleteAccount = useCallback(async () => {
    if (deleting) {
      return;
    }

    setDeleting(true);
    setAccountError(null);
    try {
      await deleteCurrentUserRequest();
      const redirectPath = resolveLogoutRedirectPath();
      try {
        sessionStorage.setItem(LOGOUT_REDIRECT_STORAGE_KEY, redirectPath);
      } catch {
        // best effort
      }
      await logoutCurrentSession();
      window.location.replace(redirectPath);
    } catch {
      setDeleting(false);
      setAccountError(tUser("deleteAccountFailed"));
    }
  }, [deleting, tUser]);

  return (
    <div className="mt-1 space-y-4">
      <section className="rounded-2xl border border-border bg-card p-5">
        <div className="flex flex-wrap items-center gap-4">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground" aria-hidden="true">
            <UserRound className="size-6" strokeWidth={1.8} />
          </span>

          <label className="block min-w-[160px] flex-1">
            <span className="mb-1.5 block text-[13px] font-medium leading-5 text-muted-foreground">{tUser("fullName")}</span>
            <div className="relative">
              <input
                value={fullName}
                onBlur={() => void saveNameIfChanged()}
                onChange={(event) => setFullName(event.target.value)}
                onKeyDown={handleNameKeyDown}
                className="h-9 w-full max-w-[320px] rounded-[8px] border-0 bg-muted px-3 pr-9 text-[14px] text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
              />
              {nameSaving ? (
                <Loader2 className="absolute right-3 top-1/2 size-4 -translate-y-1/2 animate-spin text-muted-foreground" strokeWidth={2} />
              ) : nameStatus === "saved" ? (
                <Check className="absolute right-3 top-1/2 size-4 -translate-y-1/2 text-emerald-700 dark:text-emerald-300" strokeWidth={2.1} />
              ) : null}
            </div>
          </label>

          <SettingsButton
            className="ml-auto"
            size="sm"
            aria-label={tUser("logout")}
            title={tUser("logout")}
            onClick={handleLogout}
          >
            {tUser("logout")}
          </SettingsButton>
        </div>

        {accountError ? (
          <p className="mt-3 text-[13px] leading-5 text-destructive">{accountError}</p>
        ) : null}

        <div className="mt-5 border-t border-border pt-4">
          <div className="text-[14px] font-semibold leading-5 text-foreground">{tUser("email")}</div>
          <div className="mt-1 text-[13px] leading-5 text-muted-foreground">{email ?? tUser("notSignedIn")}</div>
        </div>
      </section>

      <section className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-border bg-card p-5">
        <div className="min-w-0">
          <div className="text-[14px] font-semibold leading-5 text-foreground">{tUser("deleteAccount")}</div>
          <p className="mt-1 text-[13px] leading-5 text-muted-foreground">{tUser("deleteAccountConfirmMessage")}</p>
        </div>
        <ConfirmDropdown
          open={deleteAccountConfirmOpen}
          onOpenChange={setDeleteAccountConfirmOpen}
          trigger={
            <SettingsButton
              size="compact"
              variant="danger"
              disabled={deleting}
            >
              {deleting ? <Loader2 className="size-4 animate-spin" strokeWidth={2} /> : null}
              {tUser("deleteAccount")}
            </SettingsButton>
          }
          title={tUser("deleteAccountConfirmTitle")}
          description={tUser("deleteAccountConfirmMessage")}
          confirmText={tUser("deleteAccountConfirmAction")}
          cancelText={tCommon("cancel")}
          variant="danger"
          contentClassName="settings-account-delete-confirm"
          onConfirm={() => {
            void handleDeleteAccount();
          }}
          contentProps={{
            align: "end",
            sideOffset: 8,
          }}
        />
      </section>
    </div>
  );
}
