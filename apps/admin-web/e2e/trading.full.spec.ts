import { test, expect } from "@playwright/test";
import { login } from "./helpers";
import { ADMIN, INVESTOR1 } from "./credentials";

// Data flows need the trading/account/enrichment services (containerized in Phase 13).
// They self-skip unless the full stack is declared available.
test.describe("trading workflow (full stack)", () => {
  test.skip(
    !process.env.E2E_FULL_STACK,
    "Requires the trading/account/enrichment services (E2E_FULL_STACK=1)"
  );

  test("investor creates, edits, and deletes a NEW order", async ({ page }) => {
    await login(page, INVESTOR1());
    await page.goto("/orders/new");

    await page.getByRole("textbox", { name: "Account ID" }).fill("ACC-SEED-INV1");
    await page.getByRole("textbox", { name: "Instrument ID" }).fill("AAPL");
    await page.getByLabel("Side").click();
    await page.getByRole("option", { name: "Buy" }).click();
    await page.getByLabel("Order type").click();
    await page.getByRole("option", { name: "Limit" }).click();
    await page.getByRole("textbox", { name: "Price" }).fill("189.25");
    await page.getByLabel("Price type").click();
    await page.getByRole("option", { name: "Per Unit" }).click();
    await page.getByRole("textbox", { name: "Currency" }).fill("USD");
    await page.getByRole("textbox", { name: "Quantity" }).fill("100");
    await page.getByLabel("Settlement type").click();
    await page.getByRole("option", { name: "Regular" }).click();
    await page
      .getByLabel("Settlement date")
      .fill(new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10));

    await page.getByRole("button", { name: /create order/i }).click();
    // Lands on the order detail page.
    await page.waitForURL(
      (url) => /^\/orders\/[^/]+$/.test(url.pathname) && url.pathname !== "/orders/new"
    );
    await expect(
      page.getByRole("heading", { name: /^Order /, level: 1 })
    ).toBeVisible();
    // A NEW order is editable and deletable.
    await expect(page.getByRole("button", { name: /^Edit$/ })).toBeVisible();
    await page.getByRole("button", { name: /^Edit$/ }).click();
    const quantity = page.getByRole("textbox", { name: "Quantity" });
    await quantity.fill("101.2500");
    await page.getByRole("button", { name: "Save changes" }).click();
    await expect(page.getByText("101.2500 USD")).toBeVisible();

    await page.getByRole("button", { name: "Delete" }).click();
    await page.getByRole("button", { name: "Yes" }).click();
    await page.waitForURL(/\/orders$/);
  });

  test("admin captures, corrects, and cancels an execution", async ({
    page,
  }) => {
    await login(page, ADMIN());
    await page.goto("/orders/new");
    await page.getByRole("textbox", { name: "Account ID" }).fill("ACC-SEED-ADMIN");
    await page.getByRole("textbox", { name: "Instrument ID" }).fill("AAPL");
    await page.getByLabel("Side").click();
    await page.getByRole("option", { name: "Buy" }).click();
    await page.getByLabel("Order type").click();
    await page.getByRole("option", { name: "Limit" }).click();
    await page.getByRole("textbox", { name: "Price" }).fill("189.25");
    await page.getByLabel("Price type").click();
    await page.getByRole("option", { name: "Per Unit" }).click();
    await page.getByRole("textbox", { name: "Currency" }).fill("USD");
    await page.getByRole("textbox", { name: "Quantity" }).fill("10.0000");
    await page.getByLabel("Settlement type").click();
    await page.getByRole("option", { name: "Regular" }).click();
    await page
      .getByLabel("Settlement date")
      .fill(new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10));
    await page.getByRole("button", { name: "Create order" }).click();
    await page.waitForURL(
      (url) => /^\/orders\/[^/]+$/.test(url.pathname) && url.pathname !== "/orders/new"
    );
    const orderId = decodeURIComponent(new URL(page.url()).pathname.split("/").pop() ?? "");
    expect(orderId).not.toBe("");

    const runId = Date.now();
    await page.goto(`/executions/new?orderId=${encodeURIComponent(orderId)}`);
    await page.getByRole("textbox", { name: "Exec ID" }).fill(`E2E-${runId}-NEW`);
    await page.getByRole("textbox", { name: "Fill quantity" }).fill("5.0000");
    await page.getByRole("textbox", { name: "Fill price" }).fill("189.25000000");
    await page.getByRole("button", { name: "Capture execution" }).click();
    await page.waitForURL(
      (url) =>
        /^\/executions\/[^/]+$/.test(url.pathname) &&
        url.pathname !== "/executions/new"
    );

    await page.getByRole("link", { name: "Correct" }).click();
    await page.getByRole("textbox", { name: "New Exec ID" }).fill(`E2E-${runId}-CORR`);
    await page.getByRole("textbox", { name: "Corrected quantity" }).fill("4.0000");
    await page.getByRole("textbox", { name: "Corrected price" }).fill("188.75000000");
    await page.getByRole("button", { name: "Append correction" }).click();
    await page.waitForURL((url) => /^\/executions\/[^/]+$/.test(url.pathname));

    await page.getByRole("link", { name: "Cancel" }).click();
    await page
      .getByRole("textbox", { name: "Cancellation Exec ID" })
      .fill(`E2E-${runId}-CXL`);
    await page.getByRole("button", { name: "Confirm cancellation" }).click();
    await page.waitForURL((url) => /^\/executions\/[^/]+$/.test(url.pathname));
    await expect(page.getByText(/execution chain has been cancelled/i)).toBeVisible();
  });
});
