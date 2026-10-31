// store/idb.ts
// IndexedDB 统一入口：集中维护 DB 版本与 objectStore/index 的创建逻辑

import { IndexedDB as IndexedDBConstants } from '@/src/lib/config/constants/storage';

export const JUZI_DB_NAME = IndexedDBConstants.DB_NAME;
export const JUZI_DB_VERSION = IndexedDBConstants.DB_VERSION;

export const STORE_SCHOLAR_ARTICLES = IndexedDBConstants.STORE_SCHOLAR_ARTICLES;
export const STORE_OSS_IMAGES = IndexedDBConstants.STORE_OSS_IMAGES;
export const STORE_DERIVED_ASSETS = IndexedDBConstants.STORE_DERIVED_ASSETS;

let dbPromise: Promise<IDBDatabase> | null = null;

function ensureStores(db: IDBDatabase) {
  // Scholar articles (legacy/compat)
  if (!db.objectStoreNames.contains(STORE_SCHOLAR_ARTICLES)) {
    const articleStore = db.createObjectStore(STORE_SCHOLAR_ARTICLES, { keyPath: 'title' });
    articleStore.createIndex('createdAt', 'data.createdAt', { unique: false });
    articleStore.createIndex('updatedAt', 'data.updatedAt', { unique: false });
  }

  // OSS images by user-scoped key
  if (!db.objectStoreNames.contains(STORE_OSS_IMAGES)) {
    const ossImageStore = db.createObjectStore(STORE_OSS_IMAGES, { keyPath: 'key' });
    ossImageStore.createIndex('userKey', 'userKey', { unique: false });
    ossImageStore.createIndex('sourceId', 'sourceId', { unique: false });
    ossImageStore.createIndex('createdAt', 'createdAt', { unique: false });
    ossImageStore.createIndex('updatedAt', 'updatedAt', { unique: false });
    ossImageStore.createIndex('cacheKey', 'cacheKey', { unique: false });
  }

  // Derived assets (future-facing): keep heavy PDF bytes out of IndexedDB.
  if (!db.objectStoreNames.contains(STORE_DERIVED_ASSETS)) {
    const s = db.createObjectStore(STORE_DERIVED_ASSETS, { keyPath: 'key' });
    s.createIndex('createdAt', 'createdAt', { unique: false });
    s.createIndex('updatedAt', 'updatedAt', { unique: false });
    s.createIndex('type', 'type', { unique: false });
  }
}

/**
 * 打开 JuziLab IndexedDB（单例）
 * - 只应在浏览器端调用
 * - 会自动完成表/索引创建（onupgradeneeded）
 */
export function openJuziLabDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  if (typeof indexedDB === 'undefined') {
    return Promise.reject(new Error('indexedDB is not available (SSR)'));
  }

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(JUZI_DB_NAME, JUZI_DB_VERSION);

    request.onerror = () => reject(new Error(`Failed to open IndexedDB: ${request.error}`));

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      const oldVersion = event.oldVersion;
      // Before ensuring new stores, remove legacy PDF-blob stores to avoid keeping large binaries in IndexedDB.
      // Note: deleting stores is destructive; acceptable because PDFs should be URL-driven (HTTP cache + Range).
      if (db.objectStoreNames.contains(IndexedDBConstants.LEGACY_STORE_PDFS)) {
        db.deleteObjectStore(IndexedDBConstants.LEGACY_STORE_PDFS);
      }
      if (db.objectStoreNames.contains(IndexedDBConstants.LEGACY_STORE_OSS_PDFS)) {
        db.deleteObjectStore(IndexedDBConstants.LEGACY_STORE_OSS_PDFS);
      }
      // v5: rebuild the cache-only OSS image store with a user-scoped primary key.
      if (oldVersion < 5 && db.objectStoreNames.contains(STORE_OSS_IMAGES)) {
        db.deleteObjectStore(STORE_OSS_IMAGES);
      }
      ensureStores(db);
    };

    request.onsuccess = () => resolve(request.result);
  });

  return dbPromise;
}
