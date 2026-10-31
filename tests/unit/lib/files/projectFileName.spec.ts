import assert from "node:assert/strict";
import { test } from "vitest";
import {
  getProjectFileExtensionSuffix,
  normalizeProjectFileLookupName,
  stripProjectFileExtension,
  toProjectCanonicalJsonFileName,
  toProjectDeckPackageFileName,
  toProjectFileDisplayName,
  toProjectOutlineFileName,
} from "@/src/lib/files/projectFileName.ts";

test("stripProjectFileExtension removes known simple and compound suffixes", () => {
  assert.equal(stripProjectFileExtension("deck.deck.json"), "deck");
  assert.equal(stripProjectFileExtension("deck.deck"), "deck");
  assert.equal(stripProjectFileExtension("deck.mslides"), "deck");
  assert.equal(stripProjectFileExtension("deck.deck-outline.json"), "deck");
  assert.equal(stripProjectFileExtension("slides-20260326-141648.pptx"), "slides-20260326-141648");
  assert.equal(stripProjectFileExtension("photo.final.png"), "photo.final");
  assert.equal(stripProjectFileExtension("report.v2"), "report.v2");
  assert.equal(getProjectFileExtensionSuffix("deck.deck.json"), ".deck.json");
  assert.equal(getProjectFileExtensionSuffix("deck.deck-outline.json"), ".deck-outline.json");
});

test("each Deckwise representation gets an explicit canonical suffix", () => {
  assert.equal(toProjectCanonicalJsonFileName("Quarterly"), "Quarterly.deck.json");
  assert.equal(toProjectCanonicalJsonFileName("Quarterly.mslides"), "Quarterly.deck.json");
  assert.equal(toProjectDeckPackageFileName("Quarterly.deck.json"), "Quarterly.deck");
  assert.equal(toProjectOutlineFileName("Quarterly.deck.json"), "Quarterly.deck-outline.json");
});

test("display and lookup normalization keep legacy suffix and suffix-free names aligned", () => {
  assert.equal(toProjectFileDisplayName("draft.docx"), "draft");
  assert.equal(toProjectFileDisplayName("draft"), "draft");
  assert.equal(normalizeProjectFileLookupName("draft.docx"), "draft");
  assert.equal(normalizeProjectFileLookupName("draft"), "draft");
});
