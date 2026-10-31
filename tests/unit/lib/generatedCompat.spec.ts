import { describe, expect, it } from "vitest";
import {
  unwrapGeneratedBoolean,
  unwrapGeneratedPayload,
} from "@/src/lib/api/generatedCompat";

describe("generatedCompat", () => {
  it("unwraps nested generated payloads", () => {
    const payload = unwrapGeneratedPayload<{ id: string }>({
      data: {
        data: {
          id: "doc-1",
        },
      },
    });

    expect(payload).toEqual({ id: "doc-1" });
  });

  it("normalizes wrapped boolean-like payloads", () => {
    expect(unwrapGeneratedBoolean({ data: { data: true } })).toBe(true);
    expect(unwrapGeneratedBoolean({ data: { data: "false" } })).toBe(false);
    expect(unwrapGeneratedBoolean({ data: { data: "true" } })).toBe(true);
  });
});
