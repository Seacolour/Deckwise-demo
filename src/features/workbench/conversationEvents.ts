import type { ConversationEntry, ConversationPart } from "./conversationTypes";

export type WorkbenchToolEvent = {
  phase: "call" | "result";
  callId: string | null;
  name: string;
  argumentsRaw: string | null;
  resultRaw: string | null;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : null;
}

function nonblank(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

function jsonText(value: unknown): string | null {
  if (typeof value === "string") return value;
  if (value === undefined || value === null) return null;
  try { return JSON.stringify(value); } catch { return null; }
}

/** Accepts the SSE envelope or the JSON persisted in a tool_output message. */
export function parseWorkbenchToolEvent(raw: string): WorkbenchToolEvent | null {
  let decoded: unknown;
  try { decoded = JSON.parse(raw); } catch { return null; }
  const outer = asRecord(decoded);
  if (!outer) return null;
  const envelopeType = nonblank(outer.type)?.toLowerCase();
  const data = asRecord(outer.payload) ?? outer;
  const phaseValue = nonblank(data.phase)?.toLowerCase()
    ?? (envelopeType === "tool_call" ? "call" : envelopeType === "tool_result" ? "result" : null);
  if (phaseValue !== "call" && phaseValue !== "result") return null;
  if (data !== outer && envelopeType !== "tool") return null;
  const name = nonblank(data.name) ?? nonblank(data.tool_name);
  if (!name) return null;
  return {
    phase: phaseValue,
    callId: nonblank(data.call_id) ?? nonblank(data.callId),
    name,
    argumentsRaw: jsonText(data.arguments_json ?? data.arguments),
    resultRaw: jsonText(data.result_json ?? data.result),
  };
}

export function toolResultStatus(resultRaw: string | null, messageStatus?: string | null):
  "completed" | "error" | "stopped" {
  if (!resultRaw) {
    if (messageStatus?.toLowerCase() === "aborted") return "stopped";
    if (messageStatus?.toLowerCase() === "error") return "error";
    return "completed";
  }
  try {
    const result = asRecord(JSON.parse(resultRaw));
    if (result && (result.success === false || result.is_error === true || result.isError === true
      || (result.error !== undefined && result.error !== null && result.error !== false))) return "error";
  } catch { /* A tool result may be plain text. */ }
  return "completed";
}

function updateAssistant(entries: ConversationEntry[], assistantId: string,
  update: (entry: Extract<ConversationEntry, { kind: "assistant" }>) => ConversationEntry): ConversationEntry[] {
  return entries.map((entry) => entry.kind === "assistant" && entry.id === assistantId
    ? update(entry) : entry);
}

export function appendAssistantText(entries: ConversationEntry[], assistantId: string, delta: string): ConversationEntry[] {
  if (!delta) return entries;
  return updateAssistant(entries, assistantId, (entry) => {
    const parts = [...entry.parts];
    const last = parts.at(-1);
    if (last?.kind === "text") parts[parts.length - 1] = { ...last, text: last.text + delta };
    else parts.push({ id: `${assistantId}-text-${parts.length}`, kind: "text", text: delta });
    return { ...entry, parts };
  });
}

export function appendAssistantToolEvent(entries: ConversationEntry[], assistantId: string,
  event: WorkbenchToolEvent): ConversationEntry[] {
  return updateAssistant(entries, assistantId, (entry) => {
    const parts = [...entry.parts];
    const matchingIndex = event.callId
      ? parts.findLastIndex((part) => part.kind === "tool" && part.callId === event.callId)
      : parts.findLastIndex((part) => part.kind === "tool" && part.name === event.name && part.status === "running");
    if (event.phase === "result" && matchingIndex >= 0) {
      const previous = parts[matchingIndex] as Extract<ConversationPart, { kind: "tool" }>;
      parts[matchingIndex] = { ...previous, resultRaw: event.resultRaw,
        status: toolResultStatus(event.resultRaw) };
    } else if (event.phase === "call" && matchingIndex >= 0) {
      const previous = parts[matchingIndex] as Extract<ConversationPart, { kind: "tool" }>;
      parts[matchingIndex] = { ...previous, argumentsRaw: event.argumentsRaw };
    } else {
      parts.push({
        id: `${assistantId}-tool-${parts.length}`,
        kind: "tool",
        name: event.name,
        callId: event.callId,
        argumentsRaw: event.argumentsRaw,
        resultRaw: event.resultRaw,
        status: event.phase === "result" ? toolResultStatus(event.resultRaw) : "running",
      });
    }
    return { ...entry, parts };
  });
}

/** Prevents an optimistic row from looking live after abort or lost history refresh. */
export function settleAssistant(entries: ConversationEntry[], assistantId: string): ConversationEntry[] {
  return updateAssistant(entries, assistantId, (entry) => ({
    ...entry,
    pending: false,
    parts: entry.parts.map((part) => part.kind === "tool" && part.status === "running"
      ? { ...part, status: "stopped" } : part),
  }));
}
