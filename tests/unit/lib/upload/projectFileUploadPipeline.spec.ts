import assert from "node:assert/strict";
import { test } from "vitest";
import { completeUploadedProjectFileUpload } from "@/src/lib/upload/projectFileUploadPipeline.ts";
import { withTimeout } from "@/src/lib/upload/withTimeout.ts";

function buildRuntime(calls: string[]) {
  return {
    publishUploadedProjectFileToMaterials: async (
      _projectId: string,
      file: {
        documentId?: string;
      },
    ) => {
      calls.push(`publish:${file.documentId ?? "none"}`);
    },
    logPowerPointImportStage: (stage: string) => {
      calls.push(`stage:${stage}`);
    },
    powerPointImportPostProcessRuntime: {
      getDocument: async () => {
        calls.push("getDocument");
        return {
          draftContent: "# raw",
          version: 2,
        };
      },
      normalizeMarkdownToSlides: async () => {
        calls.push("normalize");
        return {
          text: "---\n# deck",
        };
      },
      updateDraft: async () => {
        calls.push("updateDraft");
        return {
          draftContent: "---\n# deck",
          version: 3,
        };
      },
      persistFetchedNoteContent: async () => {
        calls.push("persist");
      },
      dispatchNoteContentUpdate: () => {
        calls.push("dispatch");
      },
    },
  };
}

test("completeUploadedProjectFileUpload publishes pptx imports only after slides normalization completes", async () => {
  const calls: string[] = [];

  await completeUploadedProjectFileUpload({
    originalFileName: "deck.pptx",
    projectId: "project-1",
    uploadedFile: {
      type: "slides",
      cacheKey: "cache-1",
      name: "deck",
      documentId: "doc-1",
    },
  }, buildRuntime(calls));

  assert.deepEqual(calls, [
    "stage:post-process-check",
    "stage:fetch-draft-start",
    "getDocument",
    "stage:fetch-draft-finished",
    "stage:normalize-to-slides-start",
    "normalize",
    "stage:normalize-to-slides-finished",
    "stage:update-draft-start",
    "updateDraft",
    "stage:update-draft-finished",
    "persist",
    "dispatch",
    "stage:post-process-complete",
    "stage:publish-to-materials",
    "publish:doc-1",
  ]);
});

test("completeUploadedProjectFileUpload publishes pptx imports immediately when no AI post-process runtime is attached", async () => {
  const calls: string[] = [];

  await completeUploadedProjectFileUpload({
    originalFileName: "deck.pptx",
    projectId: "project-1",
    uploadedFile: {
      type: "slides",
      cacheKey: "cache-1",
      name: "deck",
      documentId: "doc-1",
    },
  }, {
    publishUploadedProjectFileToMaterials: async (_projectId, file) => {
      calls.push(`publish:${file.documentId ?? "none"}`);
    },
    logPowerPointImportStage: (stage: string) => {
      calls.push(`stage:${stage}`);
    },
  });

  assert.deepEqual(calls, [
    "publish:doc-1",
  ]);
});

test("completeUploadedProjectFileUpload does not publish pptx imports when slides normalization fails", async () => {
  const calls: string[] = [];
  const runtime = buildRuntime(calls);
  runtime.powerPointImportPostProcessRuntime.normalizeMarkdownToSlides = async () => {
    calls.push("normalize");
    throw new Error("normalize failed");
  };
  const originalWarn = console.warn;
  console.warn = () => {};

  try {
    await assert.rejects(
      completeUploadedProjectFileUpload({
        originalFileName: "deck.pptx",
        projectId: "project-1",
        uploadedFile: {
          type: "slides",
          cacheKey: "cache-1",
          name: "deck",
          documentId: "doc-1",
        },
      }, runtime),
      /normalize failed/,
    );

    assert.equal(calls.includes("publish:doc-1"), false);
  } finally {
    console.warn = originalWarn;
  }
});

test("completeUploadedProjectFileUpload still publishes a pptx import after the delayed post-process exits on an empty draft", async () => {
  const calls: string[] = [];
  const runtime = buildRuntime(calls);
  runtime.powerPointImportPostProcessRuntime.getDocument = async () => {
    calls.push("getDocument");
    return {
      draftContent: "   ",
      version: 2,
    };
  };

  await completeUploadedProjectFileUpload({
    originalFileName: "deck.pptx",
    projectId: "project-1",
    uploadedFile: {
      type: "slides",
      cacheKey: "cache-1",
      name: "deck",
      documentId: "doc-1",
    },
  }, runtime);

  assert.deepEqual(calls, [
    "stage:post-process-check",
    "stage:fetch-draft-start",
    "getDocument",
    "stage:fetch-draft-finished",
    "stage:post-process-skip-empty-draft",
    "stage:publish-to-materials",
    "publish:doc-1",
  ]);
});

test("completeUploadedProjectFileUpload keeps pptx imports unpublished when slides normalization times out", async () => {
  const calls: string[] = [];
  const runtime = buildRuntime(calls);
  runtime.powerPointImportPostProcessRuntime.normalizeMarkdownToSlides = async () => {
    calls.push("normalize");
    return withTimeout(
      new Promise<never>(() => {}),
      10,
      "normalize timeout",
    );
  };
  const originalWarn = console.warn;
  console.warn = () => {};

  try {
    await assert.rejects(
      completeUploadedProjectFileUpload({
        originalFileName: "deck.pptx",
        projectId: "project-1",
        uploadedFile: {
          type: "slides",
          cacheKey: "cache-1",
          name: "deck",
          documentId: "doc-1",
        },
      }, runtime),
      (error: unknown) =>
        error instanceof Error && error.message === "normalize timeout",
    );

    assert.equal(calls.includes("publish:doc-1"), false);
  } finally {
    console.warn = originalWarn;
  }
});
