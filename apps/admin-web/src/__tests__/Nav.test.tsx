import { render, screen } from "@testing-library/react";
import { axe } from "jest-axe";
import { useSession } from "next-auth/react";
import { Nav } from "@/components/Layout/Nav";

jest.mock("next/navigation", () => ({
  usePathname: () => "/dashboard",
}));

jest.mock("next-auth/react", () => ({
  useSession: jest.fn(),
}));

const mockUseSession = useSession as jest.Mock;

describe("role-aware navigation", () => {
  it("hides Reference Data from investors", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["INVESTOR"] } });
    render(<Nav />);
    expect(screen.getByRole("link", { name: /orders/i })).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /reference data/i })
    ).not.toBeInTheDocument();
  });

  it("shows Reference Data to admins", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMIN"] } });
    render(<Nav />);
    expect(
      screen.getByRole("link", { name: /reference data/i })
    ).toBeInTheDocument();
  });

  it("marks the active route with aria-current", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMIN"] } });
    render(<Nav />);
    const active = screen.getByRole("link", { name: /dashboard/i });
    expect(active).toHaveAttribute("aria-current", "page");
  });

  it("has no automated accessibility violations for investor navigation", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["INVESTOR"] } });
    const { container } = render(<Nav />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
