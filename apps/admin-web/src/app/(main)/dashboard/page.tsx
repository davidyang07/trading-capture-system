"use client";

import { useCallback } from "react";
import Link from "next/link";
import { useSession } from "next-auth/react";
import { Column } from "primereact/column";
import Table from "@/components/Table/Table";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { AsyncBoundary } from "@/components/feedback/States";
import { useQuery } from "@/hooks/useQuery";
import { searchOrders } from "@/api/orders";
import { searchAccounts } from "@/api/accounts";
import { isAdmin } from "@/lib/permissions";
import { formatMoney, humanizeEnum, formatQuantity } from "@/lib/format";
import type { Account, Order } from "@/types/api";

function SummaryCard({
  label,
  value,
  icon,
  href,
}: {
  label: string;
  value: string | number;
  icon: string;
  href: string;
}) {
  return (
    <Link href={href} className="summary-card">
      <span className="summary-icon">
        <i className={icon} aria-hidden="true" />
      </span>
      <span className="summary-body">
        <span className="summary-value">{value}</span>
        <span className="summary-label">{label}</span>
      </span>
    </Link>
  );
}

export default function DashboardPage() {
  const { data: session } = useSession();
  const admin = isAdmin(session ?? null);
  const audience = admin ? "across all owners" : "in your portfolio";

  const ordersQuery = useQuery(
    (signal) =>
      searchOrders({ size: 5, sortBy: "CREATED_AT", sortDir: "DESC" }, signal),
    []
  );
  const accountsQuery = useQuery(
    (signal) => searchAccounts({ size: 5, sortBy: "CREATED_AT", sortDir: "DESC" }, signal),
    []
  );

  const retry = useCallback(() => {
    ordersQuery.refetch();
    accountsQuery.refetch();
  }, [ordersQuery, accountsQuery]);

  return (
    <div className="dashboard">
      <PageHeader
        title="Dashboard"
        description={`A summary of recent trading activity ${audience}.`}
      />

      <section aria-label="Summary" className="summary-grid">
        <SummaryCard
          label="Orders"
          value={ordersQuery.data?.totalElements ?? "—"}
          icon="pi pi-book"
          href="/orders"
        />
        <SummaryCard
          label="Accounts"
          value={accountsQuery.data?.totalElements ?? "—"}
          icon="pi pi-dollar"
          href="/accounts"
        />
        <SummaryCard
          label="Role"
          value={admin ? "Administrator" : "Investor"}
          icon="pi pi-user"
          href="/dashboard"
        />
      </section>

      <section aria-labelledby="recent-orders-heading" className="panel">
        <div className="panel-head">
          <h2 id="recent-orders-heading">Recent orders</h2>
          <Link href="/orders" className="panel-link">
            View all
          </Link>
        </div>
        <AsyncBoundary
          loading={ordersQuery.loading}
          error={ordersQuery.error}
          data={ordersQuery.data}
          onRetry={retry}
          isEmpty={(page) => page.content.length === 0}
          emptyMessage="No orders yet."
        >
          {(page) => (
            <Table
              dataList={page.content}
              totalNum={0}
              onPageChange={() => {}}
              aria-label="Recent orders"
            >
              <Column
                field="orderId"
                header="Order ID"
                body={(o: Order) => (
                  <Link href={`/orders/${encodeURIComponent(o.orderId)}`} title={o.orderId}>
                    {o.orderId}
                  </Link>
                )}
              />
              <Column
                header="Side"
                body={(o: Order) => humanizeEnum(o.side)}
              />
              <Column field="instrumentId" header="Instrument" />
              <Column
                header="Quantity"
                align="right"
                body={(o: Order) => formatQuantity(o.quantity)}
              />
              <Column
                header="Status"
                body={(o: Order) => <StatusBadge value={o.status} />}
              />
            </Table>
          )}
        </AsyncBoundary>
      </section>

      <section aria-labelledby="recent-accounts-heading" className="panel">
        <div className="panel-head">
          <h2 id="recent-accounts-heading">Accounts</h2>
          <Link href="/accounts" className="panel-link">
            View all
          </Link>
        </div>
        <AsyncBoundary
          loading={accountsQuery.loading}
          error={accountsQuery.error}
          data={accountsQuery.data}
          onRetry={retry}
          isEmpty={(page) => page.content.length === 0}
          emptyMessage="No accounts yet."
        >
          {(page) => (
            <Table
              dataList={page.content}
              totalNum={0}
              onPageChange={() => {}}
              aria-label="Accounts"
            >
              <Column
                field="accountId"
                header="Account ID"
                body={(a: Account) => (
                  <Link href={`/accounts/${encodeURIComponent(a.accountId)}`} title={a.accountId}>
                    {a.accountId}
                  </Link>
                )}
              />
              <Column field="accountName" header="Name" />
              <Column
                header="Type"
                body={(a: Account) => humanizeEnum(a.accountType)}
              />
              <Column
                header="Status"
                body={(a: Account) => <StatusBadge value={a.status} />}
              />
            </Table>
          )}
        </AsyncBoundary>
      </section>
    </div>
  );
}
