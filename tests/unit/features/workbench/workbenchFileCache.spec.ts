import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const storage = vi.hoisted(() => ({
  get: vi.fn(), put: vi.fn(), enforce: vi.fn(), deletePrefix: vi.fn(),
}));
vi.mock("@/src/stores/derivedCache", () => ({
  idbGet: storage.get,
  idbPut: storage.put,
  idbEnforceLimits: storage.enforce,
  idbDeletePrefix: storage.deletePrefix,
}));

import { clearWorkbenchFileCacheForUser, loadWorkbenchPreviewFile } from "@/src/features/workbench/workbenchFileCache";

const file = { id: "21", cacheKey: "a".repeat(64), name: "notes.txt", sizeBytes: 6, documentId: null, image: false };

describe("workbench file cache", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    storage.get.mockResolvedValue(null);
    storage.put.mockResolvedValue(undefined);
    storage.enforce.mockResolvedValue(undefined);
    storage.deletePrefix.mockResolvedValue(undefined);
    vi.stubGlobal("fetch", vi.fn().mockImplementation(async () =>
      new Response("测试", { headers: { "content-type": "text/plain; charset=utf-8" } })));
  });
  afterEach(() => vi.unstubAllGlobals());

  it("reuses a fetched Blob locally and clears it when the user signs out", async () => {
    const first = await loadWorkbenchPreviewFile("user-1", "7", file);
    const second = await loadWorkbenchPreviewFile("user-1", "7", file);
    expect(first).toBe(second);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(storage.put).toHaveBeenCalledWith(expect.objectContaining({
      key: `wbfile:user-1:7:21:${file.cacheKey}`, blob: first,
    }));

    await clearWorkbenchFileCacheForUser("user-1");
    await loadWorkbenchPreviewFile("user-1", "7", file);
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(storage.deletePrefix).toHaveBeenCalledWith("wbfile:user-1:");
  });
});
