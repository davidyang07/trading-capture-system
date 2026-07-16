export const DEFAULT_PAGE_OPTIONS = [10, 20, 50];

export const ORDER_TYPE_OPTIONS = [
  {
    name: "Market",
    code: "Market",
  },
  {
    name: "Limit",
    code: "Limit",
  },
];

export const SIDE_OPTIONS = [
  {
    name: "Buy",
    code: "Buy",
  },
  {
    name: "Sell",
    code: "Sell",
  },
];

export const PRICE_TYPE_OPTIONS = [
  {
    name: "Percentage",
    code: "Percentage",
  },
  {
    name: "Per Unit",
    code: "Per Unit",
  },
  {
    name: "Fixed Amount",
    code: "Fixed Amount",
  },
];

export const SETTLE_TYPE_OPTIONS = [
  {
    name: "Regular",
    code: "Regular",
  },
  {
    name: "Cash",
    code: "Cash",
  },
  {
    name: "Next Day (T+)",
    code: "Next Day (T+)",
  },
  {
    name: "T+2",
    code: "T+2",
  },
  {
    name: "T+3",
    code: "T+3",
  },
  {
    name: "T+4",
    code: "T+4",
  },
];

// exec: create
export const ORDER_STATUS_OPTIONS = [
  {
    name: "New",
    code: "New",
  },
  {
    name: "Partially Filled",
    code: "Partially Filled",
  },
  {
    name: "Filled",
    code: "Filled",
  },
];

// exec: cancellation & Correciton
export const ORDER_STATUS_CANCEL_CORR_OPTIONS = [
  {
    name: "Partially Filled",
    code: "Partially Filled",
  },
  {
    name: "Filled",
    code: "Filled",
  },
];

export const ALLOC_TYPE_OPTIONS = [
  {
    name: "Calculated",
    code: "Calculated",
  },
  {
    name: "Preliminary",
    code: "Preliminary",
  },
];

export const ALLOC_REASON_OPTIONS = [
  {
    name: "Original details incomplete/incorrect",
    code: "Original details incomplete/incorrect",
  },
  {
    name: "Change in underlying order details",
    code: "Change in underlying order details",
  },
  {
    name: "Other",
    code: "Other",
  },
];

export const EXEC_STATUS_OPTIONS = [
  {
    name: "Valid",
    code: "Valid",
  },
  {
    name: "Invalid",
    code: "Invalid",
  },
  {
    name: "Allocated",
    code: "Allocated",
  },
  {
    name: "Corrected",
    code: "Corrected",
  },
  {
    name: "Canceled",
    code: "Canceled",
  },
];

export const ACCOUNT_STATUS_OPTIONS = [
  {
    name: "Active",
    code: "Active",
  },
  {
    name: "Suspended",
    code: "Suspended",
  },
];

export const CASH_TRANSACTION_TYPE_OPTIONS = [
  {
    name: "debit",
    code: "debit",
  },
  {
    name: "credit",
    code: "credit",
  },
];
