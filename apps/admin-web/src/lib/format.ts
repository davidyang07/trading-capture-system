import dayjs from "dayjs";

/**
 * Display formatting only. Exact decimal values are always kept as strings for transport
 * (ADR-0014); these helpers never feed a formatted value back into an API request.
 */

/** Format a decimal string for display without lossy float math. */
export function formatDecimal(
  value: string | null | undefined,
  minFractionDigits = 2
): string {
  if (value === null || value === undefined || value === "") {
    return "—";
  }
  const [intPart, fracPartRaw = ""] = value.split(".");
  const negative = intPart.startsWith("-");
  const digits = negative ? intPart.slice(1) : intPart;
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  let frac = fracPartRaw;
  if (frac.length < minFractionDigits) {
    frac = frac.padEnd(minFractionDigits, "0");
  }
  const body = frac.length > 0 ? `${grouped}.${frac}` : grouped;
  return negative ? `-${body}` : body;
}

export function formatMoney(
  value: string | null | undefined,
  currency?: string | null
): string {
  const amount = formatDecimal(value, 2);
  return currency ? `${amount} ${currency}` : amount;
}

/** A quantity (shares, units): grouped digits at the API's scale, never with a currency. */
export function formatQuantity(value: string | null | undefined): string {
  return formatDecimal(value, 0);
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss");
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  return dayjs(value).format("YYYY-MM-DD");
}

/** Human-friendly enum label, e.g. PARTIALLY_FILLED -> "Partially Filled". */
export function humanizeEnum(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  return value
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}
