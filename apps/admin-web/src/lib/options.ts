import type { Option } from "@/components/Form/fields";
import { humanizeEnum } from "@/lib/format";

/** Build select options from an enum's string values (label humanized). */
function toOptions(values: readonly string[]): Option[] {
  return values.map((value) => ({ label: humanizeEnum(value), value }));
}

export const SIDE_OPTIONS = toOptions(["BUY", "SELL"]);
export const ORDER_TYPE_OPTIONS = toOptions(["MARKET", "LIMIT"]);
export const PRICE_TYPE_OPTIONS = toOptions([
  "PERCENTAGE",
  "PER_UNIT",
  "FIXED_AMOUNT",
]);
export const SETTLE_TYPE_OPTIONS = toOptions([
  "REGULAR",
  "CASH",
  "NEXT_DAY",
  "T_PLUS_2",
  "T_PLUS_3",
  "T_PLUS_4",
]);
export const ORDER_STATUS_OPTIONS = toOptions([
  "NEW",
  "PARTIALLY_FILLED",
  "FILLED",
]);
export const EXECUTION_TYPE_OPTIONS = toOptions([
  "TRADE",
  "TRADE_CORRECT",
  "TRADE_CANCEL",
]);
export const ACCOUNT_TYPE_OPTIONS = toOptions(["CASH", "MARGIN"]);
export const ACCOUNT_STATUS_OPTIONS = toOptions([
  "PENDING",
  "ACTIVE",
  "SUSPENDED",
  "CLOSED",
]);
export const CASH_DIRECTION_OPTIONS = toOptions(["CREDIT", "DEBIT"]);
export const CASH_TX_TYPE_OPTIONS = toOptions([
  "DEPOSIT",
  "WITHDRAWAL",
  "TRADE_SETTLEMENT",
  "FEE",
  "ADJUSTMENT",
]);
export const SECURITY_DIRECTION_OPTIONS = toOptions(["RECEIPT", "DELIVERY"]);
export const SECURITY_TX_TYPE_OPTIONS = toOptions([
  "TRADE_SETTLEMENT",
  "TRANSFER_IN",
  "TRANSFER_OUT",
  "ADJUSTMENT",
]);
