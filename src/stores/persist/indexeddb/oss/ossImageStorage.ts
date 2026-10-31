import type { ProxyImageResult } from "@/src/lib/api/proxyImage";
import { getUserKey } from "@/src/stores/authUserKey";
import { openJuziLabDb, STORE_OSS_IMAGES } from "@/src/stores/idb";

const STORE_NAME = STORE_OSS_IMAGES;
const MAX_OSS_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_OSS_IMAGE_ITEMS_PER_USER = 200;
const MAX_OSS_IMAGE_TOTAL_BYTES_PER_USER = 40 * 1024 * 1024;

export interface OSSImageRecord {
  key: string;
  userKey: string;
  sourceId: string;
  blob: Blob;
  mime: string | null;
  contentLength: number | null;
  cacheKey?: string;
  url?: string;
  name: string;
  createdAt: number;
  updatedAt: number;
  sizeBytes: number;
}

export function buildOssImageStorageKey(userKey: string, sourceId: string) {
  return `ossimg:${userKey}:${sourceId}`;
}

function txDone(tx: IDBTransaction) {
  return new Promise<void>((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error("IDB transaction failed"));
    tx.onabort = () => reject(tx.error ?? new Error("IDB transaction aborted"));
  });
}

/**
 * OSS 图片存储管理器
 * 使用 IndexedDB 按用户分区 + sourceId 存储侧边栏上传的图片文件
 */
class OSSImageStorage {
  private db: IDBDatabase | null = null;

  private async init(): Promise<void> {
    if (this.db) return;
    this.db = await openJuziLabDb();
  }

  private async getByStorageKey(storageKey: string): Promise<OSSImageRecord | null> {
    await this.init();

    if (!this.db) {
      throw new Error("IndexedDB is not initialized");
    }

    return new Promise((resolve, reject) => {
      const transaction = this.db!.transaction([STORE_NAME], "readonly");
      const store = transaction.objectStore(STORE_NAME);
      const request = store.get(storageKey);

      request.onsuccess = () => {
        resolve((request.result as OSSImageRecord | undefined) ?? null);
      };

      request.onerror = () => {
        reject(new Error(`Failed to get OSS image: ${request.error}`));
      };
    });
  }

  private async listUserRecords(userKey: string): Promise<OSSImageRecord[]> {
    await this.init();

    if (!this.db) {
      throw new Error("IndexedDB is not initialized");
    }

    const tx = this.db.transaction([STORE_NAME], "readonly");
    const store = tx.objectStore(STORE_NAME);
    const index = store.index("userKey");
    const records: OSSImageRecord[] = [];

    await new Promise<void>((resolve, reject) => {
      const request = index.openCursor(IDBKeyRange.only(userKey));
      request.onerror = () => reject(request.error ?? new Error("Failed to scan OSS images"));
      request.onsuccess = () => {
        const cursor = request.result;
        if (!cursor) {
          resolve();
          return;
        }
        const record = cursor.value as OSSImageRecord;
        if (record?.userKey === userKey) {
          records.push(record);
        }
        cursor.continue();
      };
    });

    await txDone(tx);
    return records;
  }

  private async deleteStorageKeys(storageKeys: string[]): Promise<void> {
    if (storageKeys.length === 0) {
      return;
    }

    await this.init();

    if (!this.db) {
      throw new Error("IndexedDB is not initialized");
    }

    const tx = this.db.transaction([STORE_NAME], "readwrite");
    const store = tx.objectStore(STORE_NAME);
    for (const storageKey of storageKeys) {
      store.delete(storageKey);
    }
    await txDone(tx);
  }

  private async enforceUserLimits(userKey: string): Promise<void> {
    const records = await this.listUserRecords(userKey);
    if (records.length <= MAX_OSS_IMAGE_ITEMS_PER_USER) {
      const totalBytes = records.reduce((sum, record) => sum + Number(record.sizeBytes ?? record.blob?.size ?? 0), 0);
      if (totalBytes <= MAX_OSS_IMAGE_TOTAL_BYTES_PER_USER) {
        return;
      }
    }

    records.sort((a, b) => (b.updatedAt ?? b.createdAt ?? 0) - (a.updatedAt ?? a.createdAt ?? 0));
    const kept = records.slice();

    while (kept.length > MAX_OSS_IMAGE_ITEMS_PER_USER) {
      kept.pop();
    }

    let totalBytes = kept.reduce((sum, record) => sum + Number(record.sizeBytes ?? record.blob?.size ?? 0), 0);
    while (totalBytes > MAX_OSS_IMAGE_TOTAL_BYTES_PER_USER && kept.length > 0) {
      const victim = kept.pop();
      totalBytes -= Number(victim?.sizeBytes ?? victim?.blob?.size ?? 0);
    }

    const keptKeys = new Set(kept.map((record) => record.key));
    const victims = records
      .filter((record) => !keptKeys.has(record.key))
      .map((record) => record.key);

    await this.deleteStorageKeys(victims);
  }

  async save(
    sourceId: string,
    blob: Blob,
    metadata: {
      mime?: string | null;
      contentLength?: number | null;
      cacheKey?: string;
      url?: string;
      name: string;
    }
  ): Promise<void> {
    if (!sourceId) {
      return;
    }

    if (blob.size > MAX_OSS_IMAGE_BYTES) {
      return;
    }

    await this.init();

    if (!this.db) {
      throw new Error("IndexedDB is not initialized");
    }

    const userKey = getUserKey();
    const storageKey = buildOssImageStorageKey(userKey, sourceId);
    const existing = await this.getByStorageKey(storageKey);
    const createdAt = existing?.createdAt ?? Date.now();

    const record: OSSImageRecord = {
      key: storageKey,
      userKey,
      sourceId,
      blob,
      mime: metadata.mime ?? null,
      contentLength: metadata.contentLength ?? null,
      cacheKey: metadata.cacheKey,
      url: metadata.url,
      name: metadata.name,
      createdAt,
      updatedAt: Date.now(),
      sizeBytes: blob.size,
    };

    const transaction = this.db.transaction([STORE_NAME], "readwrite");
    const store = transaction.objectStore(STORE_NAME);
    store.put(record);
    await txDone(transaction);
    await this.enforceUserLimits(userKey);
  }

  async saveFromResult(
    sourceId: string,
    result: ProxyImageResult,
    metadata: {
      name: string;
      cacheKey?: string;
      url?: string;
    }
  ): Promise<void> {
    return this.save(sourceId, result.blob, {
      mime: result.mime,
      contentLength: result.contentLength,
      cacheKey: metadata.cacheKey,
      url: metadata.url,
      name: metadata.name,
    });
  }

  async get(sourceId: string): Promise<OSSImageRecord | null> {
    if (!sourceId) {
      return null;
    }

    const userKey = getUserKey();
    const record = await this.getByStorageKey(buildOssImageStorageKey(userKey, sourceId));
    if (!record) {
      return null;
    }
    if (record.userKey !== userKey || record.sourceId !== sourceId) {
      return null;
    }
    return record;
  }

  async has(sourceId: string): Promise<boolean> {
    const record = await this.get(sourceId);
    return record !== null;
  }

  async delete(sourceId: string): Promise<void> {
    if (!sourceId) {
      return;
    }

    const userKey = getUserKey();
    await this.deleteStorageKeys([buildOssImageStorageKey(userKey, sourceId)]);
  }

  async getAllSourceIds(): Promise<string[]> {
    const records = await this.listUserRecords(getUserKey());
    return records.map((record) => record.sourceId);
  }

  async clearUserPartition(userKey: string): Promise<void> {
    const records = await this.listUserRecords(userKey);
    await this.deleteStorageKeys(records.map((record) => record.key));
  }

  async clearUserPartitions(userKeys: Iterable<string>): Promise<void> {
    for (const userKey of userKeys) {
      const normalized = userKey.trim();
      if (!normalized) {
        continue;
      }
      await this.clearUserPartition(normalized);
    }
  }

  async clear(): Promise<void> {
    await this.init();

    if (!this.db) {
      throw new Error("IndexedDB is not initialized");
    }

    const transaction = this.db.transaction([STORE_NAME], "readwrite");
    const store = transaction.objectStore(STORE_NAME);
    store.clear();
    await txDone(transaction);
  }
}

export const ossImageStorage = new OSSImageStorage();
