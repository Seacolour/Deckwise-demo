export type ConversationPart =
  | { id: string; kind: "text"; text: string }
  | {
    id: string;
    kind: "tool";
    name: string;
    callId: string | null;
    argumentsRaw: string | null;
    resultRaw: string | null;
    status: "running" | "completed" | "error" | "stopped";
  };

export type ConversationEntry =
  | { id: string; kind: "user"; text: string }
  | { id: string; kind: "assistant"; parts: ConversationPart[]; pending?: boolean };
