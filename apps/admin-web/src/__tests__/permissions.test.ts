import {
  canManageAccounts,
  canManageReferenceData,
  canWriteExecutions,
  isAdmin,
  isInvestor,
} from "@/lib/permissions";
import { routesForRole } from "@/routes";
import { sanitizeRelativePath } from "@/lib/redirect";

const admin = { roles: ["ADMIN"] };
const investor = { roles: ["INVESTOR"] };

describe("permissions", () => {
  it("distinguishes admin from investor", () => {
    expect(isAdmin(admin)).toBe(true);
    expect(isAdmin(investor)).toBe(false);
    expect(isInvestor(investor)).toBe(true);
    expect(isAdmin(null)).toBe(false);
  });

  it("gates execution writes and reference mutations to admins", () => {
    expect(canWriteExecutions(admin)).toBe(true);
    expect(canWriteExecutions(investor)).toBe(false);
    expect(canManageReferenceData(admin)).toBe(true);
    expect(canManageReferenceData(investor)).toBe(false);
  });

  it("gates ledger posts and account lifecycle changes to admins", () => {
    expect(canManageAccounts(admin)).toBe(true);
    expect(canManageAccounts(investor)).toBe(false);
    expect(canManageAccounts(null)).toBe(false);
  });
});

describe("routesForRole", () => {
  it("hides admin-only entries from investors", () => {
    const investorRoutes = routesForRole(false).map((r) => r.url);
    expect(investorRoutes).not.toContain("/reference");
    expect(investorRoutes).toContain("/orders");

    const adminRoutes = routesForRole(true).map((r) => r.url);
    expect(adminRoutes).toContain("/reference");
  });
});

describe("sanitizeRelativePath (open-redirect guard)", () => {
  it("accepts same-origin relative paths", () => {
    expect(sanitizeRelativePath("/orders")).toBe("/orders");
    expect(sanitizeRelativePath("/accounts/ACC-1?tab=cash")).toBe(
      "/accounts/ACC-1?tab=cash"
    );
  });

  it("rejects absolute and protocol-relative destinations", () => {
    expect(sanitizeRelativePath("https://evil.example/x")).toBe("/dashboard");
    expect(sanitizeRelativePath("//evil.example")).toBe("/dashboard");
    expect(sanitizeRelativePath("/\\evil.example")).toBe("/dashboard");
    expect(sanitizeRelativePath(undefined)).toBe("/dashboard");
  });

  it("rejects paths a browser would turn into another origin", () => {
    // Browsers drop tabs and newlines from URLs: "/\t/evil" is "//evil".
    expect(sanitizeRelativePath("/\t/evil.example")).toBe("/dashboard");
    expect(sanitizeRelativePath("/\n/evil.example")).toBe("/dashboard");
    expect(sanitizeRelativePath("/\r\n/evil.example")).toBe("/dashboard");
    expect(sanitizeRelativePath("/ /evil.example")).toBe("/dashboard");
    expect(sanitizeRelativePath("/\u0000/evil.example")).toBe("/dashboard");
    expect(sanitizeRelativePath(decodeURIComponent("/%09/evil.example"))).toBe(
      "/dashboard"
    );
  });

  it("keeps encoded characters that stay on this origin", () => {
    expect(sanitizeRelativePath("/orders?q=a%20b")).toBe("/orders?q=a%20b");
    expect(sanitizeRelativePath("/%2F%2Fevil.example")).toBe(
      "/%2F%2Fevil.example"
    );
  });
});
