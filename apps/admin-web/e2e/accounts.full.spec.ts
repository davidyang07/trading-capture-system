import { test, expect } from "@playwright/test";
import { login } from "./helpers";
import { INVESTOR1 } from "./credentials";

test.describe("accounts workflow (full stack)", () => {
  test.skip(
    !process.env.E2E_FULL_STACK,
    "Requires the account service (E2E_FULL_STACK=1)"
  );

  test("investor views an owned account and posts a balanced cash transaction pair", async ({
    page,
  }) => {
    await login(page, INVESTOR1());
    await page.goto("/accounts");
    await expect(
      page.getByRole("heading", { name: "Accounts", level: 1 })
    ).toBeVisible();

    // Open the first owned account.
    await page.getByRole("link", { name: /^ACC-/ }).first().click();
    await page.waitForURL(/\/accounts\/.+/);

    // Post a uniquely described deposit through the Cash tab.
    const runRef = `phase10-${Date.now()}`;
    await page.getByRole("tab", { name: "Cash" }).click();
    await page.getByRole("button", { name: /post cash transaction/i }).click();
    await page.getByLabel("Direction").click();
    await page.getByRole("option", { name: "Credit" }).click();
    await page.getByLabel("Type").click();
    await page.getByRole("option", { name: "Deposit" }).click();
    await page.getByRole("textbox", { name: "Amount" }).fill("17.25");
    await page.getByRole("textbox", { name: "Currency" }).fill("USD");
    await page.getByRole("textbox", { name: "Description" }).fill(runRef);
    await page.getByRole("button", { name: /^Post$/ }).click();

    // The immutable ledger refreshes and shows the new row.
    await expect(page.getByText("Deposit").first()).toBeVisible();

    // Compensate with an equal withdrawal so the shared seed account's balance is
    // restored. The audit rows intentionally remain immutable and are uniquely described.
    await page.getByRole("button", { name: /post cash transaction/i }).click();
    await page.getByLabel("Direction").click();
    await page.getByRole("option", { name: "Debit" }).click();
    await page.getByLabel("Type").click();
    await page.getByRole("option", { name: "Withdrawal" }).click();
    await page.getByRole("textbox", { name: "Amount" }).fill("17.25");
    await page.getByRole("textbox", { name: "Currency" }).fill("USD");
    await page
      .getByRole("textbox", { name: "Description" })
      .fill(`${runRef}-cleanup`);
    await page.getByRole("button", { name: /^Post$/ }).click();
    await expect(page.getByText("Withdrawal").first()).toBeVisible();
  });

  test("investor receives a masked not-found response for another owner's account", async ({
    page,
  }) => {
    await login(page, INVESTOR1());
    await page.goto("/accounts/ACC-SEED-INV2");
    await expect(page.getByRole("alert")).toContainText("was not found");
    await expect(page.getByText("Investor Two Cash")).toHaveCount(0);
  });
});
