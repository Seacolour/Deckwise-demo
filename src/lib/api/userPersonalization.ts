import {
  createMemory,
  createRule,
  deleteMemory,
  deleteRule,
  listMemories,
  listRules,
  toggleRule,
  updateCurrentUser,
  updateMemory,
  updateRule,
} from "@/src/lib/api/generated/user-account";
import { unwrapGeneratedBoolean, unwrapGeneratedPayload } from "@/src/lib/api/generatedCompat";
import { runSharedRead } from "@/src/lib/api/sharedReads";
import type { UserDTO, UserMemoryDTO, UserRuleDTO } from "@/src/lib/types/all";

export const PROFILE_MEMORY_TITLE = "Deckwise personalization profile";
export const CUSTOM_INSTRUCTIONS_PREFIX = "User custom instructions:";
const USER_PERSONALIZATION_READ_KEY = "user-personalization:snapshot";
const USER_PERSONALIZATION_WARM_CACHE_MS = 500;

export type PersonalizationProfileFields = {
  about: string;
  occupation: string;
};

export type UserPersonalizationSnapshot = {
  memories: UserMemoryDTO[];
  rules: UserRuleDTO[];
};

export type UserPersonalizationReadOptions = {
  force?: boolean;
};

function personalizationReadKey(options?: UserPersonalizationReadOptions): string {
  return options?.force ? `${USER_PERSONALIZATION_READ_KEY}:force` : USER_PERSONALIZATION_READ_KEY;
}

function personalizationCacheMs(options?: UserPersonalizationReadOptions): number {
  return options?.force ? 0 : USER_PERSONALIZATION_WARM_CACHE_MS;
}

export async function fetchUserPersonalization(
  options?: UserPersonalizationReadOptions,
): Promise<UserPersonalizationSnapshot> {
  return runSharedRead(
    personalizationReadKey(options),
    async (signal) => {
      const [memoriesResponse, rulesResponse] = await Promise.all([
        listMemories({ signal }),
        listRules({ signal }),
      ]);

      return {
        memories: unwrapGeneratedPayload<UserMemoryDTO[]>(memoriesResponse) ?? [],
        rules: unwrapGeneratedPayload<UserRuleDTO[]>(rulesResponse) ?? [],
      };
    },
    { cacheMs: personalizationCacheMs(options) },
  );
}

export async function saveCurrentUserName(name: string): Promise<UserDTO | null> {
  const response = await updateCurrentUser({ name });
  return unwrapGeneratedPayload<UserDTO | null>(response);
}

export async function saveProfileMemory(
  existing: UserMemoryDTO | null | undefined,
  profile: PersonalizationProfileFields,
): Promise<UserMemoryDTO | null> {
  const content = formatProfileMemoryContent(profile);
  const id = existing?.id;

  if (!content) {
    if (id) {
      await deleteUserMemory(id);
    }
    return null;
  }

  if (id) {
    await updateMemory(id, {
      content,
      title: PROFILE_MEMORY_TITLE,
    });
    return {
      ...existing,
      content,
      title: PROFILE_MEMORY_TITLE,
    };
  }

  const response = await createMemory({
    content,
    title: PROFILE_MEMORY_TITLE,
  });
  return unwrapGeneratedPayload<UserMemoryDTO | null>(response);
}

export async function saveCustomInstructionsRule(
  existing: UserRuleDTO | null | undefined,
  instructions: string,
): Promise<UserRuleDTO | null> {
  const content = formatCustomInstructionsRuleContent(instructions);
  const id = existing?.id;

  if (!content) {
    if (id) {
      await deleteUserRule(id);
    }
    return null;
  }

  if (id) {
    await updateRuleContent(id, content);
    return {
      ...existing,
      content,
      enabled: existing.enabled ?? true,
    };
  }

  const response = await createRule({ content });
  return unwrapGeneratedPayload<UserRuleDTO | null>(response);
}

export async function createUserMemory(title: string, content: string): Promise<UserMemoryDTO | null> {
  const response = await createMemory({
    content: content.trim(),
    title: title.trim(),
  });
  return unwrapGeneratedPayload<UserMemoryDTO | null>(response);
}

export async function deleteUserMemory(memoryId: string): Promise<boolean> {
  const response = await deleteMemory(memoryId);
  return unwrapGeneratedBoolean(response);
}

export async function createUserRule(content: string): Promise<UserRuleDTO | null> {
  const response = await createRule({ content: content.trim() });
  return unwrapGeneratedPayload<UserRuleDTO | null>(response);
}

export async function updateRuleEnabled(ruleId: string, enabled: boolean): Promise<boolean> {
  const response = await toggleRule(ruleId, { enabled });
  return unwrapGeneratedBoolean(response);
}

export async function deleteUserRule(ruleId: string): Promise<boolean> {
  const response = await deleteRule(ruleId);
  return unwrapGeneratedBoolean(response);
}

export function findProfileMemory(memories: readonly UserMemoryDTO[]): UserMemoryDTO | null {
  return memories.find(isProfileMemory) ?? null;
}

export function findCustomInstructionsRule(rules: readonly UserRuleDTO[]): UserRuleDTO | null {
  return rules.find(isCustomInstructionsRule) ?? null;
}

export function isProfileMemory(memory: UserMemoryDTO): boolean {
  return memory.title === PROFILE_MEMORY_TITLE;
}

export function isCustomInstructionsRule(rule: UserRuleDTO): boolean {
  return typeof rule.content === "string" && rule.content.startsWith(CUSTOM_INSTRUCTIONS_PREFIX);
}

export function parseProfileMemoryContent(content: string | null | undefined): PersonalizationProfileFields {
  const normalized = content ?? "";
  const occupationMatch = normalized.match(/^Occupation:\s*(.*)$/m);
  const aboutMatch = normalized.match(/^About:\s*\n?([\s\S]*)$/m);

  return {
    occupation: occupationMatch?.[1]?.trim() ?? "",
    about: aboutMatch?.[1]?.trim() ?? "",
  };
}

export function formatProfileMemoryContent(profile: PersonalizationProfileFields): string {
  const occupation = profile.occupation.trim();
  const about = profile.about.trim();
  const sections: string[] = [];

  if (occupation) {
    sections.push(`Occupation: ${occupation}`);
  }

  if (about) {
    sections.push(`About:\n${about}`);
  }

  return sections.join("\n\n");
}

export function parseCustomInstructionsRuleContent(content: string | null | undefined): string {
  if (!content?.startsWith(CUSTOM_INSTRUCTIONS_PREFIX)) {
    return "";
  }

  return content.slice(CUSTOM_INSTRUCTIONS_PREFIX.length).trim();
}

export function formatCustomInstructionsRuleContent(instructions: string): string {
  const trimmed = instructions.trim();
  return trimmed ? `${CUSTOM_INSTRUCTIONS_PREFIX}\n${trimmed}` : "";
}

async function updateRuleContent(ruleId: string, content: string): Promise<boolean> {
  const response = await updateRule(ruleId, { content });
  return unwrapGeneratedBoolean(response);
}
