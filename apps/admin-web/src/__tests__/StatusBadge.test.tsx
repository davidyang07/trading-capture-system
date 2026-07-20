import { render, screen } from "@testing-library/react";
import { axe } from "jest-axe";
import { StatusBadge } from "@/components/StatusBadge";

describe("StatusBadge", () => {
  it("conveys state with a text label, not color alone", () => {
    render(<StatusBadge value="PARTIALLY_FILLED" />);
    // The humanized label must be present as text (color-independent, WCAG 1.4.1).
    expect(screen.getByText("Partially Filled")).toBeInTheDocument();
  });

  it("renders a decorative icon marked aria-hidden", () => {
    const { container } = render(<StatusBadge value="FILLED" />);
    const icon = container.querySelector("i");
    expect(icon).toHaveAttribute("aria-hidden", "true");
  });

  it("has no accessibility violations", async () => {
    const { container } = render(<StatusBadge value="ACTIVE" />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
