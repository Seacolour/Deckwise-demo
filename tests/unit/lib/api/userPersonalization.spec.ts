import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearSharedReadState } from "@/src/lib/api/sharedReads";
import type { UserMemoryDTO, UserRuleDTO } from "@/src/lib/types/all";

const mocks = vi.hoisted(() => ({
  createMemory: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  createRule: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  deleteMemory: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  deleteRule: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  listMemories: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  listRules: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  toggleRule: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  updateCurrentUser: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  updateMemory: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
  updateRule: vi.fn<(...args: unknown[]) => Promise<unknown>>(),
}));

vi.mock("@/src/lib/api/generated/user-account", () => ({
  createMemory: (...args: unknown[]) => mocks.createMemory(...args),
  createRule: (...args: unknown[]) => mocks.createRule(...args),
  deleteMemory: (...args: unknown[]) => mocks.deleteMemory(...args),
  deleteRule: (...args: unknown[]) => mocks.deleteRule(...args),
  listMemories: (...args: unknown[]) => mocks.listMemories(...args),
  listRules: (...args: unknown[]) => mocks.listRules(...args),
  toggleRule: (...args: unknown[]) => mocks.toggleRule(...args),
  updateCurrentUser: (...args: unknown[]) => mocks.updateCurrentUser(...args),
  updateMemory: (...args: unknown[]) => mocks.updateMemory(...args),
  updateRule: (...args: unknown[]) => mocks.updateRule(...args),
}));

import {
  CUSTOM_INSTRUCTIONS_PREFIX,
  PROFILE_MEMORY_TITLE,
  fetchUserPersonalization,
  findCustomInstructionsRule,
  findProfileMemory,
  formatCustomInstructionsRuleContent,
  formatProfileMemoryContent,
  isCustomInstructionsRule,
  isProfileMemory,
  parseCustomInstructionsRuleContent,
  parseProfileMemoryContent,
} from "@/src/lib/api/userPersonalization";

describe("userPersonalization", () => {
  beforeEach(() => {
    clearSharedReadState();
    for (const mock of Object.values(mocks)) {
      mock.mockReset();
    }
  });

  it("maps profile fields to the backend memory content shape", () => {
    const content = formatProfileMemoryContent({
      about: "I build internal tools.",
      occupation: "Backend engineer",
    });

    expect(content).toBe("Occupation: Backend engineer\n\nAbout:\nI build internal tools.");
    expect(parseProfileMemoryContent(content)).toEqual({
      about: "I build internal tools.",
      occupation: "Backend engineer",
    });
  });

  it("finds reserved profile memory and custom-instruction rule without matching normal items", () => {
    const memories: UserMemoryDTO[] = [
      { id: "1", title: "Project preference", content: "Use concise decks." },
      { id: "2", title: PROFILE_MEMORY_TITLE, content: "Occupation: PM" },
    ];
    const rules: UserRuleDTO[] = [
      { id: "1", content: "Always answer in English.", enabled: true },
      { id: "2", content: `${CUSTOM_INSTRUCTIONS_PREFIX}\nKeep replies short.`, enabled: true },
    ];

    expect(findProfileMemory(memories)?.id).toBe("2");
    expect(findCustomInstructionsRule(rules)?.id).toBe("2");
    expect(isProfileMemory(memories[0])).toBe(false);
    expect(isCustomInstructionsRule(rules[0])).toBe(false);
  });

  it("maps custom instructions to a stable user-rule prefix", () => {
    const content = formatCustomInstructionsRuleContent(" Prefer direct answers. ");

    expect(content).toBe(`${CUSTOM_INSTRUCTIONS_PREFIX}\nPrefer direct answers.`);
    expect(parseCustomInstructionsRuleContent(content)).toBe("Prefer direct answers.");
    expect(formatCustomInstructionsRuleContent("   ")).toBe("");
  });

  it("shares duplicate personalization reads across immediate remounts", async () => {
    const memories: UserMemoryDTO[] = [
      { id: "memory-1", title: "Project preference", content: "Use concise decks." },
    ];
    const rules: UserRuleDTO[] = [
      { id: "rule-1", content: "Prefer direct answers.", enabled: true },
    ];
    mocks.listMemories.mockResolvedValue({ data: { data: memories } });
    mocks.listRules.mockResolvedValue({ data: { data: rules } });

    const first = await fetchUserPersonalization();
    const second = await fetchUserPersonalization();

    expect(mocks.listMemories).toHaveBeenCalledTimes(1);
    expect(mocks.listRules).toHaveBeenCalledTimes(1);
    expect(first).toEqual({ memories, rules });
    expect(second).toEqual({ memories, rules });
    const memoryOptions = mocks.listMemories.mock.calls[0]?.[0];
    const ruleOptions = mocks.listRules.mock.calls[0]?.[0];
    expect(memoryOptions && typeof memoryOptions === "object" && "signal" in memoryOptions).toBe(true);
    expect(ruleOptions && typeof ruleOptions === "object" && "signal" in ruleOptions).toBe(true);
  });

  it("can force a fresh personalization read after a mutation", async () => {
    mocks.listMemories
      .mockResolvedValueOnce({ data: { data: [{ id: "old-memory", title: "Old", content: "Old content" }] } })
      .mockResolvedValueOnce({ data: { data: [{ id: "new-memory", title: "New", content: "New content" }] } });
    mocks.listRules
      .mockResolvedValueOnce({ data: { data: [{ id: "old-rule", content: "Old rule", enabled: true }] } })
      .mockResolvedValueOnce({ data: { data: [{ id: "new-rule", content: "New rule", enabled: true }] } });

    const first = await fetchUserPersonalization();
    const forced = await fetchUserPersonalization({ force: true });

    expect(mocks.listMemories).toHaveBeenCalledTimes(2);
    expect(mocks.listRules).toHaveBeenCalledTimes(2);
    expect(first.memories[0]?.id).toBe("old-memory");
    expect(forced.memories[0]?.id).toBe("new-memory");
    expect(forced.rules[0]?.id).toBe("new-rule");
  });
});
