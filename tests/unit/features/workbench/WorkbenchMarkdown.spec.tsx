// @vitest-environment jsdom

import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next-intl", () => ({ useTranslations: () => (key: string) => key }));

import { WorkbenchMarkdown } from "@/src/features/workbench/WorkbenchMarkdown";

describe("workbench Markdown bridge", () => {
  it("renders GFM tables, code blocks, and math", () => {
    const { container } = render(<WorkbenchMarkdown text={[
      "## Draft",
      "",
      "| Item | Value |",
      "| --- | --- |",
      "| One | Two |",
      "",
      "```ts",
      "const answer = 42;",
      "```",
      "",
      "$x^2$",
    ].join("\n")} />);

    expect(screen.getByRole("heading", { name: "Draft" })).toBeInTheDocument();
    expect(screen.getByRole("table")).toHaveTextContent("One");
    expect(container.querySelector("pre")).toHaveTextContent("const answer = 42;");
    expect(screen.getByRole("button", { name: "copyAria" })).toBeInTheDocument();
    expect(container.querySelector(".katex")).not.toBeNull();
  });

  it("does not execute raw HTML or unsafe links from a model", () => {
    const { container } = render(<WorkbenchMarkdown text={
      '<img src="x" onerror="alert(1)">\n\n[unsafe](javascript:alert(1))'
    } />);

    expect(container.querySelector("img")).toBeNull();
    const link = container.querySelector("a");
    expect(link).not.toBeNull();
    expect(link?.getAttribute("href") ?? "").not.toMatch(/^javascript:/i);
    expect(container.querySelector("[node]")).toBeNull();
  });

  it("hides transport-only thinking placeholders", () => {
    const { container } = render(<WorkbenchMarkdown text="__THINKING__" />);
    expect(container).toBeEmptyDOMElement();
  });
});
