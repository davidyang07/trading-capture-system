import { expect, type Locator, type Page } from "@playwright/test";

interface Creds {
  username: string;
  password: string;
}

/**
 * Drives the real Keycloak Authorization-Code + PKCE login through admin-web's BFF.
 * Uses deterministic waits (URL + visible controls), never fixed sleeps.
 */
export async function login(page: Page, user: Creds): Promise<void> {
  await page.goto("/login");
  await page.getByRole("button", { name: /sign in with keycloak/i }).click();

  // Keycloak-hosted login form.
  await page.waitForURL(/\/realms\/tcs\/protocol\/openid-connect\/auth/);
  await page.locator("#username").fill(user.username);
  await page.locator("#password").fill(user.password);
  await page.locator("#kc-login, input[type=submit]").first().click();

  // Back on the app, authenticated.
  await page.waitForURL(/\/dashboard/);
  await expect(
    page.getByRole("heading", { name: "Dashboard", level: 1 })
  ).toBeVisible();
}

export async function logout(page: Page): Promise<void> {
  await page.getByRole("button", { name: /log out/i }).click();
  await page.waitForURL(/\/login/);
}

/**
 * Picks an option in a PrimeReact dropdown. The dropdown renders a labelled textbox plus a hidden
 * <select> with the same label, so getByLabel is ambiguous; this drives the textbox by keyboard,
 * which also keeps a dialog's enter animation from swallowing the click.
 */
export async function chooseOption(
  scope: Page | Locator,
  label: string,
  option: string
): Promise<void> {
  const control = scope.getByRole("textbox", { name: label, exact: true });
  await control.focus();
  await control.press("ArrowDown");
  const page = "page" in scope ? scope.page() : scope;
  await page.getByRole("option", { name: option, exact: true }).click();
}
