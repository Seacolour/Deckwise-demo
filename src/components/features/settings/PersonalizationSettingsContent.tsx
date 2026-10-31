"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { Check, Loader2, Plus, Save, Trash2 } from "lucide-react";
import {
  createUserMemory,
  createUserRule,
  deleteUserMemory,
  deleteUserRule,
  fetchUserPersonalization,
  findCustomInstructionsRule,
  findProfileMemory,
  isCustomInstructionsRule,
  isProfileMemory,
  parseCustomInstructionsRuleContent,
  parseProfileMemoryContent,
  saveCurrentUserName,
  saveCustomInstructionsRule,
  saveProfileMemory,
  updateRuleEnabled,
} from "@/src/lib/api/userPersonalization";
import { isRecord } from "@/src/lib/types/guards";
import type { UserDTO, UserMemoryDTO, UserRuleDTO } from "@/src/lib/types/all";
import { saveAuth } from "@/src/stores/auth";
import { cn } from "@/src/lib/utils";
import { SettingsButton } from "@/src/components/features/settings/SettingsButton";

type PersonalizationTab = "profile" | "knowledge";

type PersonalizationSettingsContentProps = {
  profileName: string;
  rawUser: unknown;
};

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
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

function upsertMemory(memories: UserMemoryDTO[], updated: UserMemoryDTO | null, previous?: UserMemoryDTO | null) {
  const previousId = previous?.id;
  if (!updated) {
    return previousId ? memories.filter((memory) => memory.id !== previousId) : memories;
  }

  if (!updated.id) {
    return memories;
  }

  const replaced = memories.map((memory) => (memory.id === updated.id || memory.id === previousId ? updated : memory));
  return replaced.some((memory) => memory.id === updated.id) ? replaced : [updated, ...memories];
}

function upsertRule(rules: UserRuleDTO[], updated: UserRuleDTO | null, previous?: UserRuleDTO | null) {
  const previousId = previous?.id;
  if (!updated) {
    return previousId ? rules.filter((rule) => rule.id !== previousId) : rules;
  }

  if (!updated.id) {
    return rules;
  }

  const replaced = rules.map((rule) => (rule.id === updated.id || rule.id === previousId ? updated : rule));
  return replaced.some((rule) => rule.id === updated.id) ? replaced : [updated, ...rules];
}

function sourceLabel(source: string | undefined, t: ReturnType<typeof useTranslations>) {
  if (source === "ai") {
    return t("sourceAi");
  }
  if (source === "user") {
    return t("sourceUser");
  }
  return source || t("sourceUser");
}

export function PersonalizationSettingsContent({
  profileName,
  rawUser,
}: PersonalizationSettingsContentProps) {
  const t = useTranslations("personalization");
  const tCommon = useTranslations("common");
  const loadFailedMessage = t("loadFailed");
  const [tab, setTab] = useState<PersonalizationTab>("profile");
  const [nickname, setNickname] = useState(profileName);
  const [occupation, setOccupation] = useState("");
  const [about, setAbout] = useState("");
  const [instructions, setInstructions] = useState("");
  const [memories, setMemories] = useState<UserMemoryDTO[]>([]);
  const [rules, setRules] = useState<UserRuleDTO[]>([]);
  const [memoryTitle, setMemoryTitle] = useState("");
  const [memoryContent, setMemoryContent] = useState("");
  const [ruleContent, setRuleContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [workingId, setWorkingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    setNickname(profileName);
  }, [profileName]);

  const profileMemory = useMemo(() => findProfileMemory(memories), [memories]);
  const customInstructionsRule = useMemo(() => findCustomInstructionsRule(rules), [rules]);
  const visibleMemories = useMemo(() => memories.filter((memory) => !isProfileMemory(memory)), [memories]);
  const visibleRules = useMemo(() => rules.filter((rule) => !isCustomInstructionsRule(rule)), [rules]);

  useEffect(() => {
    let cancelled = false;

    async function loadPersonalization() {
      setLoading(true);
      setError(null);
      try {
        const snapshot = await fetchUserPersonalization();
        if (cancelled) return;
        setMemories(snapshot.memories);
        setRules(snapshot.rules);

        const loadedProfile = findProfileMemory(snapshot.memories);
        const loadedProfileFields = parseProfileMemoryContent(loadedProfile?.content);
        setOccupation(loadedProfileFields.occupation);
        setAbout(loadedProfileFields.about);

        const loadedInstructions = findCustomInstructionsRule(snapshot.rules);
        setInstructions(parseCustomInstructionsRuleContent(loadedInstructions?.content));
      } catch (loadError) {
        if (!cancelled) {
          setError(errorMessage(loadError, loadFailedMessage));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadPersonalization();

    return () => {
      cancelled = true;
    };
  }, [loadFailedMessage]);

  const saveProfile = useCallback(async () => {
    setSaving(true);
    setError(null);
    setStatus(null);
    try {
      const trimmedNickname = nickname.trim();
      if (trimmedNickname && trimmedNickname !== profileName) {
        const updatedUser = await saveCurrentUserName(trimmedNickname);
        mergePersistedAuthUser(rawUser, updatedUser);
      }

      const savedMemory = await saveProfileMemory(profileMemory, {
        about,
        occupation,
      });
      const savedRule = await saveCustomInstructionsRule(customInstructionsRule, instructions);

      setMemories((current) => upsertMemory(current, savedMemory, profileMemory));
      setRules((current) => upsertRule(current, savedRule, customInstructionsRule));
      setStatus(t("savedStatus"));
    } catch (saveError) {
      setError(errorMessage(saveError, t("saveFailed")));
    } finally {
      setSaving(false);
    }
  }, [about, customInstructionsRule, instructions, nickname, occupation, profileMemory, profileName, rawUser, t]);

  const addMemory = useCallback(async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const title = memoryTitle.trim();
    const content = memoryContent.trim();
    if (!title || !content) return;

    setWorkingId("new-memory");
    setError(null);
    setStatus(null);
    try {
      const created = await createUserMemory(title, content);
      if (created) {
        setMemories((current) => [created, ...current]);
        setMemoryTitle("");
        setMemoryContent("");
      }
    } catch (createError) {
      setError(errorMessage(createError, t("saveFailed")));
    } finally {
      setWorkingId(null);
    }
  }, [memoryContent, memoryTitle, t]);

  const addRule = useCallback(async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const content = ruleContent.trim();
    if (!content) return;

    setWorkingId("new-rule");
    setError(null);
    setStatus(null);
    try {
      const created = await createUserRule(content);
      if (created) {
        setRules((current) => [created, ...current]);
        setRuleContent("");
      }
    } catch (createError) {
      setError(errorMessage(createError, t("saveFailed")));
    } finally {
      setWorkingId(null);
    }
  }, [ruleContent, t]);

  const removeMemory = useCallback(async (memory: UserMemoryDTO) => {
    if (!memory.id) return;
    setWorkingId(memory.id);
    setError(null);
    try {
      await deleteUserMemory(memory.id);
      setMemories((current) => current.filter((item) => item.id !== memory.id));
    } catch (deleteError) {
      setError(errorMessage(deleteError, t("saveFailed")));
    } finally {
      setWorkingId(null);
    }
  }, [t]);

  const removeRule = useCallback(async (rule: UserRuleDTO) => {
    if (!rule.id) return;
    setWorkingId(rule.id);
    setError(null);
    try {
      await deleteUserRule(rule.id);
      setRules((current) => current.filter((item) => item.id !== rule.id));
    } catch (deleteError) {
      setError(errorMessage(deleteError, t("saveFailed")));
    } finally {
      setWorkingId(null);
    }
  }, [t]);

  const toggleVisibleRule = useCallback(async (rule: UserRuleDTO) => {
    if (!rule.id) return;
    const nextEnabled = !(rule.enabled ?? true);
    setWorkingId(rule.id);
    setError(null);
    try {
      await updateRuleEnabled(rule.id, nextEnabled);
      setRules((current) => current.map((item) => (
        item.id === rule.id ? { ...item, enabled: nextEnabled } : item
      )));
    } catch (toggleError) {
      setError(errorMessage(toggleError, t("saveFailed")));
    } finally {
      setWorkingId(null);
    }
  }, [t]);

  return (
    <>
      <div className="mt-6">
        <div className="flex gap-6">
          {(["profile", "knowledge"] as const).map((value) => (
            <button
              type="button"
              className={cn(
                "relative pb-3 text-[15px] font-medium leading-5 text-muted-foreground",
                tab === value && "text-foreground",
              )}
              key={value}
              onClick={() => setTab(value)}
            >
              {value === "profile" ? t("profileTab") : t("knowledgeTab")}
              {tab === value ? <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-foreground" /> : null}
            </button>
          ))}
        </div>
      </div>

      {error ? (
        <div className="mt-5 rounded-[8px] border border-destructive/30 bg-destructive/10 px-4 py-3 text-[14px] leading-5 text-destructive">
          {error}
        </div>
      ) : null}

      {status ? (
        <div className="mt-5 flex items-center gap-2 rounded-[8px] border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-[14px] leading-5 text-emerald-700 dark:text-emerald-300">
          <Check className="size-4" strokeWidth={2.2} />
          {status}
        </div>
      ) : null}

      {tab === "profile" ? (
        <>
          <section className="mt-6 grid max-w-[680px] grid-cols-[minmax(0,280px)_minmax(0,280px)] gap-5">
            <label className="block">
              <span className="mb-1.5 block text-[13px] font-medium leading-5 text-muted-foreground">{t("nickname")}</span>
              <input
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                placeholder={t("nicknamePlaceholder")}
                className="h-9 w-full rounded-[8px] border-0 bg-muted px-3 text-[14px] text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
              />
            </label>
            <label className="block">
              <span className="mb-1.5 block text-[13px] font-medium leading-5 text-muted-foreground">{t("occupation")}</span>
              <input
                value={occupation}
                onChange={(event) => setOccupation(event.target.value)}
                placeholder={t("occupationPlaceholder")}
                className="h-9 w-full rounded-[8px] border-0 bg-muted px-3 text-[14px] text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
              />
            </label>
          </section>

          <label className="mt-6 block max-w-[680px]">
            <span className="mb-1.5 block text-[13px] font-medium leading-5 text-muted-foreground">{t("moreAboutYou")}</span>
            <textarea
              value={about}
              onChange={(event) => setAbout(event.target.value)}
              placeholder={t("aboutPlaceholder")}
              className="min-h-[116px] w-full resize-none rounded-[8px] border-0 bg-muted px-3 py-2.5 text-[14px] leading-6 text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
            />
          </label>
          <p className="mt-3 text-[13px] leading-5 text-muted-foreground">{t("profileHelp")}</p>

          <div className="my-7 h-px max-w-[680px] bg-border" />

          <label className="block max-w-[680px]">
            <span className="mb-1.5 block text-[13px] font-medium leading-5 text-muted-foreground">{t("customInstructions")}</span>
            <textarea
              value={instructions}
              onChange={(event) => setInstructions(event.target.value)}
              placeholder={t("instructionsPlaceholder")}
              className="min-h-[116px] w-full resize-none rounded-[8px] border-0 bg-muted px-3 py-2.5 text-[14px] leading-6 text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
            />
          </label>

          <SettingsButton
            className="mt-8"
            disabled={loading || saving}
            onClick={saveProfile}
          >
            {saving ? <Loader2 className="size-4 animate-spin" strokeWidth={2.1} /> : <Save className="size-4" strokeWidth={2.1} />}
            {saving ? tCommon("saving") : t("saveProfile")}
          </SettingsButton>
        </>
      ) : (
        <div className="mt-6 space-y-8">
          {loading ? (
            <div className="flex items-center gap-2 text-[14px] leading-5 text-muted-foreground">
              <Loader2 className="size-4 animate-spin" strokeWidth={2.1} />
              {tCommon("loading")}
            </div>
          ) : null}

          <section>
            <h2 className="text-[16px] font-semibold leading-6 text-foreground">{t("memoriesHeading")}</h2>
            <form className="mt-3 grid gap-3" onSubmit={addMemory}>
              <input
                value={memoryTitle}
                onChange={(event) => setMemoryTitle(event.target.value)}
                placeholder={t("memoryTitlePlaceholder")}
                className="h-10 rounded-[8px] border-0 bg-muted px-3 text-[14px] text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
              />
              <textarea
                value={memoryContent}
                onChange={(event) => setMemoryContent(event.target.value)}
                placeholder={t("memoryContentPlaceholder")}
                className="min-h-[88px] resize-none rounded-[8px] border-0 bg-muted px-3 py-2.5 text-[14px] leading-6 text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
              />
              <SettingsButton
                type="submit"
                className="w-fit"
                disabled={workingId === "new-memory" || !memoryTitle.trim() || !memoryContent.trim()}
              >
                {workingId === "new-memory" ? <Loader2 className="size-4 animate-spin" strokeWidth={2.1} /> : <Plus className="size-4" strokeWidth={2.1} />}
                {t("addMemory")}
              </SettingsButton>
            </form>

            <div className="mt-4 space-y-3">
              {visibleMemories.length ? visibleMemories.map((memory) => (
                <article className="rounded-[8px] border border-border bg-card px-4 py-3" key={memory.id ?? `${memory.title}:${memory.content}`}>
                  <div className="flex items-start gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="text-[14px] font-semibold leading-5 text-foreground">{memory.title}</div>
                      <p className="mt-1 whitespace-pre-wrap text-[14px] leading-6 text-muted-foreground">{memory.content}</p>
                      <div className="mt-2 text-[12px] leading-4 text-muted-foreground">{sourceLabel(memory.source, t)}</div>
                    </div>
                    <SettingsButton
                      size="icon"
                      variant="ghostIcon"
                      disabled={workingId === memory.id}
                      aria-label={tCommon("delete")}
                      onClick={() => removeMemory(memory)}
                    >
                      {workingId === memory.id ? <Loader2 className="size-4 animate-spin" strokeWidth={2.1} /> : <Trash2 className="size-4" strokeWidth={2.1} />}
                    </SettingsButton>
                  </div>
                </article>
              )) : (
                <p className="text-[14px] leading-5 text-muted-foreground">{t("emptyMemories")}</p>
              )}
            </div>
          </section>

          <section>
            <h2 className="text-[16px] font-semibold leading-6 text-foreground">{t("rulesHeading")}</h2>
            <form className="mt-3 grid gap-3" onSubmit={addRule}>
              <textarea
                value={ruleContent}
                onChange={(event) => setRuleContent(event.target.value)}
                placeholder={t("ruleContentPlaceholder")}
                className="min-h-[88px] resize-none rounded-[8px] border-0 bg-muted px-3 py-2.5 text-[14px] leading-6 text-foreground outline-none placeholder:text-muted-foreground/55 focus:bg-card focus:ring-2 focus:ring-ring/35"
              />
              <SettingsButton
                type="submit"
                className="w-fit"
                disabled={workingId === "new-rule" || !ruleContent.trim()}
              >
                {workingId === "new-rule" ? <Loader2 className="size-4 animate-spin" strokeWidth={2.1} /> : <Plus className="size-4" strokeWidth={2.1} />}
                {t("addRule")}
              </SettingsButton>
            </form>

            <div className="mt-4 space-y-3">
              {visibleRules.length ? visibleRules.map((rule) => {
                const enabled = rule.enabled ?? true;
                return (
                  <article className="rounded-[8px] border border-border bg-card px-4 py-3" key={rule.id ?? rule.content}>
                    <div className="flex items-start gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="whitespace-pre-wrap text-[14px] leading-6 text-foreground">{rule.content}</p>
                        <div className="mt-2 flex gap-2 text-[12px] leading-4 text-muted-foreground">
                          <span>{enabled ? t("enabled") : t("disabled")}</span>
                          <span>{sourceLabel(rule.source, t)}</span>
                        </div>
                      </div>
                      <SettingsButton
                        size="sm"
                        variant="toggle"
                        data-state={enabled ? "on" : "off"}
                        disabled={workingId === rule.id}
                        onClick={() => toggleVisibleRule(rule)}
                      >
                        {enabled ? t("enabled") : t("disabled")}
                      </SettingsButton>
                      <SettingsButton
                        size="icon"
                        variant="ghostIcon"
                        disabled={workingId === rule.id}
                        aria-label={tCommon("delete")}
                        onClick={() => removeRule(rule)}
                      >
                        {workingId === rule.id ? <Loader2 className="size-4 animate-spin" strokeWidth={2.1} /> : <Trash2 className="size-4" strokeWidth={2.1} />}
                      </SettingsButton>
                    </div>
                  </article>
                );
              }) : (
                <p className="text-[14px] leading-5 text-muted-foreground">{t("emptyRules")}</p>
              )}
            </div>
          </section>
        </div>
      )}
    </>
  );
}
