// @vitest-environment jsdom

import { useState } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

function CounterButton() {
  const [count, setCount] = useState(0);

  return (
    <button type="button" onClick={() => setCount((value) => value + 1)}>
      Count: {count}
    </button>
  );
}

describe("testing library harness", () => {
  it("renders React components in jsdom and supports user interaction", async () => {
    render(<CounterButton />);

    const button = screen.getByRole("button", { name: "Count: 0" });
    expect(button).toBeInTheDocument();

    fireEvent.click(button);

    expect(screen.getByRole("button", { name: "Count: 1" })).toBeInTheDocument();
  });
});
