import {
  createManagedRequestContext,
  isLikelyAuthSensitiveClientUrl,
} from "@/src/lib/api/requestRuntime";

const MAX_CACHED_PDFS = 4;

const pdfBufferCache = new Map<string, ArrayBuffer | Promise<ArrayBuffer>>();

function touchPdfCacheEntry(
  url: string,
  value: ArrayBuffer | Promise<ArrayBuffer> | undefined = pdfBufferCache.get(url),
) {
  if (!value) return;
  pdfBufferCache.delete(url);
  pdfBufferCache.set(url, value);
}

function evictPdfCacheEntries() {
  while (pdfBufferCache.size > MAX_CACHED_PDFS) {
    const oldestKey = pdfBufferCache.keys().next().value;
    if (!oldestKey) return;
    pdfBufferCache.delete(oldestKey);
  }
}

export async function getCachedPdfBuffer(url: string): Promise<ArrayBuffer> {
  const cached = pdfBufferCache.get(url);
  if (cached instanceof ArrayBuffer) {
    touchPdfCacheEntry(url, cached);
    return cached;
  }

  if (cached) {
    touchPdfCacheEntry(url, cached);
    return cached;
  }

  const managedRequest = createManagedRequestContext({
    path: url,
    method: "GET",
    kind: "query",
    authSensitive: isLikelyAuthSensitiveClientUrl(url),
  });
  const fetchPromise = fetch(url, {
    cache: "force-cache",
    signal: managedRequest.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        throw new Error(`Failed to fetch PDF: ${response.status} ${response.statusText}`);
      }

      const buffer = await response.arrayBuffer();
      pdfBufferCache.set(url, buffer);
      touchPdfCacheEntry(url, buffer);
      evictPdfCacheEntries();
      return buffer;
    })
    .catch((error) => {
      if (pdfBufferCache.get(url) === fetchPromise) {
        pdfBufferCache.delete(url);
      }
      throw error;
    })
    .finally(() => {
      managedRequest.cleanup();
    });

  pdfBufferCache.set(url, fetchPromise);
  return fetchPromise;
}

export function clonePdfBuffer(buffer: ArrayBuffer): Uint8Array {
  return new Uint8Array(buffer.slice(0));
}

export function clearPdfBufferCache() {
  pdfBufferCache.clear();
}
