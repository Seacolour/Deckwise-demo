import {
  getDesktopBridge,
  type DesktopRecoveryKind,
  type DesktopRecoveryRecord,
  type DesktopSettings,
} from "./desktopBridge";

export async function getDesktopSettings(): Promise<DesktopSettings | null> {
  const getSettings = getDesktopBridge()?.getSettings;
  if (!getSettings) {
    return null;
  }
  try {
    return await getSettings();
  } catch {
    return null;
  }
}

export async function readDesktopRecovery<T>({
  key,
  kind,
  userKey,
}: {
  key: string;
  kind: DesktopRecoveryKind;
  userKey: string;
}): Promise<DesktopRecoveryRecord<T> | null> {
  const readRecovery = getDesktopBridge()?.readRecovery;
  if (!readRecovery) {
    return null;
  }
  try {
    return await readRecovery<T>({ key, kind, userKey });
  } catch {
    return null;
  }
}

export async function listDesktopRecovery<T>({
  kind,
  userKey,
}: {
  kind: DesktopRecoveryKind;
  userKey: string;
}): Promise<DesktopRecoveryRecord<T>[]> {
  const listRecovery = getDesktopBridge()?.listRecovery;
  if (!listRecovery) {
    return [];
  }
  try {
    return await listRecovery<T>({ kind, userKey });
  } catch {
    return [];
  }
}

export async function saveDesktopRecovery<T>({
  key,
  kind,
  payload,
  userKey,
}: {
  key: string;
  kind: DesktopRecoveryKind;
  payload: T;
  userKey: string;
}): Promise<boolean> {
  const saveRecovery = getDesktopBridge()?.saveRecovery;
  if (!saveRecovery) {
    return false;
  }
  try {
    await saveRecovery({ key, kind, payload, userKey });
    return true;
  } catch {
    return false;
  }
}

export async function deleteDesktopRecovery({
  key,
  kind,
  userKey,
}: {
  key: string;
  kind: DesktopRecoveryKind;
  userKey: string;
}): Promise<boolean> {
  const deleteRecovery = getDesktopBridge()?.deleteRecovery;
  if (!deleteRecovery) {
    return false;
  }
  try {
    await deleteRecovery({ key, kind, userKey });
    return true;
  } catch {
    return false;
  }
}

export async function clearDesktopRecovery({
  kind,
  userKey,
}: {
  kind?: DesktopRecoveryKind;
  userKey: string;
}): Promise<boolean> {
  const clearRecovery = getDesktopBridge()?.clearRecovery;
  if (!clearRecovery) {
    return false;
  }
  try {
    await clearRecovery({ kind, userKey });
    return true;
  } catch {
    return false;
  }
}
