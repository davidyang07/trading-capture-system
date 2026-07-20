import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { useSession } from "next-auth/react";
import { Header } from "@/components/Layout/Header";

jest.mock("next-auth/react", () => ({
  useSession: jest.fn(),
}));

const mockUseSession = useSession as jest.Mock;

describe("Header", () => {
  it("shows the username and role for an authenticated admin", () => {
    mockUseSession.mockReturnValue({
      status: "authenticated",
      data: { user: { username: "tcs-admin" }, roles: ["ADMIN"] },
    });
    render(<Header />);
    expect(screen.getByText("tcs-admin")).toBeInTheDocument();
    expect(screen.getByText(/Administrator/)).toBeInTheDocument();
  });

  it("routes logout through the server-side federated-logout endpoint", async () => {
    mockUseSession.mockReturnValue({
      status: "authenticated",
      data: { user: { username: "investor1" }, roles: ["INVESTOR"] },
    });
    const original = window.location;
    const assign = jest.fn();
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { href: "http://localhost/", origin: "http://localhost", assign },
    });

    render(<Header />);
    await userEvent.click(screen.getByRole("button", { name: /log out/i }));
    // Full-page navigation clears all in-memory (user-scoped) state on logout.
    expect(assign).toHaveBeenCalledWith("/api/auth/logout");

    Object.defineProperty(window, "location", {
      configurable: true,
      value: original,
    });
  });

  it("renders nothing user-specific when unauthenticated", () => {
    mockUseSession.mockReturnValue({ status: "unauthenticated", data: null });
    render(<Header />);
    expect(
      screen.queryByRole("button", { name: /log out/i })
    ).not.toBeInTheDocument();
  });

  it("does not expose stale user controls while the session is loading", () => {
    mockUseSession.mockReturnValue({ status: "loading", data: null });
    render(<Header />);
    expect(
      screen.queryByRole("button", { name: /log out/i })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/Administrator|Investor/)
    ).not.toBeInTheDocument();
  });

  it("has no automated accessibility violations when authenticated", async () => {
    mockUseSession.mockReturnValue({
      status: "authenticated",
      data: { user: { username: "investor1" }, roles: ["INVESTOR"] },
    });
    const { container } = render(<Header />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
