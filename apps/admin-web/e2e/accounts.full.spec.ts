import { test, expect, type Locator } from "@playwright/test";

import { login } from "./helpers";
import { ADMIN, INVESTOR1 } from "./credentials";

// PrimeReact dropdowns render a labelled textbox plus a hidden labelled <select>; drive the
// textbox by keyboard (as workflows.spec.ts does) so the dialog's enter animation cannot eat a click.
async function choose(scope: Locator, label: string, option: string): Promise<void> {
  const control = scope.getByRole("textbox", { name: label });
  await control.focus();
  await control.press("ArrowDown");
  await scope.page().getByRole("option", { name: option, exact: true }).click();
}

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
    await choose(dialog, "Direction", "Credit");
    await choose(dialog, "Type", "Deposit");
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
    await choose(dialog, "Direction", "Debit");
    await choose(dialog, "Type", "Withdrawal");
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
    await expect(page.getByRole("alert")).toContainText("was not found");
    await expect(page.getByText("Investor Two Cash")).toHaveCount(0);
  });
});
