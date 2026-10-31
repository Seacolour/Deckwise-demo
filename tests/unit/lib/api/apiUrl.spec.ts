import { describe, expect, test } from "vitest";
import { getDeleteProjectUrl } from "@/src/lib/api/generated/project-api";
import { joinApiUrl } from "@/src/lib/api/apiUrl";

describe("API URL composition", () => {
  test("routes generated project endpoints through the browser API proxy", () => {
    const generatedPath = getDeleteProjectUrl("smoke-project");

    expect(generatedPath).toBe("/projects/smoke-project");
    expect(joinApiUrl("/api", generatedPath)).toBe("/api/projects/smoke-project");
  });

  test("normalizes a trailing base slash without changing absolute paths", () => {
    expect(joinApiUrl("/api/", "/projects/1")).toBe("/api/projects/1");
    expect(joinApiUrl("", "https://api.example.test/projects/1"))
      .toBe("https://api.example.test/projects/1");
  });
});
