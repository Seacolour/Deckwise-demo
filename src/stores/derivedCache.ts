"use client";

import { openJuziLabDb, STORE_DERIVED_ASSETS } from "@/src/stores/idb";

export type DerivedAssetRecordBase = {
  key: string;
  type: string;
  createdAt?: number;
  updatedAt?: number;
  sizeBytes?: number;
};

function reqToPromise<T>(req: IDBRequest<T>) {
  return new Promise<T>((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("IDB request failed"));
  });
}

function txDone(tx: IDBTransaction) {
  return new Promise<void>((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error("IDB transaction failed"));
    tx.onabort = () => reject(tx.error ?? new Error("IDB transaction aborted"));
  });
}

export async function idbGet<T>(key: string): Promise<T | null> {
  const db = await openJuziLabDb();
  const tx = db.transaction(STORE_DERIVED_ASSETS, "readonly");
  const store = tx.objectStore(STORE_DERIVED_ASSETS);
  const res = (await reqToPromise(store.get(key))) as unknown;
  await txDone(tx);
  return (res as T) ?? null;
}

export async function idbPut<T extends DerivedAssetRecordBase>(rec: T): Promise<void> {
  const db = await openJuziLabDb();
  const tx = db.transaction(STORE_DERIVED_ASSETS, "readwrite");
  tx.objectStore(STORE_DERIVED_ASSETS).put(rec);
  await txDone(tx);
}

export async function idbDelete(key: string): Promise<void> {
  const db = await openJuziLabDb();
  const tx = db.transaction(STORE_DERIVED_ASSETS, "readwrite");
  tx.objectStore(STORE_DERIVED_ASSETS).delete(key);
  await txDone(tx);
}

export async function idbDeletePrefix(
  prefix: string,
  opts?: { filter?: (rec: DerivedAssetRecordBase) => boolean },
): Promise<void> {
  const items = await idbScanPrefix<DerivedAssetRecordBase>(prefix);
  for (const item of items) {
    if (!item || typeof item.key !== "string") {
      continue;
    }
    if (opts?.filter && !opts.filter(item)) {
      continue;
    }
    await idbDelete(item.key);
  }
}

export async function idbScanPrefix<T>(prefix: string): Promise<T[]> {
  const db = await openJuziLabDb();
  const tx = db.transaction(STORE_DERIVED_ASSETS, "readonly");
  const store = tx.objectStore(STORE_DERIVED_ASSETS);
  const out: T[] = [];
  await new Promise<void>((resolve, reject) => {
    const req = store.openCursor();
    req.onerror = () => reject(req.error ?? new Error("IDB cursor failed"));
    req.onsuccess = () => {
      const cursor = req.result;
      if (!cursor) {
        resolve();
        return;
      }
      const k = String(cursor.key ?? "");
      if (k.startsWith(prefix)) out.push(cursor.value as T);
      cursor.continue();
    };
  });
  await txDone(tx);
  return out;
}

export async function idbEnforceLimits(opts: {
  prefix: string;
  maxItems: number;
  maxBytes: number;
  // newest kept; oldest deleted
  getUpdatedAt?: (rec: DerivedAssetRecordBase) => number;
  getSizeBytes?: (rec: DerivedAssetRecordBase) => number;
}) {
  const { prefix, maxItems, maxBytes } = opts;
  const getUpdatedAt = opts.getUpdatedAt ?? ((r: DerivedAssetRecordBase) => Number(r?.updatedAt ?? r?.createdAt ?? 0));
  const getSizeBytes = opts.getSizeBytes ?? ((r: DerivedAssetRecordBase) => Number(r?.sizeBytes ?? 0));

  const items = (await idbScanPrefix<DerivedAssetRecordBase>(prefix)).filter((item) => typeof item.key === "string");
  if (items.length <= maxItems && items.reduce((s, r) => s + getSizeBytes(r), 0) <= maxBytes) return;

  items.sort((a, b) => getUpdatedAt(b) - getUpdatedAt(a));

  const kept = items.slice();
  while (kept.length > maxItems) {
    const victim = kept.pop();
    if (victim?.key) await idbDelete(String(victim.key));
  }

  let totalBytes = kept.reduce((s, r) => s + getSizeBytes(r), 0);
  while (totalBytes > maxBytes && kept.length > 0) {
    const victim = kept.pop();
    if (!victim?.key) break;
    await idbDelete(String(victim.key));
    totalBytes = kept.reduce((s, r) => s + getSizeBytes(r), 0);
  }
}
