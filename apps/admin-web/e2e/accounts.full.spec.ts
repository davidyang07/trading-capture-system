import { test, expect } from "@playwright/test";

import { chooseOption, login } from "./helpers";
import { ADMIN, INVESTOR1 } from "./credentials";

test.describe("accounts workflow (full stack)", () => {
  test.skip(
    !process.env.E2E_FULL_STACK,
    "Requires the account service (E2E_FULL_STACK=1)"
  );

  test("investor views an owned account but cannot post to its ledger", async ({
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

    await page.getByRole("tab", { name: "Cash" }).click();
    await expect(
      page.getByRole("button", { name: /post cash transaction/i })
    ).toHaveCount(0);
  });

  test("administrator posts a balanced cash transaction pair on an investor account", async ({
    page,
  }) => {
    await login(page, ADMIN());
    await page.goto("/accounts/ACC-SEED-INV1");

    // Post a uniquely described deposit through the Cash tab.
    const runRef = `phase10-${Date.now()}`;
    await page.getByRole("tab", { name: "Cash" }).click();
    await page.getByRole("button", { name: /post cash transaction/i }).click();
    let dialog = page.getByRole("dialog", { name: "Post cash transaction" });
    await chooseOption(dialog, "Direction", "Credit");
    await chooseOption(dialog, "Type", "Deposit");
    await dialog.getByRole("textbox", { name: "Amount" }).fill("17.25");
    await dialog.getByRole("textbox", { name: "Currency" }).fill("USD");
    await dialog.getByRole("textbox", { name: "Description" }).fill(runRef);
    await dialog.getByRole("button", { name: /^Post$/ }).click();
    await expect(dialog).toBeHidden();

    // The immutable ledger refreshes and shows the new row.
    await expect(page.getByText("Deposit").first()).toBeVisible();

    // Compensate with an equal withdrawal so the shared seed account's balance is
    // restored. The audit rows intentionally remain immutable and are uniquely described.
    await page.getByRole("button", { name: /post cash transaction/i }).click();
    dialog = page.getByRole("dialog", { name: "Post cash transaction" });
    await chooseOption(dialog, "Direction", "Debit");
    await chooseOption(dialog, "Type", "Withdrawal");
    await dialog.getByRole("textbox", { name: "Amount" }).fill("17.25");
    await dialog.getByRole("textbox", { name: "Currency" }).fill("USD");
    await dialog
      .getByRole("textbox", { name: "Description" })
      .fill(`${runRef}-cleanup`);
    await dialog.getByRole("button", { name: /^Post$/ }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByText("Withdrawal").first()).toBeVisible();
  });

  test("investor receives a masked not-found response for another owner's account", async ({
    page,
  }) => {
    await login(page, INVESTOR1());
    await page.goto("/accounts/ACC-SEED-INV2");
    // Filter by text: Next's route announcer is also role="alert".
    await expect(
      page.getByRole("alert").filter({ hasText: "was not found" })
    ).toBeVisible();
    await expect(page.getByText("Investor Two Cash")).toHaveCount(0);
  });
});
