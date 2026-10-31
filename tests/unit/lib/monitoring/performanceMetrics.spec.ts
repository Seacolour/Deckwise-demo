import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import {
  normalizePerformanceRoute,
  reportBusinessOperationDuration,
  reportPerformanceDistribution,
  resolveClientPerformanceAttributes,
} from "@/src/lib/monitoring/performanceMetrics";

beforeEach(() => {
  vi.spyOn(console, "log").mockImplementation(() => undefined);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("performance metric route normalization", () => {
  test("removes high-cardinality identifiers while preserving route shape", () => {
    expect(normalizePerformanceRoute("/canvas/123/documents/550e8400-e29b-41d4-a716-446655440000"))
      .toBe("/canvas/:id/documents/:id");
    expect(normalizePerformanceRoute("/app/project/aBcDeFgHiJkLmNoPqRsTuVwXyZ12"))
      .toBe("/app/project/:id");
  });

  test("removes supported locale prefixes before applying the route contract", () => {
    expect(normalizePerformanceRoute("/zh/canvas/123"))
      .toBe("/canvas/:id");
    expect(normalizePerformanceRoute("/pt-BR/app/settings/account"))
      .toBe("/app/settings/account");
    expect(normalizePerformanceRoute("/zh-Hant"))
      .toBe("/");
  });

  test("preserves stable route names", () => {
    expect(normalizePerformanceRoute("/app/settings/account")).toBe("/app/settings/account");
    expect(normalizePerformanceRoute("/")).toBe("/");
  });

  test("builds bounded device, network, and viewport dimensions", () => {
    expect(resolveClientPerformanceAttributes({
      connection: {
        downlink: 7.2,
        effectiveType: "4g",
        rtt: 145,
        saveData: false,
      },
      deviceMemory: 8,
      devicePixelRatio: 2,
      hardwareConcurrency: 10,
      maxTouchPoints: 5,
      viewportHeight: 820,
      viewportWidth: 1180,
    })).toEqual({
      deviceClass: "tablet",
      deviceMemoryBucket: "5-8gb",
      devicePixelRatioBucket: "2x",
      hardwareConcurrencyBucket: "9+",
      networkDownlinkBucket: "5-9mbps",
      networkEffectiveType: "4g",
      networkRttBucket: "100-299ms",
      networkSaveData: false,
      viewportBucket: "lg",
      viewportHeightBucket: "medium",
    });
  });

  test("logs finite distributions locally with bounded attributes", () => {
    expect(reportBusinessOperationDuration("pptx_export", 125.5, {
      outcome: "success",
      optional: undefined,
    })).toBe(true);

    expect(console.log).toHaveBeenCalledWith(
      "[Performance] deckwise.business_operation.duration: 125.5 millisecond",
      expect.objectContaining({
        operation: "pptx_export",
        outcome: "success",
        route: "server",
      }),
    );
    expect(vi.mocked(console.log).mock.calls[0]?.[1])
      .not.toHaveProperty("optional");
  });

  test("drops invalid samples without logging", () => {
    expect(reportPerformanceDistribution("deckwise.test", Number.NaN)).toBe(false);
    expect(console.log).not.toHaveBeenCalled();
  });
});
