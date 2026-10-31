import { describe, expect, test } from "vitest";
import {
  largeDeckSlideCountBucket,
  parseSlideCountLabel,
} from "@/src/components/_shared/monitoring/useProductPerformanceSignals";

describe("product performance signal dimensions", () => {
  test("reads localized slide-count labels without depending on copy", () => {
    expect(parseSlideCountLabel("50 slides")).toBe(50);
    expect(parseSlideCountLabel("共 1,024 页")).toBe(1024);
    expect(parseSlideCountLabel("slides")).toBeNull();
  });

  test("uses bounded large-deck cardinality buckets", () => {
    expect(largeDeckSlideCountBucket(49)).toBe("under-50");
    expect(largeDeckSlideCountBucket(50)).toBe("50-99");
    expect(largeDeckSlideCountBucket(100)).toBe("100+");
  });
});
