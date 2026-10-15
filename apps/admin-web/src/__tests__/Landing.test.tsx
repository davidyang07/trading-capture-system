import { render, screen, within } from "@testing-library/react";
import { axe } from "jest-axe";
import { Landing } from "@/components/Landing/Landing";

describe("Landing", () => {
  it("has one page heading and a heading for every section", () => {
    render(<Landing signInAction={jest.fn()} />);
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(
      screen.getByRole("heading", { level: 1, name: /capture every trade/i })
    ).toBeInTheDocument();
    for (const name of [
      "One console for the whole post-trade flow",
      "From fill to settlement in four steps",
      "Correct by construction",
      "The right controls for each person",
      "Open the console",
    ]) {
      expect(screen.getByRole("heading", { level: 2, name })).toBeInTheDocument();
    }
  });

  it("links the section navigation to sections that exist", () => {
    const { container } = render(<Landing signInAction={jest.fn()} />);
    const nav = screen.getByRole("navigation", { name: "Sections" });
    for (const link of within(nav).getAllByRole("link")) {
      const id = link.getAttribute("href")?.replace("#", "") ?? "";
      expect(container.querySelector(`#${id}`)).not.toBeNull();
    }
  });

  it("makes every sign-in button submit a form (the route wires the server action)", () => {
    // jsdom runs React 18, which cannot invoke function form actions; the real redirect to
    // Keycloak is covered end to end in e2e/auth.spec.ts.
    render(<Landing signInAction={jest.fn()} />);
    const buttons = screen.getAllByRole("button", { name: /sign in/i });
    expect(buttons).toHaveLength(3);
    for (const button of buttons) {
      expect(button).toHaveAttribute("type", "submit");
      expect(button.closest("form")).not.toBeNull();
    }
  });

  it("describes the decorative console preview for assistive technology", () => {
    render(<Landing signInAction={jest.fn()} />);
    expect(
      screen.getByRole("img", { name: /preview of the executions console/i })
    ).toBeInTheDocument();
  });

  it("has no detectable accessibility violations", async () => {
    const { container } = render(<Landing signInAction={jest.fn()} />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
