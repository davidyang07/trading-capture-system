"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Button } from "primereact/button";
import { Column } from "primereact/column";
import { ConfirmDialog, confirmDialog } from "primereact/confirmdialog";
import Table from "@/components/Table/Table";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { AsyncBoundary } from "@/components/feedback/States";
import { useToast } from "@/components/Toast/ToastProvider";
import { useQuery } from "@/hooks/useQuery";
import { useMutation } from "@/hooks/useMutation";
import { deleteOrder, getOrder, updateOrder } from "@/api/orders";
import { isApiError } from "@/lib/api-error";
import { OrderForm } from "@/app/(main)/orders/OrderForm";
import {
  formatDate,
  formatDateTime,
  formatMoney,
  humanizeEnum,
  formatQuantity,
} from "@/lib/format";
import type { Execution, Order, UpdateOrderRequest } from "@/types/api";

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="detail-row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

export default function OrderDetailPage() {
  const params = useParams<{ orderId: string }>();
  const orderId = decodeURIComponent(params.orderId);
  const router = useRouter();
  const toast = useToast();
  const [editing, setEditing] = useState(false);

  const query = useQuery((signal) => getOrder(orderId, signal), [orderId]);
  const updateMutation = useMutation((body: UpdateOrderRequest) =>
    updateOrder(orderId, body)
  );
  const deleteMutation = useMutation(() => deleteOrder(orderId));

  const handleUpdate = async (order: Order, body: UpdateOrderRequest) => {
    try {
      await updateMutation.mutate(body);
      toast.successToast({ detail: "Order updated." });
      setEditing(false);
      query.refetch();
    } catch {
      /* surfaced by the form */
    }
  };

  const confirmDelete = () => {
    confirmDialog({
      message: "Delete this order? This cannot be undone.",
      header: "Confirm delete",
      icon: "pi pi-exclamation-triangle",
      acceptClassName: "p-button-danger",
      accept: async () => {
        try {
          await deleteMutation.mutate();
          toast.successToast({ detail: "Order deleted." });
          router.push("/orders");
        } catch (err) {
          const message = isApiError(err)
            ? err.userMessage
            : "Unable to delete order.";
          toast.errorToast({ detail: message });
        }
      },
    });
  };

  return (
    <div>
      <ConfirmDialog />
      <PageHeader
        title={`Order ${orderId}`}
        description="Order detail and execution history."
        actions={
          <Link href="/orders" className="p-button p-button-text">
            Back to orders
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
          const order = detail.order;
          const editable = order.status === "NEW";
          const deletable = editable && detail.executions.length === 0;

          if (editing) {
            return (
              <section aria-label="Edit order">
                <OrderForm
                  submitLabel="Save changes"
                  pending={updateMutation.pending}
                  error={updateMutation.error}
                  initialValues={{
                    accountId: order.accountId,
                    instrumentId: order.instrumentId,
                    side: order.side,
                    orderType: order.orderType,
                    price: order.price,
                    priceType: order.priceType,
                    currency: order.currency,
                    quantity: order.quantity,
                    settleType: order.settleType,
                    settleDate: order.settleDate,
                  }}
                  onSubmit={(body) => handleUpdate(order, body)}
                />
                <div className="form-actions">
                  <Button
                    label="Cancel"
                    text
                    onClick={() => setEditing(false)}
                  />
                </div>
              </section>
            );
          }

          return (
            <>
              <section aria-labelledby="order-info-heading" className="panel">
                <div className="panel-head">
                  <h2 id="order-info-heading">Order</h2>
                  <div className="panel-actions">
                    {editable ? (
                      <Button
                        label="Edit"
                        icon="pi pi-pencil"
                        outlined
                        size="small"
                        onClick={() => setEditing(true)}
                      />
                    ) : null}
                    {deletable ? (
                      <Button
                        label="Delete"
                        icon="pi pi-trash"
                        severity="danger"
                        outlined
                        size="small"
                        onClick={confirmDelete}
                      />
                    ) : null}
                  </div>
                </div>
                <dl className="detail-grid">
                  <DetailRow
                    label="Status"
                    value={<StatusBadge value={order.status} />}
                  />
                  <DetailRow label="Owner" value={order.ownerSubject} />
                  <DetailRow label="Account" value={order.accountId} />
                  <DetailRow label="Instrument" value={order.instrumentId} />
                  <DetailRow label="Side" value={humanizeEnum(order.side)} />
                  <DetailRow
                    label="Order type"
                    value={humanizeEnum(order.orderType)}
                  />
                  <DetailRow
                    label="Price"
                    value={`${formatMoney(order.price)} (${humanizeEnum(
                      order.priceType
                    )})`}
                  />
                  <DetailRow
                    label="Quantity"
                    value={formatQuantity(order.quantity)}
                  />
                  <DetailRow
                    label="Filled / Remaining"
                    value={`${formatQuantity(order.cumQty)} / ${formatQuantity(
                      order.leavesQty
                    )}`}
                  />
                  <DetailRow
                    label="Settlement"
                    value={`${humanizeEnum(order.settleType)} · ${formatDate(
                      order.settleDate
                    )}`}
                  />
                  <DetailRow
                    label="Created"
                    value={formatDateTime(order.createdAt)}
                  />
                </dl>
              </section>

              <section
                aria-labelledby="order-execs-heading"
                className="panel"
              >
                <div className="panel-head">
                  <h2 id="order-execs-heading">Execution history</h2>
                </div>
                {detail.executions.length === 0 ? (
                  <p className="muted">No executions recorded for this order.</p>
                ) : (
                  <Table
                    dataList={detail.executions}
                    totalNum={0}
                    onPageChange={() => {}}
                    aria-label="Execution history"
                  >
                    <Column
                      field="execId"
                      header="Exec ID"
                      body={(e: Execution) => (
                        <Link
                          href={`/executions/${encodeURIComponent(
                            e.internalId
                          )}`}
                          title={e.execId}
                        >
                          {e.execId}
                        </Link>
                      )}
                    />
                    <Column
                      header="Type"
                      body={(e: Execution) => (
                        <StatusBadge value={e.execType} />
                      )}
                    />
                    <Column
                      header="Qty"
                      align="right"
                      body={(e: Execution) => formatQuantity(e.lastQty)}
                    />
                    <Column
                      header="Price"
                      align="right"
                      body={(e: Execution) => formatMoney(e.lastPx)}
                    />
                    <Column
                      header="Time"
                      body={(e: Execution) => formatDateTime(e.transactTime)}
                    />
                  </Table>
                )}
              </section>
            </>
          );
        }}
      </AsyncBoundary>
    </div>
  );
}
