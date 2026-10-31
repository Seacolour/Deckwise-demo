import assert from "node:assert/strict";
import { test } from "vitest";
import {
  isSlideFileName,
  makeTextFileName,
  resolveProjectFileKind,
  resolveUploadedProjectFileKind,
} from "@/src/lib/files/projectFileKinds.ts";
import {
  isUnsupportedSpreadsheetFileName,
  isUnsupportedSpreadsheetSemanticType,
} from "@/src/lib/files/unsupportedSpreadsheetFiles.ts";

test("isSlideFileName recognizes package, canonical JSON, and legacy deck suffixes", () => {
  assert.equal(isSlideFileName("demo.deck.json"), true);
  assert.equal(isSlideFileName("demo.deck"), true);
  assert.equal(isSlideFileName("demo.mslides"), true);
  assert.equal(isSlideFileName("demo.json"), false);
  assert.equal(isSlideFileName("demo.md"), false);
});

test("makeTextFileName uses canonical default names", () => {
  const now = new Date(2026, 2, 18, 10, 11, 12);

  assert.equal(makeTextFileName("slides", now), "slides-20260318-101112.deck.json");
  assert.equal(makeTextFileName("note", now), "New note.md");
});

test("resolveProjectFileKind distinguishes deck files from normal .md notes", () => {
  assert.equal(resolveProjectFileKind("deck.deck.json"), "slides");
  assert.equal(resolveProjectFileKind("deck.deck"), "slides");
  assert.equal(resolveProjectFileKind("deck.deck-outline.json"), "slides");
  assert.equal(resolveProjectFileKind("deck.mslides"), "slides");
  assert.equal(resolveProjectFileKind("deck.pptx"), "slides");
  assert.equal(resolveProjectFileKind("budget.xlsx"), "sheet");
  assert.equal(resolveProjectFileKind("notes.md"), "note");
  assert.equal(resolveProjectFileKind("meeting.docx"), "note");
});

test("resolveUploadedProjectFileKind prefers the backend-converted file name and never leaves docx/pptx as office", () => {
  assert.equal(
    resolveUploadedProjectFileKind({
      requestedKind: "office",
      originalFileName: "deck.pptx",
      uploadedFileName: "deck.mslides",
    }),
    "slides",
  );

  assert.equal(
    resolveUploadedProjectFileKind({
      requestedKind: "office",
      originalFileName: "deck.pptx",
      uploadedFileName: "deck.md",
    }),
    "slides",
  );

  assert.equal(
    resolveUploadedProjectFileKind({
      requestedKind: "office",
      originalFileName: "draft.docx",
      uploadedFileName: "draft.md",
    }),
    "note",
  );

  assert.equal(
    resolveUploadedProjectFileKind({
      requestedKind: "office",
      originalFileName: "draft.docx",
    }),
    "note",
  );

  assert.equal(
    resolveUploadedProjectFileKind({
      requestedKind: "sheet",
      originalFileName: "budget.xlsx",
      uploadedFileName: "budget.json",
    }),
    "sheet",
  );
});

test("only legacy spreadsheet filenames and semantic types are blocked outside project kind routing", () => {
  assert.equal(isUnsupportedSpreadsheetFileName("table.csv"), true);
  assert.equal(isUnsupportedSpreadsheetFileName("table.xls"), true);
  assert.equal(isUnsupportedSpreadsheetFileName("table.xlsx"), false);
  assert.equal(isUnsupportedSpreadsheetFileName("notes.md"), false);
  assert.equal(isUnsupportedSpreadsheetSemanticType("spreadsheet"), false);
  assert.equal(isUnsupportedSpreadsheetSemanticType("csv"), true);
  assert.equal(isUnsupportedSpreadsheetSemanticType("slides"), false);
});
