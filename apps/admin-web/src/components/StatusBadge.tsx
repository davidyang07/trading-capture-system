import { humanizeEnum } from "@/lib/format";

/**
 * Status pill. State is conveyed by an explicit text label AND a leading glyph, not by
 * color alone (WCAG 1.4.1 — §14). Color is a secondary reinforcement.
 */
type Tone = "neutral" | "info" | "success" | "warning" | "danger";

const TONE_ICON: Record<Tone, string> = {
  neutral: "pi pi-circle",
  info: "pi pi-info-circle",
  success: "pi pi-check-circle",
  warning: "pi pi-exclamation-circle",
  danger: "pi pi-times-circle",
};

const STATUS_TONE: Record<string, Tone> = {
  // orders / executions
  NEW: "info",
  PARTIALLY_FILLED: "warning",
  FILLED: "success",
  TRADE: "info",
  TRADE_CORRECT: "warning",
  TRADE_CANCEL: "danger",
  UNALLOCATED: "neutral",
  ALLOCATED: "success",
  // accounts
  PENDING: "warning",
  ACTIVE: "success",
  SUSPENDED: "danger",
  CLOSED: "neutral",
  INACTIVE: "neutral",
};

export function StatusBadge({ value }: { value: string }) {
  const tone = STATUS_TONE[value] ?? "neutral";
  return (
    <span className={`status-badge tone-${tone}`}>
      <i className={TONE_ICON[tone]} aria-hidden="true" />
      {humanizeEnum(value)}
    </span>
  );
}
