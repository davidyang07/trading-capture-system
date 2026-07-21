"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useSession } from "next-auth/react";
import { Button } from "primereact/button";
import { Column } from "primereact/column";
import { TabView, TabPanel } from "primereact/tabview";
import Table from "@/components/Table/Table";
import { PageHeader } from "@/components/PageHeader";
import { StatusBadge } from "@/components/StatusBadge";
import { AsyncBoundary, ErrorState, LoadingState } from "@/components/feedback/States";
import { useToast } from "@/components/Toast/ToastProvider";
import { useQuery } from "@/hooks/useQuery";
import { useMutation } from "@/hooks/useMutation";
import { isAdmin } from "@/lib/permissions";
import { isApiError } from "@/lib/api-error";
import {
  changeAccountStatus,
  getAccount,
  getBalances,
  getCashTransactions,
  getSecurityTransactions,
} from "@/api/accounts";
import {
  PostCashDialog,
  PostSecurityDialog,
} from "@/app/(main)/accounts/[accountId]/TransactionDialogs";
import {
  formatDateTime,
  formatMoney,
  humanizeEnum,
} from "@/lib/format";
import type {
  Account,
  Balances,
  CashTransaction,
  SecurityTransaction,
} from "@/types/api";

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="detail-row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function StatusActions({
  account,
  onChanged,
}: {
  account: Account;
  onChanged: () => void;
}) {
  const toast = useToast();
  const mutation = useMutation(
    (action: "activate" | "suspend" | "close") =>
      changeAccountStatus(account.accountId, action)
  );

  const run = async (action: "activate" | "suspend" | "close") => {
    try {
      await mutation.mutate(action);
      toast.successToast({ detail: `Account ${action}d.` });
      onChanged();
    } catch (err) {
      toast.errorToast({
        detail: isApiError(err) ? err.userMessage : "Status change failed.",
      });
    }
  };

  const canActivate =
    account.status === "PENDING" || account.status === "SUSPENDED";
  const canSuspend = account.status === "ACTIVE";
  const canClose =
    account.status === "ACTIVE" || account.status === "SUSPENDED";

  return (
    <div className="panel-actions">
      {canActivate ? (
        <Button
          label="Activate"
          icon="pi pi-check"
          size="small"
          outlined
          loading={mutation.pending}
          onClick={() => run("activate")}
        />
      ) : null}
      {canSuspend ? (
        <Button
          label="Suspend"
          icon="pi pi-pause"
          size="small"
          outlined
          loading={mutation.pending}
          onClick={() => run("suspend")}
        />
      ) : null}
      {canClose ? (
        <Button
          label="Close"
          icon="pi pi-lock"
          size="small"
          outlined
          severity="danger"
          loading={mutation.pending}
          onClick={() => run("close")}
        />
      ) : null}
    </div>
  );
}

function BalancesPanel({
  accountId,
  refreshToken,
}: {
  accountId: string;
  refreshToken: number;
}) {
  const query = useQuery(
    (signal) => getBalances(accountId, signal),
    [accountId, refreshToken]
  );
  return (
    <AsyncBoundary
      loading={query.loading}
      error={query.error}
      data={query.data}
      onRetry={query.refetch}
      isEmpty={(b: Balances) =>
        b.cashBalances.length === 0 && b.securityPositions.length === 0
      }
      emptyMessage="No balances yet."
    >
      {(b) => (
        <div className="balances-grid">
          <div>
            <h3>Cash</h3>
            {b.cashBalances.length === 0 ? (
              <p className="muted">No cash balances.</p>
            ) : (
              <ul className="balance-list">
                {b.cashBalances.map((c) => (
                  <li key={c.currency}>
                    <span>{c.currency}</span>
                    <strong>{formatMoney(c.balance, c.currency)}</strong>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <h3>Positions</h3>
            {b.securityPositions.length === 0 ? (
              <p className="muted">No positions.</p>
            ) : (
              <ul className="balance-list">
                {b.securityPositions.map((p) => (
                  <li key={p.instrumentId}>
                    <span>{p.instrumentId}</span>
                    <strong>{formatMoney(p.quantity)}</strong>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </AsyncBoundary>
  );
}

function CashLedger({
  accountId,
  refreshToken,
}: {
  accountId: string;
  refreshToken: number;
}) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const query = useQuery(
    (signal) => getCashTransactions(accountId, page, size, signal),
    [accountId, page, size, refreshToken]
  );
  return (
    <AsyncBoundary
      loading={query.loading}
      error={query.error}
      data={query.data}
      onRetry={query.refetch}
      isEmpty={(p) => p.content.length === 0}
      emptyMessage="No cash transactions yet."
    >
      {(p) => (
        <Table
          dataList={p.content}
          totalNum={p.totalElements}
          onPageChange={({ pageNumber, pageSize }) => {
            setPage(pageNumber);
            setSize(pageSize);
          }}
          aria-label="Cash transactions"
        >
          <Column
            header="Direction"
            body={(t: CashTransaction) => humanizeEnum(t.direction)}
          />
          <Column
            header="Type"
            body={(t: CashTransaction) => humanizeEnum(t.transactionType)}
          />
          <Column
            header="Amount"
            body={(t: CashTransaction) => formatMoney(t.amount, t.currency)}
          />
          <Column
            header="Posted"
            body={(t: CashTransaction) => formatDateTime(t.postedAt)}
          />
        </Table>
      )}
    </AsyncBoundary>
  );
}

function SecurityLedger({
  accountId,
  refreshToken,
}: {
  accountId: string;
  refreshToken: number;
}) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const query = useQuery(
    (signal) => getSecurityTransactions(accountId, page, size, signal),
    [accountId, page, size, refreshToken]
  );
  return (
    <AsyncBoundary
      loading={query.loading}
      error={query.error}
      data={query.data}
      onRetry={query.refetch}
      isEmpty={(p) => p.content.length === 0}
      emptyMessage="No security transactions yet."
    >
      {(p) => (
        <Table
          dataList={p.content}
          totalNum={p.totalElements}
          onPageChange={({ pageNumber, pageSize }) => {
            setPage(pageNumber);
            setSize(pageSize);
          }}
          aria-label="Security transactions"
        >
          <Column
            header="Direction"
            body={(t: SecurityTransaction) => humanizeEnum(t.direction)}
          />
          <Column field="instrumentId" header="Instrument" />
          <Column
            header="Quantity"
            body={(t: SecurityTransaction) => formatMoney(t.quantity)}
          />
          <Column
            header="Posted"
            body={(t: SecurityTransaction) => formatDateTime(t.postedAt)}
          />
        </Table>
      )}
    </AsyncBoundary>
  );
}

export default function AccountDetailPage() {
  const params = useParams<{ accountId: string }>();
  const accountId = decodeURIComponent(params.accountId);
  const [refreshToken, setRefreshToken] = useState(0);
  const [cashDialog, setCashDialog] = useState(false);
  const [securityDialog, setSecurityDialog] = useState(false);
  const { data: session } = useSession();
  const admin = isAdmin(session ?? null);
  const bump = () => setRefreshToken((n) => n + 1);

  const query = useQuery(
    (signal) => getAccount(accountId, signal),
    [accountId, refreshToken]
  );

  return (
    <div>
      <PageHeader
        title={`Account ${accountId}`}
        description="Account detail, balances, and ledgers."
        actions={
          <Link href="/accounts" className="p-button p-button-text">
            Back to accounts
          </Link>
        }
      />
      <PostCashDialog
        accountId={accountId}
        visible={cashDialog}
        onHide={() => setCashDialog(false)}
        onPosted={bump}
      />
      <PostSecurityDialog
        accountId={accountId}
        visible={securityDialog}
        onHide={() => setSecurityDialog(false)}
        onPosted={bump}
      />

      {query.loading && !query.data ? <LoadingState /> : null}
      {query.error && !query.data ? (
        <ErrorState error={query.error} onRetry={query.refetch} />
      ) : null}

      {query.data ? (
        <>
          <section className="panel" aria-labelledby="account-heading">
            <div className="panel-head">
              <h2 id="account-heading">{query.data.accountName}</h2>
              {admin ? (
                <StatusActions account={query.data} onChanged={bump} />
              ) : null}
            </div>
            <dl className="detail-grid">
              <DetailRow
                label="Status"
                value={<StatusBadge value={query.data.status} />}
              />
              <DetailRow label="Owner" value={query.data.ownerSubject} />
              <DetailRow
                label="Type"
                value={humanizeEnum(query.data.accountType)}
              />
              <DetailRow label="Currency" value={query.data.baseCurrency} />
              <DetailRow
                label="Created"
                value={formatDateTime(query.data.createdAt)}
              />
            </dl>
          </section>

          <section className="panel" aria-label="Balances and ledgers">
            <TabView>
              <TabPanel header="Balances">
                <BalancesPanel
                  accountId={accountId}
                  refreshToken={refreshToken}
                />
              </TabPanel>
              <TabPanel header="Cash">
                <div className="panel-actions ledger-actions">
                  <Button
                    label="Post cash transaction"
                    icon="pi pi-plus"
                    size="small"
                    onClick={() => setCashDialog(true)}
                  />
                </div>
                <CashLedger accountId={accountId} refreshToken={refreshToken} />
              </TabPanel>
              <TabPanel header="Securities">
                <div className="panel-actions ledger-actions">
                  <Button
                    label="Post security transaction"
                    icon="pi pi-plus"
                    size="small"
                    onClick={() => setSecurityDialog(true)}
                  />
                </div>
                <SecurityLedger
                  accountId={accountId}
                  refreshToken={refreshToken}
                />
              </TabPanel>
            </TabView>
          </section>
        </>
      ) : null}
    </div>
  );
}
