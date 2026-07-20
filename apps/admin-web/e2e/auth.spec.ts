import { test, expect } from "@playwright/test";
import { login, logout } from "./helpers";
import { ADMIN, INVESTOR1 } from "./credentials";

test.describe("authentication & authorization", () => {
  test("unauthenticated user is redirected to login", async ({ page }) => {
    await page.goto("/orders");
    await page.waitForURL(/\/login\?callbackUrl=/);
    await expect(
      page.getByRole("button", { name: /sign in with keycloak/i })
    ).toBeVisible();
  });

  test("expired-session state is announced safely", async ({ page }) => {
    await page.goto("/login?error=SessionRequired");
    await expect(
      page
        .getByRole("alert")
        .filter({ hasText: "Your session has ended. Please sign in again." })
    ).toBeVisible();
  });

  test("investor can sign in and reach the dashboard", async ({ page }) => {
    await login(page, INVESTOR1());
    await expect(page.getByText("investor1")).toBeVisible();
    // Investor does not see the admin-only Reference Data nav entry.
    await expect(
      page.getByRole("link", { name: /reference data/i })
    ).toHaveCount(0);
  });

  test("administrator sees admin-only navigation", async ({ page }) => {
    await login(page, ADMIN());
    await expect(
      page.getByRole("link", { name: /reference data/i })
    ).toBeVisible();
  });

  test("investor is blocked from an admin-only route (403)", async ({
    page,
  }) => {
    await login(page, INVESTOR1());
    await page.goto("/reference");
    // Middleware redirects to the 403 page (server-side authorization still enforces).
    await page.waitForURL(/\/403/);
  });

  test("logout ends the session and returns to login", async ({ page }) => {
    await login(page, INVESTOR1());
    await logout(page);
    // Navigating back into the app requires re-authentication.
    await page.goto("/dashboard");
    await page.waitForURL(/\/login/);
  });

  test("open-redirect callbackUrl is neutralized", async ({ page }) => {
    // A hostile callbackUrl must not become the post-login destination.
    const user = INVESTOR1();
    await page.goto("/login?callbackUrl=https://evil.example/pwn");
    await page.getByRole("button", { name: /sign in with keycloak/i }).click();
    await page.waitForURL(/\/realms\/tcs\/protocol\/openid-connect\/auth/);
    await page.locator("#username").fill(user.username);
    await page.locator("#password").fill(user.password);
    await page.locator("#kc-login, input[type=submit]").first().click();
    // Lands on the safe same-origin default, never the external host.
    await page.waitForURL(/localhost:\d+\/dashboard/);
    expect(page.url()).not.toContain("evil.example");
  });

  test("authenticated shell remains operable at a narrow viewport", async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await login(page, INVESTOR1());

    await expect(page.getByRole("navigation", { name: "Primary" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();
    await expect(page.getByRole("button", { name: /log out/i })).toBeVisible();
    const hasDocumentOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth
    );
    expect(hasDocumentOverflow).toBe(false);
  });
});
