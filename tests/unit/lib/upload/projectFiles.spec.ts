import assert from "node:assert/strict";
import { test } from "vitest";
import { installBrowserRuntime } from "@/src/test/browser/runtime.ts";
import { installWeakNetworkFetch } from "@/src/test/network/weakNetwork.ts";
import { clearManagedRequestsForTests } from "@/src/lib/api/requestRuntime.ts";
import { uploadProjectFile, uploadProjectFilesBatch } from "@/src/lib/upload/projectFiles.ts";

test("uploadProjectFile single-flights duplicate concurrent uploads with the same identity", async () => {
  const browser = installBrowserRuntime();
  const mock = installWeakNetworkFetch([
    {
      kind: "api-json",
      delayMs: 25,
      data: {
        cacheKey: "cache-1",
        publicUrl: "https://cdn.example.com/files/notes.md",
        fileName: "notes.md",
        documentId: 7,
      },
    },
  ]);

  try {
    const file = new File(["hello"], "notes.md", {
      type: "text/markdown",
      lastModified: 1710000000000,
    });

    const [first, second] = await Promise.all([
      uploadProjectFile({
        kind: "note",
        file,
        projectId: "project-1",
        sessionId: "session-1",
        tFiles: (key) => key,
      }),
      uploadProjectFile({
        kind: "note",
        file,
        projectId: "project-1",
        sessionId: "session-1",
        tFiles: (key) => key,
      }),
    ]);

    assert.equal(mock.calls.length, 1);
    assert.match(mock.calls[0]!.url, /\/files\/upload\?projectId=project-1&uploadSource=user_upload$/);
    assert.deepEqual(first, second);
    mock.assertConsumed();
  } finally {
    clearManagedRequestsForTests();
    mock.restore();
    browser.restore();
  }
});

test("uploadProjectFile keeps generated uploads out of the user-upload single-flight identity", async () => {
  const browser = installBrowserRuntime();
  const mock = installWeakNetworkFetch([
    {
      kind: "api-json",
      delayMs: 25,
      data: {
        cacheKey: "cache-user",
        publicUrl: "https://cdn.example.com/files/deck.mslides",
        fileName: "deck.mslides",
        documentId: 7,
      },
    },
    {
      kind: "api-json",
      delayMs: 25,
      data: {
        cacheKey: "cache-generated",
        publicUrl: "https://cdn.example.com/files/deck-generated.mslides",
        fileName: "deck-generated.mslides",
        documentId: 8,
      },
    },
  ]);

  try {
    const file = new File(["slides"], "deck.mslides", {
      type: "application/json",
      lastModified: 1710000000000,
    });

    const [userUpload, generatedUpload] = await Promise.all([
      uploadProjectFile({
        kind: "slides",
        file,
        projectId: "project-1",
        sessionId: "session-1",
        tFiles: (key) => key,
      }),
      uploadProjectFile({
        kind: "slides",
        file,
        projectId: "project-1",
        sessionId: "session-1",
        uploadSource: "generated",
        tFiles: (key) => key,
      }),
    ]);

    assert.equal(mock.calls.length, 2);
    assert.match(mock.calls[0]!.url, /uploadSource=user_upload$/);
    assert.match(mock.calls[1]!.url, /uploadSource=generated$/);
    assert.equal(userUpload.cacheKey, "cache-user");
    assert.equal(generatedUpload.cacheKey, "cache-generated");
    mock.assertConsumed();
  } finally {
    clearManagedRequestsForTests();
    mock.restore();
    browser.restore();
  }
});

test("uploadProjectFilesBatch uploads multiple files in one batch request", async () => {
  const browser = installBrowserRuntime();
  const mock = installWeakNetworkFetch([
    {
      kind: "api-json",
      delayMs: 25,
      data: {
        batchId: "batch-1",
        projectId: "project-1",
        totalCount: 2,
        files: [
          {
            cacheKey: "cache-image",
            publicUrl: "https://cdn.example.com/files/cover.png",
            fileName: "cover.png",
            sourceFileId: 11,
          },
          {
            cacheKey: "cache-pdf",
            publicUrl: "https://cdn.example.com/files/brief.pdf",
            fileName: "brief.pdf",
            sourceFileId: 12,
            documentId: 20,
          },
        ],
      },
    },
  ]);

  try {
    const image = new File(["image"], "cover.png", {
      type: "image/png",
      lastModified: 1710000000000,
    });
    const pdf = new File(["pdf"], "brief.pdf", {
      type: "application/pdf",
      lastModified: 1710000000001,
    });

    const uploaded = await uploadProjectFilesBatch({
      files: [
        { kind: "image", file: image, includePreviewUrl: true },
        { kind: "pdf", file: pdf },
      ],
      projectId: "project-1",
      tFiles: (key) => key,
    });

    assert.equal(mock.calls.length, 1);
    assert.match(mock.calls[0]!.url, /\/files\/upload\/batch\?projectId=project-1&uploadSource=user_upload$/);
    assert.equal((mock.calls[0]!.init?.body as FormData).getAll("files").length, 2);
    assert.equal(uploaded[0]?.cacheKey, "cache-image");
    assert.equal(uploaded[0]?.sourceFileId, "11");
    assert.equal(uploaded[0]?.type, "image");
    assert.equal(uploaded[1]?.cacheKey, "cache-pdf");
    assert.equal(uploaded[1]?.sourceFileId, "12");
    assert.equal(uploaded[1]?.documentId, "20");
    assert.equal(uploaded[1]?.type, "pdf");
    mock.assertConsumed();
  } finally {
    clearManagedRequestsForTests();
    mock.restore();
    browser.restore();
  }
});
