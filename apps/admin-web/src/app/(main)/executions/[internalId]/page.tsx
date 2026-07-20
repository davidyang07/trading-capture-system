"use client";

import Link from "next/link";
import { useSession } from "next-auth/react";
import { Column } from "primereact/column";
import Table from "@/components/Table/Table";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { AsyncBoundary } from "@/components/feedback/States";
import { useQuery } from "@/hooks/useQuery";
import { getExecution } from "@/api/executions";
import { canWriteExecutions } from "@/lib/permissions";
import {
  formatDate,
  formatDateTime,
  formatMoney,
  humanizeEnum,
} from "@/lib/format";
import type { ChainLink, ExecutionDetail, MiscFee } from "@/types/api";

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="detail-row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function isTip(detail: ExecutionDetail): boolean {
  const tip = detail.chain.find((c) => c.tip);
  return tip?.internalId === detail.execution.internalId;
}

export default function ExecutionDetailPage({
  params,
}: {
  params: { internalId: string };
}) {
  const internalId = decodeURIComponent(params.internalId);
  const { data: session } = useSession();
  const canWrite = canWriteExecutions(session ?? null);
  const query = useQuery(
    (signal) => getExecution(internalId, signal),
    [internalId]
  );

  return (
    <div>
      <PageHeader
        title="Execution"
        description="Execution detail, fees, and amendment chain."
        actions={
          <Link href="/executions" className="p-button p-button-text">
            Back to executions
          </Link>
        }
      />
      <AsyncBoundary
        loading={query.loading}
        error={query.error}
        data={query.data}
        onRetry={query.refetch}
      >
        {(detail) => {
          const e = detail.execution;
          const amendable =
            canWrite &&
            detail.chainActive &&
            e.allocationState === "UNALLOCATED" &&
            isTip(detail);
          return (
            <>
              <section className="panel" aria-labelledby="exec-heading">
                <div className="panel-head">
                  <h2 id="exec-heading">
                    {e.execId} <StatusBadge value={e.execType} />
                  </h2>
                  {amendable ? (
                    <div className="panel-actions">
                      <Link
                        href={`/executions/${encodeURIComponent(
                          internalId
                        )}/correct`}
                        className="p-button p-button-outlined p-button-sm"
                      >
                        Correct
                      </Link>
                      <Link
                        href={`/executions/${encodeURIComponent(
                          internalId
                        )}/cancel`}
                        className="p-button p-button-outlined p-button-danger p-button-sm"
                      >
                        Cancel
                      </Link>
                    </div>
                  ) : null}
                </div>
                {!detail.chainActive ? (
                  <p className="banner banner-warning" role="status">
                    This execution chain has been cancelled (void).
                  </p>
                ) : null}
                <dl className="detail-grid">
                  <DetailRow
                    label="Order"
                    value={
                      <Link href={`/orders/${encodeURIComponent(e.orderId)}`}>
                        {e.orderId}
                      </Link>
                    }
                  />
                  <DetailRow label="Side" value={humanizeEnum(e.side)} />
                  <DetailRow label="Quantity" value={formatMoney(e.lastQty)} />
                  <DetailRow label="Price" value={formatMoney(e.lastPx)} />
                  <DetailRow
                    label="Order status"
                    value={<StatusBadge value={e.ordStatus} />}
                  />
                  <DetailRow
                    label="Cumulative / Remaining"
                    value={`${formatMoney(e.cumQty)} / ${formatMoney(
                      e.leavesQty
                    )}`}
                  />
                  <DetailRow label="Avg price" value={formatMoney(e.avgPx)} />
                  <DetailRow
                    label="Allocation"
                    value={<StatusBadge value={e.allocationState} />}
                  />
                  <DetailRow
                    label="Trade date"
                    value={formatDate(e.tradeDate)}
                  />
                  <DetailRow
                    label="Transact time"
                    value={formatDateTime(e.transactTime)}
                  />
                  {e.text ? <DetailRow label="Note" value={e.text} /> : null}
                </dl>
              </section>

              {detail.miscFees.length > 0 ? (
                <section className="panel" aria-labelledby="fees-heading">
                  <div className="panel-head">
                    <h2 id="fees-heading">Fees</h2>
                  </div>
                  <Table
                    dataList={detail.miscFees}
                    totalNum={0}
                    onPageChange={() => {}}
                    aria-label="Fees"
                  >
                    <Column field="feeType" header="Type" />
                    <Column
                      header="Amount"
                      body={(f: MiscFee) => formatMoney(f.amount, f.currency)}
                    />
                    <Column
                      header="Basis"
                      body={(f: MiscFee) => humanizeEnum(f.basis)}
                    />
                  </Table>
                </section>
              ) : null}

              <section className="panel" aria-labelledby="chain-heading">
                <div className="panel-head">
                  <h2 id="chain-heading">Amendment chain</h2>
                </div>
                <Table
                  dataList={detail.chain}
                  totalNum={0}
                  onPageChange={() => {}}
                  aria-label="Amendment chain"
                >
                  <Column
                    field="execId"
                    header="Exec ID"
                    body={(c: ChainLink) =>
                      c.internalId === internalId ? (
                        <strong>{c.execId}</strong>
                      ) : (
                        <Link
                          href={`/executions/${encodeURIComponent(
                            c.internalId
                          )}`}
                        >
                          {c.execId}
                        </Link>
                      )
                    }
                  />
                  <Column
                    header="Type"
                    body={(c: ChainLink) => <StatusBadge value={c.execType} />}
                  />
                  <Column
                    header="Qty"
                    body={(c: ChainLink) => formatMoney(c.lastQty)}
                  />
                  <Column
                    header="Time"
                    body={(c: ChainLink) => formatDateTime(c.transactTime)}
                  />
                  <Column
                    header="Tip"
                    body={(c: ChainLink) =>
                      c.tip ? (
                        <span aria-label="Current tip">
                          <i className="pi pi-flag" aria-hidden="true" /> Tip
                        </span>
                      ) : (
                        ""
                      )
                    }
                  />
                </Table>
              </section>
            </>
          );
        }}
      </AsyncBoundary>
    </div>
  );
}
