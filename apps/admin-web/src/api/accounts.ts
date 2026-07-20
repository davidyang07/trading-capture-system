import { apiFetch, type RequestOptions } from "@/lib/http";
import type {
  Account,
  AccountSearchParams,
  AccountStatus,
  Balances,
  CashTransaction,
  CreateAccountRequest,
  Page,
  PostCashTransactionRequest,
  PostSecurityTransactionRequest,
  SecurityTransaction,
} from "@/types/api";

const SERVICE = "account" as const;

export function searchAccounts(
  params: AccountSearchParams,
  signal?: AbortSignal
): Promise<Page<Account>> {
  return apiFetch<Page<Account>>(SERVICE, "api/v1/accounts", {
    query: params as RequestOptions["query"],
    signal,
  });
}

export function getAccount(
  accountId: string,
  signal?: AbortSignal
): Promise<Account> {
  return apiFetch<Account>(
    SERVICE,
    `api/v1/accounts/${encodeURIComponent(accountId)}`,
    { signal }
  );
}

export function createAccount(body: CreateAccountRequest): Promise<Account> {
  return apiFetch<Account>(SERVICE, "api/v1/accounts", {
    method: "POST",
    body,
  });
}

type LifecycleAction = "activate" | "suspend" | "close";

export function changeAccountStatus(
  accountId: string,
  action: LifecycleAction
): Promise<Account> {
  return apiFetch<Account>(
    SERVICE,
    `api/v1/accounts/${encodeURIComponent(accountId)}/${action}`,
    { method: "POST" }
  );
}

export function getBalances(
  accountId: string,
  signal?: AbortSignal
): Promise<Balances> {
  return apiFetch<Balances>(
    SERVICE,
    `api/v1/accounts/${encodeURIComponent(accountId)}/balances`,
    { signal }
  );
}

export function getCashTransactions(
  accountId: string,
  page: number,
  size: number,
  signal?: AbortSignal
): Promise<Page<CashTransaction>> {
  return apiFetch<Page<CashTransaction>>(
    SERVICE,
    `api/v1/accounts/${encodeURIComponent(accountId)}/cash-transactions`,
    { query: { page, size }, signal }
  );
}

export function postCashTransaction(
  accountId: string,
  body: PostCashTransactionRequest
): Promise<CashTransaction> {
  return apiFetch<CashTransaction>(
    SERVICE,
    `api/v1/accounts/${encodeURIComponent(accountId)}/cash-transactions`,
    { method: "POST", body }
  );
}

export function getSecurityTransactions(
  accountId: string,
  page: number,
  size: number,
  signal?: AbortSignal
): Promise<Page<SecurityTransaction>> {
  return apiFetch<Page<SecurityTransaction>>(
    SERVICE,
    `api/v1/accounts/${encodeURIComponent(accountId)}/security-transactions`,
    { query: { page, size }, signal }
  );
}

export function postSecurityTransaction(
  accountId: string,
  body: PostSecurityTransactionRequest
): Promise<SecurityTransaction> {
  return apiFetch<SecurityTransaction>(
    SERVICE,
    `api/v1/accounts/${encodeURIComponent(accountId)}/security-transactions`,
    { method: "POST", body }
  );
}

export type { AccountStatus };
