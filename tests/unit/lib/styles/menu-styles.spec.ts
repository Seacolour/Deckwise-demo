import assert from "node:assert/strict";
import { test } from "vitest";

import {
  resolveFloatingMenuLayout,
  resolveIconTriggerFloatingMenuLayout,
  resolveSideFloatingMenuLayout,
} from "@/src/lib/styles/menu-styles.ts";

test("resolveFloatingMenuLayout keeps a menu below when there is enough space", () => {
  const layout = resolveFloatingMenuLayout({
    preferredLeft: 420,
    anchorTop: 200,
    anchorBottom: 224,
    menuWidth: 200,
    menuHeight: 180,
    viewportWidth: 1280,
    viewportHeight: 900,
    edgeGapPx: 8,
    offsetPx: 6,
    preferredPlacement: "below",
  });

  assert.deepEqual(layout, {
    left: 420,
    top: 230,
    placement: "below",
  });
});

test("resolveFloatingMenuLayout flips a menu above when below would overflow and above has more room", () => {
  const layout = resolveFloatingMenuLayout({
    preferredLeft: 420,
    anchorTop: 640,
    anchorBottom: 664,
    menuWidth: 200,
    menuHeight: 220,
    viewportWidth: 1280,
    viewportHeight: 800,
    edgeGapPx: 8,
    offsetPx: 6,
    preferredPlacement: "below",
  });

  assert.deepEqual(layout, {
    left: 420,
    top: 414,
    placement: "above",
  });
});

test("resolveFloatingMenuLayout clamps inside the viewport when neither side fully fits", () => {
  const layout = resolveFloatingMenuLayout({
    preferredLeft: 980,
    anchorTop: 180,
    anchorBottom: 204,
    menuWidth: 260,
    menuHeight: 500,
    viewportWidth: 1024,
    viewportHeight: 600,
    edgeGapPx: 12,
    offsetPx: 10,
    preferredPlacement: "below",
  });

  assert.deepEqual(layout, {
    left: 752,
    top: 88,
    placement: "below",
  });
});

test("resolveIconTriggerFloatingMenuLayout opens from trigger lower-right by default", () => {
  const layout = resolveIconTriggerFloatingMenuLayout({
    anchorRect: {
      top: 120,
      right: 220,
      bottom: 144,
    },
    menuWidth: 240,
    menuHeight: 180,
    viewportWidth: 1280,
    viewportHeight: 900,
    edgeGapPx: 12,
  });

  assert.deepEqual(layout, {
    left: 228,
    top: 152,
    placement: "below",
  });
});

test("resolveIconTriggerFloatingMenuLayout clamps horizontally near the right edge", () => {
  const layout = resolveIconTriggerFloatingMenuLayout({
    anchorRect: {
      top: 120,
      right: 1000,
      bottom: 144,
    },
    menuWidth: 240,
    menuHeight: 180,
    viewportWidth: 1024,
    viewportHeight: 900,
    edgeGapPx: 12,
  });

  assert.deepEqual(layout, {
    left: 772,
    top: 152,
    placement: "below",
  });
});

test("resolveSideFloatingMenuLayout opens to the preferred side when there is room", () => {
  const layout = resolveSideFloatingMenuLayout({
    anchorRect: {
      top: 240,
      left: 360,
      right: 384,
      bottom: 264,
    },
    menuWidth: 180,
    menuHeight: 140,
    viewportWidth: 1280,
    viewportHeight: 900,
    edgeGapPx: 8,
    offsetPx: 4,
    preferredPlacement: "left",
  });

  assert.deepEqual(layout, {
    left: 176,
    top: 240,
    placement: "left",
  });
});

test("resolveSideFloatingMenuLayout flips when the preferred side has less room", () => {
  const layout = resolveSideFloatingMenuLayout({
    anchorRect: {
      top: 160,
      left: 72,
      right: 96,
      bottom: 184,
    },
    menuWidth: 180,
    menuHeight: 140,
    viewportWidth: 720,
    viewportHeight: 600,
    edgeGapPx: 8,
    offsetPx: 4,
    preferredPlacement: "left",
  });

  assert.deepEqual(layout, {
    left: 100,
    top: 160,
    placement: "right",
  });
});

test("resolveSideFloatingMenuLayout clamps vertically inside the viewport", () => {
  const layout = resolveSideFloatingMenuLayout({
    anchorRect: {
      top: 540,
      left: 360,
      right: 384,
      bottom: 564,
    },
    menuWidth: 180,
    menuHeight: 140,
    viewportWidth: 720,
    viewportHeight: 600,
    edgeGapPx: 8,
    offsetPx: 4,
    preferredPlacement: "left",
  });

  assert.deepEqual(layout, {
    left: 176,
    top: 452,
    placement: "left",
  });
});
