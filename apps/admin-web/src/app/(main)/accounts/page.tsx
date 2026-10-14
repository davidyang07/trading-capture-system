"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "primereact/button";
import { Column } from "primereact/column";
import { Dropdown } from "primereact/dropdown";
import Table from "@/components/Table/Table";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { AsyncBoundary } from "@/components/feedback/States";
import { useQuery } from "@/hooks/useQuery";
import { searchAccounts } from "@/api/accounts";
import { formatDate, humanizeEnum } from "@/lib/format";
import { ACCOUNT_STATUS_OPTIONS, ACCOUNT_TYPE_OPTIONS } from "@/lib/options";
import type { Account, AccountSearchParams } from "@/types/api";

export default function AccountsPage() {
  const router = useRouter();
  const [filters, setFilters] = useState<AccountSearchParams>({
    page: 0,
    size: 10,
    sortBy: "CREATED_AT",
    sortDir: "DESC",
  });

  const query = useQuery(
    (signal) => searchAccounts(filters, signal),
    [filters]
  );

  const setFilter = (patch: Partial<AccountSearchParams>) =>
    setFilters((prev) => ({ ...prev, page: 0, ...patch }));

  return (
    <div>
      <PageHeader
        title="Accounts"
        description="Financial accounts, balances, and ledgers."
        actions={
          <Button
            label="New account"
            icon="pi pi-plus"
            onClick={() => router.push("/accounts/new")}
          />
        }
      />

      <div className="filter-bar" role="search">
        <label className="filter-item">
          <span>Status</span>
          <Dropdown
            value={filters.status}
            options={ACCOUNT_STATUS_OPTIONS}
            optionLabel="label"
            optionValue="value"
            showClear
            placeholder="Any"
            onChange={(e) => setFilter({ status: e.value })}
          />
        </label>
        <label className="filter-item">
          <span>Type</span>
          <Dropdown
            value={filters.accountType}
            options={ACCOUNT_TYPE_OPTIONS}
            optionLabel="label"
            optionValue="value"
            showClear
            placeholder="Any"
            onChange={(e) => setFilter({ accountType: e.value })}
          />
        </label>
      </div>

      <AsyncBoundary
        loading={query.loading}
        error={query.error}
        data={query.data}
        onRetry={query.refetch}
        isEmpty={(page) => page.content.length === 0}
        emptyMessage="No accounts match these filters."
      >
        {(page) => (
          <Table
            dataList={page.content}
            totalNum={page.totalElements}
            page={filters.page}
            onPageChange={({ pageNumber, pageSize }) =>
              setFilters((prev) => ({
                ...prev,
                page: pageNumber,
                size: pageSize,
              }))
            }
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
            <Column field="baseCurrency" header="Currency" />
            <Column
              header="Status"
              body={(a: Account) => <StatusBadge value={a.status} />}
            />
            <Column
              header="Created"
              body={(a: Account) => formatDate(a.createdAt)}
            />
          </Table>
        )}
      </AsyncBoundary>
    </div>
  );
}
