import { expect, test } from "@playwright/test";
import { INVESTOR1 } from "./credentials";
import { login } from "./helpers";
import { emptyPage, json } from "./mock-api";

test.describe("frontend resilience", () => {
  test("temporary API failure shows a reference and recovers through deterministic retry", async ({
    page,
  }) => {
    let attempts = 0;
    let serviceAvailable = false;
    await page.route("**/api/proxy/trading/api/v1/orders?**", (route) => {
      attempts += 1;
      return serviceAvailable
        ? json(route, emptyPage)
        : json(route, { status: 503, detail: "internal host failed" }, 503, "retry-ref");
    });
    await page.route("**/api/proxy/account/api/v1/accounts?**", (route) =>
      json(route, emptyPage)
    );

    await login(page, INVESTOR1());
    await expect(
      page.getByRole("alert").filter({ hasText: "Something went wrong on our side." })
    ).toBeVisible();
    await expect(page.getByText("retry-ref")).toBeVisible();
    await expect(page.getByText("internal host failed")).toHaveCount(0);

    const failedAttempts = attempts;
    serviceAvailable = true;
    await page.getByRole("button", { name: "Retry" }).click();
    await expect(page.getByText("No orders yet.")).toBeVisible();
    expect(attempts).toBeGreaterThan(failedAttempts);
  });

  test("unauthorized API response renders a safe expired-session state", async ({ page }) => {
    await page.route("**/api/proxy/trading/api/v1/orders?**", (route) =>
      json(route, { status: 401, detail: "raw token diagnostic" }, 401, "auth-ref")
    );
    await page.route("**/api/proxy/account/api/v1/accounts?**", (route) =>
      json(route, emptyPage)
    );

    await login(page, INVESTOR1());
    await expect(
      page
        .getByRole("alert")
        .filter({ hasText: "Your session has expired. Please sign in again." })
    ).toBeVisible();
    await expect(page.getByText("auth-ref")).toBeVisible();
    await expect(page.getByText("raw token diagnostic")).toHaveCount(0);
  });

  test("malformed successful API response is reported without rendering corrupt data", async ({
    page,
  }) => {
    await page.route("**/api/proxy/trading/api/v1/orders?**", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "{not valid json",
      })
    );
    await page.route("**/api/proxy/account/api/v1/accounts?**", (route) =>
      json(route, emptyPage)
    );

    await login(page, INVESTOR1());
    await expect(
      page
        .getByRole("alert")
        .filter({ hasText: "The server returned an unexpected response." })
    ).toBeVisible();
  });
});
