import { test } from "vitest";
import assert from "node:assert/strict";
import {
  getRecentFilePreview,
  rememberRecentFilePreview,
} from "@/src/stores/recentFilePreview.ts";

test("recent file preview reopens notes from the last saved viewport", () => {
  rememberRecentFilePreview({
    projectId: "project-a",
    type: "note",
    cacheKey: "paper-1",
    documentId: "doc-1",
    url: "https://example.com/note-1.md",
    scrollTop: 920,
    scrollLeft: 18,
  });

  const preview = getRecentFilePreview({
    projectId: "project-a",
    type: "note",
    cacheKey: "paper-1",
    documentId: "doc-1",
    url: "https://example.com/note-1.md",
  });

  assert.equal(preview?.projectId, "project-a");
  assert.equal(preview?.type, "note");
  assert.equal(preview?.cacheKey, "paper-1");
  assert.equal(preview?.documentId, "doc-1");
  assert.equal(preview?.url, "https://example.com/note-1.md");
  assert.equal(preview?.scrollTop, 920);
  assert.equal(preview?.scrollLeft, 18);
  assert.equal(typeof preview?.updatedAt, "number");
});

test("recent file preview falls back to url identity when cacheKey is missing", () => {
  rememberRecentFilePreview({
    projectId: "project-b",
    type: "slides",
    cacheKey: null,
    documentId: null,
    url: "https://example.com/slides.md",
    scrollTop: 240,
    scrollLeft: 0,
  });

  const preview = getRecentFilePreview({
    projectId: "project-b",
    type: "slides",
    cacheKey: null,
    documentId: null,
    url: "https://example.com/slides.md",
  });

  assert.equal(preview?.scrollTop, 240);
  assert.equal(preview?.url, "https://example.com/slides.md");
});
