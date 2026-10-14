import { expect, test, type Page } from "@playwright/test";
import { ADMIN, INVESTOR1 } from "./credentials";
import { login } from "./helpers";
import {
  createdOrder,
  emptyPage,
  json,
  mockDashboard,
  ownedAccount,
} from "./mock-api";

async function chooseOption(page: Page, label: string, option: string): Promise<void> {
  const control = page.getByRole("textbox", { name: label });
  await control.focus();
  await control.press("ArrowDown");
  await page.getByRole("option", { name: option }).click();
}

async function fillOrder(page: Page): Promise<void> {
  await page.getByRole("textbox", { name: "Account ID" }).fill(ownedAccount.accountId);
  await page.getByRole("textbox", { name: "Instrument ID" }).fill("AAPL");
  await chooseOption(page, "Side", "Buy");
  await chooseOption(page, "Order type", "Limit");
  await page
    .getByRole("textbox", { name: "Price", exact: true })
    .fill("189.25000000");
  await chooseOption(page, "Price type", "Per Unit");
  await page.getByRole("textbox", { name: "Currency" }).fill("USD");
  await page.getByRole("textbox", { name: "Quantity" }).fill("100.1250");
  await chooseOption(page, "Settlement type", "Regular");
  await page.getByRole("textbox", { name: "Settlement date" }).fill("2026-07-22");
}

test.describe("deterministic investor and administrator workflows", () => {
  test("investor views an owned account, balances, and positions but not lifecycle or posting controls", async ({
    page,
  }) => {
    await mockDashboard(page);
    await page.route("**/api/proxy/account/api/v1/accounts/ACC-E2E-INV1", (route) =>
      json(route, ownedAccount)
    );
    await page.route("**/api/proxy/account/api/v1/accounts/ACC-E2E-INV1/balances", (route) =>
      json(route, {
        accountId: ownedAccount.accountId,
        cashBalances: [{ currency: "USD", balance: "95000.0000" }],
        securityPositions: [{ instrumentId: "AAPL", quantity: "400.0000" }],
      })
    );
    await page.route(
      "**/api/proxy/account/api/v1/accounts/ACC-E2E-INV1/cash-transactions?**",
      (route) => json(route, emptyPage)
    );

    await login(page, INVESTOR1());
    await page.goto(`/accounts/${ownedAccount.accountId}`);

    await expect(page.getByRole("heading", { name: ownedAccount.accountName })).toBeVisible();
    await expect(page.getByText("95,000.0000 USD")).toBeVisible();
    await expect(page.getByText("AAPL")).toBeVisible();
    await expect(page.getByRole("button", { name: /activate|suspend|close/i })).toHaveCount(0);

    // Ledger posts are ADMIN-only (the account service answers 403), so investors get no controls.
    await page.getByRole("tab", { name: "Cash" }).click();
    await expect(page.getByRole("button", { name: "Post cash transaction" })).toHaveCount(0);
    await page.getByRole("tab", { name: "Securities" }).click();
    await expect(page.getByRole("button", { name: "Post security transaction" })).toHaveCount(0);
  });

  test("investor cannot learn whether another investor account exists", async ({ page }) => {
    await mockDashboard(page);
    await page.route("**/api/proxy/account/api/v1/accounts/ACC-E2E-INV2", (route) =>
      json(
        route,
        { status: 404, errorCode: "NOT_FOUND", detail: "masked" },
        404,
        "cross-owner-ref"
      )
    );

    await login(page, INVESTOR1());
    await page.goto("/accounts/ACC-E2E-INV2");

    await expect(
      page.getByRole("alert").filter({ hasText: "was not found" })
    ).toBeVisible();
    await expect(page.getByText("cross-owner-ref")).toBeVisible();
    await expect(page.getByText("masked")).toHaveCount(0);
  });

  test("order submission preserves decimal strings and blocks a duplicate click", async ({
    page,
  }) => {
    await mockDashboard(page);
    let postCount = 0;
    let postedBody: unknown;
    let releaseRequest!: () => void;
    const requestCanComplete = new Promise<void>((resolve) => {
      releaseRequest = resolve;
    });

    await page.route("**/api/proxy/trading/api/v1/orders**", async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      if (request.method() === "POST") {
        postCount += 1;
        postedBody = request.postDataJSON();
        await requestCanComplete;
        return json(route, createdOrder, 201);
      }
      if (url.pathname.endsWith(`/${createdOrder.orderId}`)) {
        return json(route, { order: createdOrder, executions: [] });
      }
      return json(route, emptyPage);
    });

    await login(page, INVESTOR1());
    await page.goto("/orders/new");
    await fillOrder(page);

    const submit = page.getByRole("button", { name: "Create order" });
    await submit.click();
    await expect(submit).toBeDisabled();
    await submit.click({ force: true });
    expect(postCount).toBe(1);
    expect(postedBody).toMatchObject({
      price: "189.25000000",
      quantity: "100.1250",
    });

    releaseRequest();
    await page.waitForURL(`**/orders/${createdOrder.orderId}`);
    await expect(
      page.getByRole("heading", { name: `Order ${createdOrder.orderId}`, level: 1 })
    ).toBeVisible();
  });

  test("business rejection is mapped to safe actionable feedback", async ({ page }) => {
    await mockDashboard(page);
    await page.route("**/api/proxy/trading/api/v1/orders**", (route) => {
      if (route.request().method() === "POST") {
        return json(
          route,
          {
            status: 409,
            errorCode: "ACCOUNT_NOT_ACTIVE",
            detail: "database constraint and internal identifier",
          },
          409,
          "order-reject-ref"
        );
      }
      return json(route, emptyPage);
    });

    await login(page, INVESTOR1());
    await page.goto("/orders/new");
    await fillOrder(page);
    await page.getByRole("button", { name: "Create order" }).click();

    await expect(
      page.getByRole("alert").filter({ hasText: "The account is not active." })
    ).toBeVisible();
    await expect(page.getByText("order-reject-ref")).toBeVisible();
    await expect(page.getByText(/database constraint/i)).toHaveCount(0);
  });

  test("administrator reads reference data, mutates lifecycle, and sees validation feedback", async ({
    page,
  }) => {
    await mockDashboard(page);
    let status = "ACTIVE";
    await page.route("**/api/proxy/enrichment/api/v1/instruments**", (route) =>
      json(route, {
        ...emptyPage,
        content: [
          {
            instrumentId: "AAPL",
            name: "Apple Inc.",
            instrumentType: "EQUITY",
            exchangeMic: "XNAS",
            currencyCode: "USD",
            status: "ACTIVE",
            createdAt: "2026-07-01T00:00:00Z",
            updatedAt: "2026-07-01T00:00:00Z",
            version: 0,
          },
        ],
        totalElements: 1,
        totalPages: 1,
      })
    );
    await page.route("**/api/proxy/enrichment/api/v1/currencies", (route) =>
      json(route, [{ code: "USD", name: "US Dollar", minorUnits: 2 }])
    );
    await page.route("**/api/proxy/enrichment/api/v1/exchanges", (route) =>
      json(route, [{ mic: "XNAS", name: "Nasdaq", country: "US", timezone: "America/New_York" }])
    );
    await page.route("**/api/proxy/account/api/v1/accounts/ACC-E2E-INV1**", (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith("/suspend")) {
        status = "SUSPENDED";
        return json(route, { ...ownedAccount, status });
      }
      if (url.pathname.endsWith("/balances")) {
        return json(route, { accountId: ownedAccount.accountId, cashBalances: [], securityPositions: [] });
      }
      if (url.pathname.endsWith("-transactions")) {
        return json(route, emptyPage);
      }
      return json(route, { ...ownedAccount, status });
    });

    await login(page, ADMIN());
    await page.goto("/reference");
    await expect(page.getByText("Apple Inc.")).toBeVisible();
    await expect(page.getByRole("table", { name: "Instruments" })).toBeVisible();

    await page.goto(`/accounts/${ownedAccount.accountId}`);
    await page.getByRole("button", { name: "Suspend" }).click();
    await expect(page.getByText("Suspended")).toBeVisible();

    await page.getByRole("tab", { name: "Cash" }).click();
    await page.getByRole("button", { name: "Post cash transaction" }).click();
    const dialog = page.getByRole("dialog", { name: "Post cash transaction" });
    await expect(dialog).toBeVisible();
    await expect(dialog.locator(":focus")).toHaveCount(1);
    await page.keyboard.press("Escape");
    await expect(dialog).toBeHidden();

    await page.goto("/executions/new");
    await page.getByRole("button", { name: "Capture execution" }).click();
    await expect(
      page
        .locator('p[role="alert"]')
        .filter({ hasText: "Please correct the highlighted fields." })
    ).toBeVisible();
  });
});
