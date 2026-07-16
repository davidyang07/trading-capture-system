// [NOTE] label:fieldName

/** detail - basic info */
export const BASIC_INFO_FIELD_MAP: { [key: string]: string } = {
  "Exec ID": "execId",
  "Currency": "currency",
  "Exec Type": "execType",
  "Transact Time": "transactTime",
  "Client Order ID": "clOrderId",
  "Order ID": "orderId",
  "Side": "side",
  "Leaves Qty": "leavesQty",
  "Cumulative Qty": "cumQty",
  "Settle Type": "settlType",
  "Settle Date": "settlDate",
  "Avg Price": "avgPrice",
  "Internal ID": "internalId",
  "Exec Ref ID": "execRefId",
  "Order Status": "ordStatus",
  "Order Qty": "orderQty",
  "Last Qty": "lastQty",
  "Last Price": "lastPrice",
  "Text": "text",
  "Trade Date": "tradeDate",
  "Execution Status": "status",
};

/** detail - instrument info */
export const INSTRUMENT_FIELD_MAP: { [key: string]: string } = {
  "Product": "product",
  "CFI Code": "cfiCode",
  "Security ID": "securityId",
  "Security ID Source": "securityIdSource",
  "Security Exchange": "securityExchange",
  "Security Alts": "securityAltList",
};

/** detail - instrument security alts */
export const SEC_ALT_FIELD_MAP: { [key: string]: string } = {
  "Security Alt ID": "altId",
  "Security Alt ID Source": "idSource",
};

/** detail - misc fees */
export const MISC_FIELD_MAP: { [key: string]: string } = {
  "Misc Fee Amt": "amt",
  "Misc Fee Type": "type",
  "Misc Fee Currency": "currency",
  "Misc Fee Basis": "basis",
};
