"use client";

import { useEffect } from "react";
import { reportPerformanceDistribution } from "@/src/lib/monitoring/performanceMetrics";

const LARGE_DECK_MIN_SLIDES = 50;
const INTERACTION_SAMPLE_GAP_MS = 5_000;
const SLIDES_EDITOR_SELECTOR = '[data-deckwise-editor-kind="slides"]';

export function parseSlideCountLabel(label: string | null | undefined): number | null {
  const match = label?.replaceAll(",", "").match(/\d+/u);
  if (!match) return null;
  const count = Number(match[0]);
  return Number.isFinite(count) && count >= 0 ? count : null;
}

export function largeDeckSlideCountBucket(slideCount: number): "50-99" | "100+" | "under-50" {
  if (slideCount >= 100) return "100+";
  if (slideCount >= LARGE_DECK_MIN_SLIDES) return "50-99";
  return "under-50";
}

function readSlideCount(editor: Element): number | null {
  return parseSlideCountLabel(editor.querySelector(".slide-rail-status-label")?.textContent);
}

function afterNextPaint(callback: () => void): () => void {
  let secondFrame = 0;
  const firstFrame = window.requestAnimationFrame(() => {
    secondFrame = window.requestAnimationFrame(callback);
  });
  return () => {
    window.cancelAnimationFrame(firstFrame);
    if (secondFrame) window.cancelAnimationFrame(secondFrame);
  };
}

export function useProductPerformanceSignals(): void {
  useEffect(() => {
    let inspectFrame = 0;
    let generationStartedAt: number | null = null;
    let generationFirstVisualReported = false;
    let lastInteractionSampleAt = 0;
    const editorFirstSeenAt = new WeakMap<Element, number>();
    const editorRenderReported = new WeakSet<Element>();
    const pendingPaintCancellations = new Set<() => void>();

    const reportAfterPaint = (callback: () => void) => {
      const cancel = afterNextPaint(() => {
        pendingPaintCancellations.delete(cancel);
        callback();
      });
      pendingPaintCancellations.add(cancel);
    };

    const inspectProductSurfaces = () => {
      inspectFrame = 0;
      const generationActive = Boolean(
        document.querySelector('.project-main-slides-start[data-generating="true"]'),
      );
      if (generationActive && generationStartedAt === null) {
        generationStartedAt = performance.now();
        generationFirstVisualReported = false;
      }

      const firstVisual = document.querySelector(
        '.project-main-slides-streaming-editor-layer[data-visual-state="ready"]',
      );
      if (
        generationStartedAt !== null &&
        !generationFirstVisualReported &&
        firstVisual
      ) {
        const startedAt = generationStartedAt;
        generationFirstVisualReported = true;
        reportAfterPaint(() => {
          reportPerformanceDistribution(
            "deckwise.deck_generation.first_visual",
            performance.now() - startedAt,
            {
              unit: "millisecond",
              attributes: { outcome: "success" },
            },
          );
        });
      }

      if (!generationActive && generationFirstVisualReported) {
        generationStartedAt = null;
        generationFirstVisualReported = false;
      }

      for (const editor of document.querySelectorAll(SLIDES_EDITOR_SELECTOR)) {
        if (!editorFirstSeenAt.has(editor)) {
          editorFirstSeenAt.set(editor, performance.now());
        }
        if (editorRenderReported.has(editor)) continue;
        const slideCount = readSlideCount(editor);
        const editorReady = Boolean(
          editor.querySelector(".editor-shell") &&
          editor.querySelector(".ProseMirror, .workspace-canvas, [data-card-index]"),
        );
        if (slideCount === null || slideCount < LARGE_DECK_MIN_SLIDES || !editorReady) continue;

        editorRenderReported.add(editor);
        const startedAt = editorFirstSeenAt.get(editor) ?? performance.now();
        reportAfterPaint(() => {
          reportPerformanceDistribution(
            "deckwise.editor.large_deck_render_latency",
            performance.now() - startedAt,
            {
              unit: "millisecond",
              attributes: { slideCountBucket: largeDeckSlideCountBucket(slideCount) },
            },
          );
        });
      }
    };

    const scheduleInspect = () => {
      if (!inspectFrame) inspectFrame = window.requestAnimationFrame(inspectProductSurfaces);
    };

    const handleEditorInteraction = (event: Event) => {
      const target = event.target instanceof Element ? event.target : null;
      const editor = target?.closest(SLIDES_EDITOR_SELECTOR);
      if (!editor) return;
      const slideCount = readSlideCount(editor);
      if (slideCount === null || slideCount < LARGE_DECK_MIN_SLIDES) return;
      if (event instanceof KeyboardEvent && (event.key === "Shift" || event.key === "Control" || event.key === "Meta" || event.key === "Alt")) {
        return;
      }
      const startedAt = performance.now();
      if (startedAt - lastInteractionSampleAt < INTERACTION_SAMPLE_GAP_MS) return;
      lastInteractionSampleAt = startedAt;
      reportAfterPaint(() => {
        reportPerformanceDistribution(
          "deckwise.editor.large_deck_interaction_latency",
          performance.now() - startedAt,
          {
            unit: "millisecond",
            attributes: {
              interaction: event.type === "keydown" ? "keyboard" : "pointer",
              slideCountBucket: largeDeckSlideCountBucket(slideCount),
            },
          },
        );
      });
    };

    const observer = new MutationObserver(scheduleInspect);
    observer.observe(document.documentElement, {
      attributeFilter: ["data-generating", "data-visual-state"],
      attributes: true,
      childList: true,
      subtree: true,
    });
    document.addEventListener("keydown", handleEditorInteraction, true);
    document.addEventListener("pointerdown", handleEditorInteraction, true);
    scheduleInspect();

    return () => {
      observer.disconnect();
      document.removeEventListener("keydown", handleEditorInteraction, true);
      document.removeEventListener("pointerdown", handleEditorInteraction, true);
      if (inspectFrame) window.cancelAnimationFrame(inspectFrame);
      for (const cancel of pendingPaintCancellations) cancel();
      pendingPaintCancellations.clear();
    };
  }, []);
}
