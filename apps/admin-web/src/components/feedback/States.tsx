"use client";

import { useEffect, useRef, type ReactNode } from "react";
import Link from "next/link";
import { Button } from "primereact/button";
import type { ApiError } from "@/lib/api-error";

/**
 * Shared loading / empty / error states so every data view is consistent (§13).
 * Error states show a safe message plus the correlation id for support, never a stack.
 */

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="state-box" role="status" aria-live="polite">
      <i className="pi pi-spin pi-spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({
  message = "No records found.",
  icon = "pi pi-inbox",
  action,
}: {
  message?: string;
  icon?: string;
  action?: ReactNode;
}) {
  return (
    <div className="state-box" role="status">
      <i className={icon} aria-hidden="true" />
      <p>{message}</p>
      {action}
    </div>
  );
}

export function ErrorState({
  error,
  onRetry,
}: {
  error: ApiError;
  onRetry?: () => void;
}) {
  const alertRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Prefer the first server-rejected form field. If this is a page/query error,
    // focus the alert itself so keyboard and screen-reader users land on the failure.
    const firstField = error.fieldErrors[0]?.field;
    const matchingControl = firstField
      ? Array.from(document.querySelectorAll<HTMLElement>("[name]")).find(
          (element) => element.getAttribute("name") === firstField
        )
      : undefined;
    (matchingControl ?? alertRef.current)?.focus();
  }, [error]);

  return (
    <div
      ref={alertRef}
      className="state-box state-error"
      role="alert"
      tabIndex={-1}
    >
      <i className="pi pi-exclamation-triangle" aria-hidden="true" />
      <p>{error.userMessage}</p>
      {error.correlationId ? (
        <p className="state-ref">
          Reference: <code>{error.correlationId}</code>
        </p>
      ) : null}
      {error.fieldErrors.length > 0 ? (
        <ul className="state-field-errors" aria-label="Fields requiring attention">
          {error.fieldErrors.map((fieldError, index) => (
            <li key={`${fieldError.field}-${index}`}>
              <strong>{humanizeField(fieldError.field)}:</strong>{" "}
              {fieldError.message}
            </li>
          ))}
        </ul>
      ) : null}
      {error.kind === "unauthenticated" ? (
        <Link href="/login?error=SessionExpired" className="p-button p-button-sm">
          Sign in again
        </Link>
      ) : null}
      {onRetry &&
      error.kind !== "unauthenticated" &&
      error.kind !== "unauthorized" ? (
        <Button
          label="Retry"
          icon="pi pi-refresh"
          size="small"
          outlined
          onClick={onRetry}
        />
      ) : null}
    </div>
  );
}

function humanizeField(field: string): string {
  return field
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/[._-]+/g, " ")
    .replace(/^./, (character) => character.toUpperCase());
}

/**
 * Convenience wrapper: renders the right state for a query result and only calls
 * `children` once data is present.
 */
export function AsyncBoundary<T>({
  loading,
  error,
  data,
  isEmpty,
  onRetry,
  emptyMessage,
  emptyAction,
  loadingLabel,
  children,
}: {
  loading: boolean;
  error: ApiError | undefined;
  data: T | undefined;
  isEmpty?: (data: T) => boolean;
  onRetry?: () => void;
  emptyMessage?: string;
  emptyAction?: ReactNode;
  loadingLabel?: string;
  children: (data: T) => ReactNode;
}) {
  if (loading && data === undefined) {
    return <LoadingState label={loadingLabel} />;
  }
  if (error && data === undefined) {
    return <ErrorState error={error} onRetry={onRetry} />;
  }
  if (data === undefined) {
    return <LoadingState label={loadingLabel} />;
  }
  if (isEmpty && isEmpty(data)) {
    return <EmptyState message={emptyMessage} action={emptyAction} />;
  }
  return (
    <>
      {loading ? (
        <span className="sr-only" role="status" aria-live="polite">
          Refreshing data…
        </span>
      ) : null}
      {children(data)}
    </>
  );
}
