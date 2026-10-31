import { describe, expect, it, vi } from "vitest";
import { metadata } from "@/app/login/layout";

vi.mock("next/font/local", () => ({
  default: () => ({
    className: "mock-font",
    style: { fontFamily: "Mock Inter" },
    variable: "--font-ui",
  }),
}));

describe("login metadata", () => {
  it("does not advertise the private Demo login page to crawlers", () => {
    expect(metadata.title).toBe("Deckwise Demo — Sign in");
    expect(metadata.robots).toEqual({ index: false, follow: false });
    expect(metadata.alternates).toBeUndefined();
  });
});
