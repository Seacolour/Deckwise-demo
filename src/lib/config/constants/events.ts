export const EVENTS = {
  // Chat
  CHAT_NEW: "chat:new",

  // Existing events (already standardized)
  FILE_DISPLAY: "file:display",
  FILE_ACTIVE: "file:active",
  NOTE_OPEN: "note:open",
  PROJECT_CREATED: "project:created",
  PROJECT_UPDATED: "project:updated",
  PROJECT_DELETED: "project:deleted",
  PROJECT_MISSING: "project:missing",
  SESSION_SWITCH: "session:switch",
  SESSION_CREATED: "session:created",
  SESSION_UPDATED: "session:updated",
  SESSION_TITLE_SYNCED: "session:title-synced",
  SESSION_DELETED: "session:deleted",
  MESSAGE_SENT: "message:sent",
} as const;

export type AppEventName = (typeof EVENTS)[keyof typeof EVENTS];

/**
 * Backward compatibility map for legacy event names.
 * Keep until all senders/listeners are migrated.
 */
export const LEGACY_EVENTS = {
  CHAT_NEW: "newchat",
} as const;
