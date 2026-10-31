import { describe, expect, it } from "vitest";
import {
  appendAssistantText,
  appendAssistantToolEvent,
  parseWorkbenchToolEvent,
  settleAssistant,
  toolResultStatus,
} from "@/src/features/workbench/conversationEvents";
import type { ConversationEntry } from "@/src/features/workbench/conversationTypes";

describe("workbench conversation stream", () => {
  const initial: ConversationEntry[] = [{ id: "a", kind: "assistant", parts: [], pending: true }];

  it("accepts backend tool envelopes and ignores unrelated metadata", () => {
    expect(parseWorkbenchToolEvent(JSON.stringify({ type: "tool", seq: 4, payload: {
      phase: "call", call_id: "call-1", name: "file_list", arguments_json: "{}",
    } }))).toEqual({ phase: "call", callId: "call-1", name: "file_list", argumentsRaw: "{}", resultRaw: null });
    expect(parseWorkbenchToolEvent(JSON.stringify({ type: "metadata", payload: { phase: "call", name: "file_list" } }))).toBeNull();
    expect(parseWorkbenchToolEvent("not json")).toBeNull();
  });

  it("keeps text around tools in order and merges the result by call ID", () => {
    const call = { phase: "call" as const, callId: "call-1", name: "file_list", argumentsRaw: "{}", resultRaw: null };
    const result = { phase: "result" as const, callId: "call-1", name: "file_list", argumentsRaw: null, resultRaw: '{"files":[]}' };
    const withFirstText = appendAssistantText(initial, "a", "先查看");
    const withCall = appendAssistantToolEvent(withFirstText, "a", call);
    const withResult = appendAssistantToolEvent(withCall, "a", result);
    const complete = appendAssistantText(withResult, "a", "已完成");
    expect(complete[0]).toEqual({ id: "a", kind: "assistant", pending: true, parts: [
      { id: "a-text-0", kind: "text", text: "先查看" },
      { id: "a-tool-1", kind: "tool", name: "file_list", callId: "call-1", argumentsRaw: "{}", resultRaw: '{"files":[]}', status: "completed" },
      { id: "a-text-2", kind: "text", text: "已完成" },
    ] });
  });

  it("distinguishes failed and interrupted tools without pretending completion", () => {
    expect(toolResultStatus('{"success":false}')).toBe("error");
    expect(toolResultStatus('{"success":true}', "aborted")).toBe("completed");
    expect(toolResultStatus(null, "aborted")).toBe("stopped");
    const withCall = appendAssistantToolEvent(initial, "a", {
      phase: "call", callId: "c", name: "document_read", argumentsRaw: null, resultRaw: null,
    });
    expect(settleAssistant(withCall, "a")[0]).toMatchObject({
      pending: false,
      parts: [{ status: "stopped" }],
    });
  });
});
