"use client";

/**
 * 图片缓存预热 - 踩坑记录
 *
 * ## 踩坑
 * - 原来的实现没有清理机制，Map无限增长 → 内存泄漏
 * - 修复：添加LRU清理，MAX_CACHE_SIZE=100
 *
 * ## 机制
 * - warmImageElements: 保存HTMLImageElement用于预加载
 * - warmImageStatus: 保存每个URL的加载状态
 * - accessOrder: LRU访问顺序，用于清理
 */

type WarmImageStatus = "ok" | "err" | Promise<"ok" | "err">;

const MAX_CACHE_SIZE = 100; // 踩坑：必须限制缓存大小，否则内存泄漏

const warmImageElements = new Map<string, HTMLImageElement>();
const warmImageStatus = new Map<string, WarmImageStatus>();
const accessOrder: string[] = []; // LRU访问顺序

function evictIfNeeded() {
  if (accessOrder.length <= MAX_CACHE_SIZE) {
    return;
  }

  // 踩坑：一次清理20%，避免频繁触发清理
  const evictCount = Math.floor(MAX_CACHE_SIZE * 0.2);
  for (let i = 0; i < evictCount; i++) {
    const key = accessOrder.shift();
    if (key) {
      warmImageStatus.delete(key);
      warmImageElements.delete(key);
    }
  }
}

function markAccess(key: string) {
  const idx = accessOrder.indexOf(key);
  if (idx !== -1) {
    accessOrder.splice(idx, 1);
  }
  accessOrder.push(key);
  evictIfNeeded();
}

export function canWarmImage(src: string | null): boolean {
  return Boolean(src) && !src!.startsWith("blob:");
}

export function getWarmImageStatus(src: string | null): "idle" | "loading" | "ok" | "err" {
  if (!src || !canWarmImage(src)) {
    return "idle";
  }

  const cached = warmImageStatus.get(src);
  if (cached === "ok" || cached === "err") {
    markAccess(src); // 踩坑：访问时更新LRU顺序
    return cached;
  }
  if (cached instanceof Promise) {
    return "loading";
  }
  return "idle";
}

export function isWarmImageReady(src: string | null): boolean {
  return getWarmImageStatus(src) === "ok";
}

export function warmImage(src: string): Promise<"ok" | "err"> {
  if (!canWarmImage(src)) {
    return Promise.resolve("ok");
  }

  const cached = warmImageStatus.get(src);
  if (cached instanceof Promise) {
    return cached;
  }
  if (cached === "ok" || cached === "err") {
    markAccess(src);
    return Promise.resolve(cached);
  }

  const promise = new Promise<"ok" | "err">((resolve) => {
    const img = new window.Image();
    warmImageElements.set(src, img);

    const settleLoaded = () => {
      warmImageStatus.set(src, "ok");
      markAccess(src);
      resolve("ok");
    };

    const settleFailed = () => {
      warmImageStatus.set(src, "err");
      warmImageElements.delete(src);
      resolve("err");
    };

    img.onload = () => {
      const decodePromise = typeof img.decode === "function" ? img.decode() : Promise.resolve();
      void decodePromise.then(settleLoaded).catch(settleLoaded);
    };
    img.onerror = settleFailed;
    img.src = src;

    if (img.complete) {
      if (img.naturalWidth > 0) {
        const decodePromise = typeof img.decode === "function" ? img.decode() : Promise.resolve();
        void decodePromise.then(settleLoaded).catch(settleLoaded);
      } else {
        settleFailed();
      }
    }
  });

  warmImageStatus.set(src, promise);
  accessOrder.push(src);
  return promise;
}

// Export for testing/debugging
export function getCacheSize() {
  return warmImageStatus.size;
}

export function clearImageCache() {
  warmImageElements.clear();
  warmImageStatus.clear();
  accessOrder.length = 0;
}
