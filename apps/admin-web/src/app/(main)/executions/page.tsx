"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSession } from "next-auth/react";
import { Button } from "primereact/button";
import { Column } from "primereact/column";
import { Dropdown } from "primereact/dropdown";
import Table from "@/components/Table/Table";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { AsyncBoundary } from "@/components/feedback/States";
import { useQuery } from "@/hooks/useQuery";
import { searchExecutions } from "@/api/executions";
import { canWriteExecutions } from "@/lib/permissions";
import { formatDateTime, formatMoney } from "@/lib/format";
import { EXECUTION_TYPE_OPTIONS, SIDE_OPTIONS } from "@/lib/options";
import type { Execution, ExecutionSearchParams } from "@/types/api";

export default function ExecutionsPage() {
  const router = useRouter();
  const { data: session } = useSession();
  const canCapture = canWriteExecutions(session ?? null);
  const [filters, setFilters] = useState<ExecutionSearchParams>({
    page: 0,
    size: 10,
    sortBy: "TRANSACT_TIME",
    sortDir: "DESC",
  });

  const query = useQuery(
    (signal) => searchExecutions(filters, signal),
    [filters]
  );

  const setFilter = (patch: Partial<ExecutionSearchParams>) =>
    setFilters((prev) => ({ ...prev, page: 0, ...patch }));

  return (
    <div>
      <PageHeader
        title="Executions"
        description="Trade executions and their correction/cancellation history."
        actions={
          canCapture ? (
            <Button
              label="Capture execution"
              icon="pi pi-plus"
              onClick={() => router.push("/executions/new")}
            />
          ) : undefined
        }
      />

      <div className="filter-bar" role="search">
        <label className="filter-item">
          <span>Type</span>
          <Dropdown
            value={filters.execType}
            options={EXECUTION_TYPE_OPTIONS}
            optionLabel="label"
            optionValue="value"
            showClear
            placeholder="Any"
            ariaLabel="Type"
            pt={{ select: { "aria-label": "Type" } }}
            onChange={(e) => setFilter({ execType: e.value })}
          />
        </label>
        <label className="filter-item">
          <span>Side</span>
          <Dropdown
            value={filters.side}
            options={SIDE_OPTIONS}
            optionLabel="label"
            optionValue="value"
            showClear
            placeholder="Any"
            ariaLabel="Side"
            pt={{ select: { "aria-label": "Side" } }}
            onChange={(e) => setFilter({ side: e.value })}
          />
        </label>
      </div>

      <AsyncBoundary
        loading={query.loading}
        error={query.error}
        data={query.data}
        onRetry={query.refetch}
        isEmpty={(page) => page.content.length === 0}
        emptyMessage="No executions match these filters."
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
            aria-label="Executions"
          >
            <Column
              field="execId"
              header="Exec ID"
              body={(e: Execution) => (
                <Link href={`/executions/${encodeURIComponent(e.internalId)}`}>
                  {e.execId}
                </Link>
              )}
            />
            <Column
              field="orderId"
              header="Order"
              body={(e: Execution) => (
                <Link href={`/orders/${encodeURIComponent(e.orderId)}`}>
                  {e.orderId}
                </Link>
              )}
            />
            <Column
              header="Type"
              body={(e: Execution) => <StatusBadge value={e.execType} />}
            />
            <Column header="Qty" body={(e: Execution) => formatMoney(e.lastQty)} />
            <Column
              header="Price"
              body={(e: Execution) => formatMoney(e.lastPx)}
            />
            <Column
              header="Allocation"
              body={(e: Execution) => <StatusBadge value={e.allocationState} />}
            />
            <Column
              header="Time"
              body={(e: Execution) => formatDateTime(e.transactTime)}
            />
          </Table>
        )}
      </AsyncBoundary>
    </div>
  );
}
