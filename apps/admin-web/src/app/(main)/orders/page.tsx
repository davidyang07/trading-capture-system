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
import { searchOrders } from "@/api/orders";
import { formatDate, formatMoney, humanizeEnum } from "@/lib/format";
import { ORDER_STATUS_OPTIONS, SIDE_OPTIONS } from "@/lib/options";
import type { Order, OrderSearchParams } from "@/types/api";

export default function OrdersPage() {
  const router = useRouter();
  const [filters, setFilters] = useState<OrderSearchParams>({
    page: 0,
    size: 10,
    sortBy: "CREATED_AT",
    sortDir: "DESC",
  });

  const query = useQuery(
    (signal) => searchOrders(filters, signal),
    [filters]
  );

  const setFilter = (patch: Partial<OrderSearchParams>) =>
    setFilters((prev) => ({ ...prev, page: 0, ...patch }));

  return (
    <div>
      <PageHeader
        title="Orders"
        description="Capture and manage trade orders."
        actions={
          <Button
            label="New order"
            icon="pi pi-plus"
            onClick={() => router.push("/orders/new")}
          />
        }
      />

      <div className="filter-bar" role="search">
        <label className="filter-item">
          <span>Status</span>
          <Dropdown
            value={filters.status}
            options={ORDER_STATUS_OPTIONS}
            optionLabel="label"
            optionValue="value"
            showClear
            placeholder="Any"
            ariaLabel="Status"
            pt={{ select: { "aria-label": "Status" } }}
            onChange={(e) => setFilter({ status: e.value })}
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
        emptyMessage="No orders match these filters."
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
            aria-label="Orders"
          >
            <Column
              field="orderId"
              header="Order ID"
              body={(o: Order) => (
                <Link href={`/orders/${encodeURIComponent(o.orderId)}`}>
                  {o.orderId}
                </Link>
              )}
            />
            <Column header="Side" body={(o: Order) => humanizeEnum(o.side)} />
            <Column field="instrumentId" header="Instrument" />
            <Column
              header="Quantity"
              body={(o: Order) => formatMoney(o.quantity, o.currency)}
            />
            <Column
              header="Filled"
              body={(o: Order) => formatMoney(o.cumQty)}
            />
            <Column
              header="Settle"
              body={(o: Order) => formatDate(o.settleDate)}
            />
            <Column
              header="Status"
              body={(o: Order) => <StatusBadge value={o.status} />}
            />
          </Table>
        )}
      </AsyncBoundary>
    </div>
  );
}
