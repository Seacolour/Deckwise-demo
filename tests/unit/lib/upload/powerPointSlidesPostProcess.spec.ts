import assert from "node:assert/strict";
import { test } from "vitest";
import {
  resolvePowerPointImportPlan,
  runPowerPointImportPostProcess,
  shouldAutoNormalizeImportedPowerPoint,
} from "@/src/lib/upload/powerPointSlidesPostProcess.ts";

test("shouldAutoNormalizeImportedPowerPoint enables post-process for ppt/pptx imports that became text drafts", () => {
  assert.equal(
    shouldAutoNormalizeImportedPowerPoint({
      originalFileName: "deck.pptx",
      uploadedFile: {
        type: "slides",
        documentId: "doc-1",
      },
    }),
    true,
  );

  assert.equal(
    shouldAutoNormalizeImportedPowerPoint({
      originalFileName: "deck.md",
      uploadedFile: {
        type: "slides",
        documentId: "doc-1",
      },
    }),
    false,
  );

  assert.equal(
    shouldAutoNormalizeImportedPowerPoint({
      originalFileName: "deck.pptx",
      uploadedFile: {
        type: "note",
        documentId: "doc-1",
      },
    }),
    true,
  );

  assert.equal(
    shouldAutoNormalizeImportedPowerPoint({
      originalFileName: "deck.pptx",
      uploadedFile: {
        type: "slides",
        documentId: null,
      },
    }),
    false,
  );
});

test("resolvePowerPointImportPlan defers sidebar publish for every ppt/pptx source while only normalizing text drafts", () => {
  assert.deepEqual(
    resolvePowerPointImportPlan({
      originalFileName: "deck.pptx",
      uploadedFile: {
        type: "slides",
        documentId: "doc-1",
      },
    }),
    {
      isPowerPointImport: true,
      shouldNormalize: true,
      shouldDelaySidebarPublish: true,
    },
  );

  assert.deepEqual(
    resolvePowerPointImportPlan({
      originalFileName: "deck.pptx",
      uploadedFile: {
        type: "image",
        documentId: "doc-1",
      },
    }),
    {
      isPowerPointImport: true,
      shouldNormalize: false,
      shouldDelaySidebarPublish: true,
    },
  );

  assert.deepEqual(
    resolvePowerPointImportPlan({
      originalFileName: "notes.md",
      uploadedFile: {
        type: "note",
        documentId: "doc-1",
      },
    }),
    {
      isPowerPointImport: false,
      shouldNormalize: false,
      shouldDelaySidebarPublish: false,
    },
  );
});

test("runPowerPointImportPostProcess rewrites the fetched draft before the upload pipeline can publish it", async () => {
  const calls: string[] = [];
  const stages: string[] = [];

  await runPowerPointImportPostProcess({
    originalFileName: "deck.pptx",
    uploadedFile: {
      type: "slides",
      cacheKey: "cache-1",
      name: "deck",
      projectId: "project-1",
      url: "https://example.test/deck.md",
      documentId: "doc-1",
    },
    runtime: {
      getDocument: async (documentId, opts) => {
        calls.push(`getDocument:${documentId}:${String(opts.includeVersions)}`);
        return {
          draftContent: "# raw pandoc markdown",
          version: 2,
        };
      },
      normalizeMarkdownToSlides: async (input) => {
        calls.push(`normalize:${input.deckTitle}`);
        return {
          text: "---\nlayout: cover\n---\n# deck",
        };
      },
      updateDraft: async (documentId, input) => {
        calls.push(`updateDraft:${documentId}:${input.version}`);
        return {
          draftContent: input.draftContent,
          version: 3,
        };
      },
      persistFetchedNoteContent: async (input) => {
        calls.push(`persist:${input.documentId}:${input.version}`);
      },
      dispatchNoteContentUpdate: (detail) => {
        calls.push(`dispatch:${detail.documentId}:${detail.version}`);
      },
    },
    logStage: (stage) => {
      stages.push(stage);
    },
  });

  assert.deepEqual(calls, [
    "getDocument:doc-1:false",
    "normalize:deck",
    "updateDraft:doc-1:2",
    "persist:doc-1:3",
    "dispatch:doc-1:3",
  ]);
  assert.deepEqual(stages, [
    "post-process-check",
    "fetch-draft-start",
    "fetch-draft-finished",
    "normalize-to-slides-start",
    "normalize-to-slides-finished",
    "update-draft-start",
    "update-draft-finished",
    "post-process-complete",
  ]);
});
