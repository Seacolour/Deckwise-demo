import assert from "node:assert/strict";
import { test, afterEach, beforeEach } from "vitest";
import {
  __resetLocalProjectFilesForTests,
  createLocalTextFile,
  deleteLocalProjectFile,
  getLocalTextFile,
  getLocalProjectFileUploadBinding,
  markLocalProjectFileUploaded,
  upsertLocalTextFileContent,
} from "@/src/stores/localProjectFiles.ts";

beforeEach(() => {
  __resetLocalProjectFilesForTests();
});

afterEach(() => {
  __resetLocalProjectFilesForTests();
});

test("local project file upload bindings can promote and clear local drafts", async () => {
  const record = await createLocalTextFile("project-1", {
    kind: "note",
    fileName: "note-1.md",
    content: "# draft",
  });

  await markLocalProjectFileUploaded("project-1", record.cacheKey, {
    cacheKey: "remote-cache-1",
    documentId: "doc-7",
    url: "https://example.com/remote-cache-1.md",
  });

  assert.deepEqual(
    await getLocalProjectFileUploadBinding("project-1", record.cacheKey),
    {
      cacheKey: "remote-cache-1",
      documentId: "doc-7",
      url: "https://example.com/remote-cache-1.md",
    },
  );

  await deleteLocalProjectFile("project-1", record.cacheKey);

  assert.equal(
    await getLocalProjectFileUploadBinding("project-1", record.cacheKey),
    null,
  );
});

test("local note files keep text content in sync", async () => {
  const record = await createLocalTextFile("project-3", {
    kind: "note",
    fileName: "draft.md",
    content: "# draft",
  });

  assert.equal(record.type, "note");
  assert.equal(record.content, "# draft");

  await upsertLocalTextFileContent("project-3", record.cacheKey, "# next");

  const updated = await getLocalTextFile("project-3", record.cacheKey);
  assert.ok(updated);
  assert.equal(updated?.type, "note");
  assert.equal(updated?.content, "# next");
});
