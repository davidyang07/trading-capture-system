import { useCallback, useRef } from "react";

function newKey(): string {
  // randomUUID needs a secure context; getRandomValues works everywhere.
  if (typeof crypto.randomUUID === "function") {
    return `web-${crypto.randomUUID()}`;
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return `web-${Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("")}`;
}

/**
 * One idempotency key per distinct ledger posting. Submitting the same posting again,
 * say after a timeout that left it unclear whether the first attempt landed, reuses the
 * key, so the account service records it at most once and answers the replay with the
 * original row. A changed posting is a new posting and gets a new key. Call `reset` once
 * a posting succeeded or the form was abandoned.
 */
export function useIdempotencyKey() {
  const last = useRef<{ fingerprint: string; key: string } | null>(null);

  const keyFor = useCallback((posting: unknown): string => {
    const fingerprint = JSON.stringify(posting);
    if (last.current?.fingerprint !== fingerprint) {
      last.current = { fingerprint, key: newKey() };
    }
    return last.current.key;
  }, []);

  const reset = useCallback(() => {
    last.current = null;
  }, []);

  return { keyFor, reset };
}
