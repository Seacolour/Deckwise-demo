export type DesktopRuntimeInfo = {
  appVersion: string;
  architecture?: string;
  deviceName: string;
  platform: string;
};

export type DesktopMenuId = "file" | "edit" | "view" | "window" | "help";

export type DesktopMenuPosition = {
  x: number;
  y: number;
};

export type DesktopPreferences = {
  askWhereToSave: boolean;
  defaultExportDirectory: string | null;
  lastOpenDirectory: string | null;
  lastSaveDirectory: string | null;
};

export type DesktopSettings = {
  version: 1;
  preferences: DesktopPreferences;
};

export type DesktopRecoveryKind = "chat" | "notes" | "slides";

export type DesktopRecoveryRequest = {
  userKey: string;
  kind: DesktopRecoveryKind;
  key: string;
};

export type DesktopRecoveryWriteRequest<T = unknown> = DesktopRecoveryRequest & {
  payload: T;
};

export type DesktopRecoveryListRequest = {
  userKey: string;
  kind: DesktopRecoveryKind;
};

export type DesktopRecoveryClearRequest = {
  userKey: string;
  kind?: DesktopRecoveryKind;
};

export type DesktopRecoveryRecord<T = unknown> = {
  key: string;
  payload: T;
  updatedAt: number;
};

export type DeckwiseDesktopBridge = {
  isDesktop: true;
  platform?: string;
  clearRecovery?: (request: DesktopRecoveryClearRequest) => Promise<boolean>;
  deleteRecovery?: (request: DesktopRecoveryRequest) => Promise<boolean>;
  getSettings?: () => Promise<DesktopSettings>;
  getRuntimeInfo: () => Promise<DesktopRuntimeInfo>;
  listRecovery?: <T = unknown>(
    request: DesktopRecoveryListRequest,
  ) => Promise<DesktopRecoveryRecord<T>[]>;
  openDesktopAuthUrl: (url: string) => Promise<boolean>;
  openDesktopMenu?: (
    menuId: DesktopMenuId,
    position: DesktopMenuPosition,
  ) => Promise<boolean>;
  readRecovery?: <T = unknown>(
    request: DesktopRecoveryRequest,
  ) => Promise<DesktopRecoveryRecord<T> | null>;
  saveRecovery?: <T = unknown>(
    request: DesktopRecoveryWriteRequest<T>,
  ) => Promise<{ key: string; updatedAt: number }>;
  setDesktopTitleBarTheme?: (theme: "light" | "dark") => Promise<boolean>;
  updateSettings?: (patch: Partial<DesktopPreferences>) => Promise<DesktopSettings>;
};

declare global {
  interface Window {
    deckwiseDesktop?: DeckwiseDesktopBridge;
  }
}

export function getDesktopBridge(): DeckwiseDesktopBridge | null {
  if (typeof window === "undefined") {
    return null;
  }

  const bridge = window.deckwiseDesktop;
  if (
    bridge?.isDesktop !== true
    || typeof bridge.getRuntimeInfo !== "function"
    || typeof bridge.openDesktopAuthUrl !== "function"
  ) {
    return null;
  }

  return bridge;
}

export function isDeckwiseDesktop(): boolean {
  return getDesktopBridge() !== null;
}
