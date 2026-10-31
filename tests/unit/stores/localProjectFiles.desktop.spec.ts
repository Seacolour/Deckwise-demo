// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LocalStorageKeys } from "@/src/lib/config/constants/storage";
import {
  __resetLocalProjectFilesForTests,
  createLocalTextFile,
  getLocalTextFile,
  listLocalProjectFiles,
  markLocalProjectFileUploaded,
  upsertLocalTextFileContent,
} from "@/src/stores/localProjectFiles";

describe("localProjectFiles desktop recovery", () => {
  const listRecovery = vi.fn();
  const saveRecovery = vi.fn();
  const deleteRecovery = vi.fn();

  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem(LocalStorageKeys.USER, JSON.stringify({ id: 36 }));
    listRecovery.mockReset().mockResolvedValue([]);
    saveRecovery.mockReset().mockResolvedValue({ key: "saved", updatedAt: 1 });
    deleteRecovery.mockReset().mockResolvedValue(true);
    window.deckwiseDesktop = {
      isDesktop: true,
      deleteRecovery,
      getRuntimeInfo: vi.fn(),
      listRecovery,
      openDesktopAuthUrl: vi.fn(),
      saveRecovery,
    };
    __resetLocalProjectFilesForTests();
  });

  afterEach(() => {
    __resetLocalProjectFilesForTests();
    delete window.deckwiseDesktop;
  });

  it("hydrates an unsaved note from the current user's recovery partition", async () => {
    const recovered = {
      key: "local-file:project-1:local-note-recovered",
      type: "note" as const,
      projectId: "project-1",
      cacheKey: "local-note-recovered",
      fileName: "Recovered note.md",
      content: "# recovered",
      createdAt: 10,
      updatedAt: 20,
    };
    listRecovery.mockResolvedValue([{
      key: recovered.key,
      payload: recovered,
      updatedAt: recovered.updatedAt,
    }]);

    await expect(listLocalProjectFiles("project-1")).resolves.toEqual([recovered]);
    await expect(
      getLocalTextFile("project-1", "local-note-recovered"),
    ).resolves.toEqual(recovered);
    expect(listRecovery).toHaveBeenCalledWith({
      kind: "notes",
      userKey: "36",
    });

    localStorage.setItem(LocalStorageKeys.USER, JSON.stringify({ id: 37 }));
    listRecovery.mockResolvedValue([]);
    await expect(listLocalProjectFiles("project-1")).resolves.toEqual([]);
    expect(listRecovery).toHaveBeenCalledWith({
      kind: "notes",
      userKey: "37",
    });
  });

  it("persists local edits and removes recovery after remote upload", async () => {
    const record = await createLocalTextFile("project-2", {
      kind: "note",
      fileName: "Desktop note.md",
      content: "# draft",
    });
    expect(saveRecovery).toHaveBeenCalledWith(expect.objectContaining({
      key: record.key,
      kind: "notes",
      payload: record,
      userKey: "36",
    }));

    await upsertLocalTextFileContent("project-2", record.cacheKey, "# updated");
    expect(saveRecovery).toHaveBeenLastCalledWith(expect.objectContaining({
      key: record.key,
      kind: "notes",
      payload: expect.objectContaining({ content: "# updated" }),
      userKey: "36",
    }));

    await markLocalProjectFileUploaded("project-2", record.cacheKey, {
      cacheKey: "remote-cache",
      documentId: "document-1",
      url: "https://example.com/document-1.md",
    });
    expect(deleteRecovery).toHaveBeenCalledWith({
      key: record.key,
      kind: "notes",
      userKey: "36",
    });
  });
});
