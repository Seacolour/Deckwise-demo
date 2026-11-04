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

export type ConversationAttachment = {
  kind: "file" | "image" | "document";
  name: string;
  fileId?: string;
  documentId?: string;
  cacheKey?: string;
};

export type ConversationEntry =
  | { id: string; kind: "user"; text: string; attachments?: ConversationAttachment[] }
  | { id: string; kind: "assistant"; parts: ConversationPart[]; pending?: boolean };
