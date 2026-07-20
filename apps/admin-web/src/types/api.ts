/**
 * Typed API contracts, aligned with contracts/openapi/*.yaml.
 *
 * Decimal money/quantity fields are kept as strings end-to-end — the backend serializes
 * DECIMAL(19,x) as JSON strings and we must never route exact values through JavaScript
 * floating-point (ADR-0014). Formatting for display happens separately (lib/format.ts).
 */

// ----- shared -----
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errorCode?: string;
  correlationId?: string;
  timestamp?: string;
  fieldErrors?: FieldError[];
}

// ----- trading: orders -----
export type OrderStatus = "NEW" | "PARTIALLY_FILLED" | "FILLED";
export type Side = "BUY" | "SELL";
export type OrderType = "MARKET" | "LIMIT";
export type PriceType = "PERCENTAGE" | "PER_UNIT" | "FIXED_AMOUNT";
export type SettleType =
  | "REGULAR"
  | "CASH"
  | "NEXT_DAY"
  | "T_PLUS_2"
  | "T_PLUS_3"
  | "T_PLUS_4";
export type ExecutionType = "TRADE" | "TRADE_CORRECT" | "TRADE_CANCEL";
export type AllocationState = "UNALLOCATED" | "ALLOCATED";
export type MiscFeeBasis = "ABSOLUTE" | "PER_UNIT" | "PERCENTAGE";

export interface Order {
  orderId: string;
  clOrdId: string | null;
  ownerSubject: string;
  accountId: string;
  instrumentId: string;
  status: OrderStatus;
  side: Side;
  orderType: OrderType;
  price: string;
  priceType: PriceType;
  currency: string;
  quantity: string;
  cumQty: string;
  leavesQty: string;
  settleType: SettleType;
  settleDate: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateOrderRequest {
  orderId?: string;
  clOrdId?: string;
  accountId: string;
  instrumentId: string;
  side: Side;
  orderType: OrderType;
  price: string;
  priceType: PriceType;
  currency: string;
  quantity: string;
  settleType: SettleType;
  settleDate: string;
}

export type UpdateOrderRequest = Omit<CreateOrderRequest, "orderId">;

export interface OrderDetail {
  order: Order;
  executions: Execution[];
}

export interface OrderSearchParams {
  status?: OrderStatus;
  side?: Side;
  instrumentId?: string;
  accountId?: string;
  ownerSubject?: string;
  settleDateFrom?: string;
  settleDateTo?: string;
  page?: number;
  size?: number;
  sortBy?: "ORDER_ID" | "STATUS" | "PRICE" | "SETTLE_DATE" | "CREATED_AT";
  sortDir?: "ASC" | "DESC";
}

// ----- trading: executions -----
export interface MiscFee {
  feeType: string;
  amount: string;
  currency: string;
  basis: MiscFeeBasis;
}

export interface Execution {
  internalId: string;
  execId: string;
  orderId: string;
  execType: ExecutionType;
  execRefId: string | null;
  rootExecutionId: string;
  ordStatus: OrderStatus;
  side: Side;
  lastQty: string;
  lastPx: string | null;
  cumQty: string;
  leavesQty: string;
  avgPx: string | null;
  currency: string;
  tradeDate: string;
  transactTime: string;
  settleType: SettleType;
  settleDate: string;
  text: string | null;
  allocationState: AllocationState;
  allocationId: string | null;
  createdAt: string;
  version: number;
}

export interface ChainLink {
  internalId: string;
  execId: string;
  execType: ExecutionType;
  lastQty: string;
  lastPx: string | null;
  transactTime: string;
  tip: boolean;
}

export interface ExecutionDetail {
  execution: Execution;
  miscFees: MiscFee[];
  chainRootExecutionId: string;
  chainActive: boolean;
  chain: ChainLink[];
}

export interface CreateExecutionRequest {
  orderId: string;
  execId?: string;
  lastQty: string;
  lastPx: string;
  tradeDate?: string;
  transactTime?: string;
  text?: string;
}

export interface CorrectExecutionRequest {
  execId?: string;
  lastQty: string;
  lastPx: string;
  text?: string;
}

export interface CancelExecutionRequest {
  execId?: string;
  text?: string;
}

export interface ExecutionSearchParams {
  orderId?: string;
  execId?: string;
  execType?: ExecutionType;
  side?: Side;
  allocationState?: AllocationState;
  tradeDateFrom?: string;
  tradeDateTo?: string;
  page?: number;
  size?: number;
  sortBy?: "EXEC_ID" | "TRANSACT_TIME" | "TRADE_DATE" | "CREATED_AT";
  sortDir?: "ASC" | "DESC";
}

// ----- account -----
export type AccountType = "CASH" | "MARGIN";
export type AccountStatus = "PENDING" | "ACTIVE" | "SUSPENDED" | "CLOSED";
export type CashDirection = "CREDIT" | "DEBIT";
export type SecurityDirection = "RECEIPT" | "DELIVERY";
export type CashTransactionType =
  | "DEPOSIT"
  | "WITHDRAWAL"
  | "TRADE_SETTLEMENT"
  | "FEE"
  | "ADJUSTMENT";
export type SecurityTransactionType =
  | "TRADE_SETTLEMENT"
  | "TRANSFER_IN"
  | "TRANSFER_OUT"
  | "ADJUSTMENT";

export interface Account {
  accountId: string;
  ownerSubject: string;
  accountName: string;
  accountType: AccountType;
  status: AccountStatus;
  baseCurrency: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateAccountRequest {
  accountId?: string;
  ownerSubject?: string;
  accountName: string;
  accountType: AccountType;
  baseCurrency: string;
}

export interface AccountSearchParams {
  status?: AccountStatus;
  accountType?: AccountType;
  currency?: string;
  ownerSubject?: string;
  page?: number;
  size?: number;
  sortBy?: "ACCOUNT_ID" | "ACCOUNT_NAME" | "STATUS" | "CREATED_AT";
  sortDir?: "ASC" | "DESC";
}

export interface CashTransaction {
  id: number;
  transactionRef: string;
  accountId: string;
  direction: CashDirection;
  transactionType: CashTransactionType;
  amount: string;
  currency: string;
  externalRef: string | null;
  idempotencyKey: string | null;
  description: string | null;
  postedAt: string;
}

export interface SecurityTransaction {
  id: number;
  transactionRef: string;
  accountId: string;
  direction: SecurityDirection;
  transactionType: SecurityTransactionType;
  instrumentId: string;
  quantity: string;
  externalRef: string | null;
  idempotencyKey: string | null;
  description: string | null;
  postedAt: string;
}

export interface PostCashTransactionRequest {
  direction: CashDirection;
  transactionType: CashTransactionType;
  amount: string;
  currency: string;
  externalRef?: string;
  idempotencyKey?: string;
  description?: string;
}

export interface PostSecurityTransactionRequest {
  direction: SecurityDirection;
  transactionType: SecurityTransactionType;
  instrumentId: string;
  quantity: string;
  externalRef?: string;
  idempotencyKey?: string;
  description?: string;
}

export interface Balances {
  accountId: string;
  cashBalances: { currency: string; balance: string }[];
  securityPositions: { instrumentId: string; quantity: string }[];
}

// ----- enrichment (reference data used for selectors) -----
export type InstrumentStatus = "ACTIVE" | "INACTIVE";
export type InstrumentType = "EQUITY" | "BOND" | "ETF" | "FUND";

export interface InstrumentSummary {
  instrumentId: string;
  name: string;
  instrumentType: InstrumentType;
  exchangeMic: string;
  currencyCode: string;
  status: InstrumentStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface InstrumentSearchParams {
  instrumentType?: InstrumentType;
  exchangeMic?: string;
  currency?: string;
  status?: InstrumentStatus;
  page?: number;
  size?: number;
  sortBy?: "INSTRUMENT_ID" | "NAME" | "CREATED_AT";
  sortDir?: "ASC" | "DESC";
}

export interface Currency {
  code: string;
  name: string;
  minorUnits: number;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
}

export interface Exchange {
  mic: string;
  name: string;
  country: string | null;
  timezone: string | null;
}
