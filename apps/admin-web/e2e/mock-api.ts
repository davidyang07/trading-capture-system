import type { Page, Route } from "@playwright/test";

export const emptyPage = {
  content: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
};

export async function json(
  route: Route,
  body: unknown,
  status = 200,
  correlationId = "e2e-correlation"
): Promise<void> {
  await route.fulfill({
    status,
    contentType: status >= 400 ? "application/problem+json" : "application/json",
    headers: { "X-Correlation-ID": correlationId },
    body: JSON.stringify(body),
  });
}

/** Keep dashboard requests deterministic while tests exercise another route. */
export async function mockDashboard(page: Page): Promise<void> {
  await page.route("**/api/proxy/trading/api/v1/orders?**", (route) =>
    json(route, emptyPage)
  );
  await page.route("**/api/proxy/account/api/v1/accounts?**", (route) =>
    json(route, emptyPage)
  );
}

export const ownedAccount = {
  accountId: "ACC-E2E-INV1",
  ownerSubject: "investor1",
  accountName: "Investor One E2E",
  accountType: "CASH",
  status: "ACTIVE",
  baseCurrency: "USD",
  createdAt: "2026-07-19T12:00:00Z",
  updatedAt: "2026-07-19T12:00:00Z",
  version: 0,
};

export const createdOrder = {
  orderId: "ORD-E2E-DECIMAL",
  clOrdId: null,
  ownerSubject: "investor1",
  accountId: ownedAccount.accountId,
  instrumentId: "AAPL",
  status: "NEW",
  side: "BUY",
  orderType: "LIMIT",
  price: "189.25000000",
  priceType: "PER_UNIT",
  currency: "USD",
  quantity: "100.1250",
  cumQty: "0.0000",
  leavesQty: "100.1250",
  settleType: "REGULAR",
  settleDate: "2026-07-22",
  createdAt: "2026-07-19T12:00:00Z",
  updatedAt: "2026-07-19T12:00:00Z",
  version: 0,
};
