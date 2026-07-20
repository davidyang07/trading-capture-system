import type { FieldError, Problem } from "@/types/api";

/**
 * Frontend error model. Every non-2xx response from the BFF is mapped to a typed
 * ApiError so callers can branch on `kind` and screens can render safe messages while
 * retaining a correlation id for support (§10, §13).
 */
export type ApiErrorKind =
  | "validation"
  | "unauthenticated"
  | "unauthorized"
  | "not-found"
  | "conflict"
  | "rate-limited"
  | "server"
  | "network"
  | "timeout"
  | "malformed";

function kindForStatus(status: number): ApiErrorKind {
  switch (status) {
    case 400:
      return "validation";
    case 401:
      return "unauthenticated";
    case 403:
      return "unauthorized";
    case 404:
      return "not-found";
    case 409:
      return "conflict";
    case 429:
      return "rate-limited";
    default:
      return status >= 500 ? "server" : "server";
  }
}

const SAFE_MESSAGES: Record<ApiErrorKind, string> = {
  validation: "Please correct the highlighted fields and try again.",
  unauthenticated: "Your session has expired. Please sign in again.",
  unauthorized: "You do not have permission to perform this action.",
  "not-found": "The requested item was not found.",
  conflict: "This action conflicts with the current state of the record.",
  "rate-limited": "Too many requests. Please wait a moment and retry.",
  server: "Something went wrong on our side. Please try again.",
  network: "Unable to reach the server. Check your connection and retry.",
  timeout: "The request took too long. Please retry.",
  malformed: "The server returned an unexpected response.",
};

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number;
  readonly errorCode?: string;
  readonly correlationId?: string;
  readonly fieldErrors: FieldError[];

  constructor(params: {
    kind: ApiErrorKind;
    status: number;
    message: string;
    errorCode?: string;
    correlationId?: string;
    fieldErrors?: FieldError[];
  }) {
    super(params.message);
    this.name = "ApiError";
    this.kind = params.kind;
    this.status = params.status;
    this.errorCode = params.errorCode;
    this.correlationId = params.correlationId;
    this.fieldErrors = params.fieldErrors ?? [];
  }

  /** A message safe to show a user (never a stack trace or internal detail). */
  get userMessage(): string {
    if (this.kind === "conflict" && this.errorCode) {
      const friendly = CONFLICT_MESSAGES[this.errorCode];
      if (friendly) {
        return friendly;
      }
    }
    return SAFE_MESSAGES[this.kind];
  }

  static network(): ApiError {
    return new ApiError({
      kind: "network",
      status: 0,
      message: SAFE_MESSAGES.network,
    });
  }

  static timeout(): ApiError {
    return new ApiError({
      kind: "timeout",
      status: 0,
      message: SAFE_MESSAGES.timeout,
    });
  }

  static malformed(
    statusOrCorrelationId: number | string = 0,
    correlationId?: string
  ): ApiError {
    return new ApiError({
      kind: "malformed",
      status:
        typeof statusOrCorrelationId === "number" ? statusOrCorrelationId : 0,
      message: SAFE_MESSAGES.malformed,
      correlationId:
        typeof statusOrCorrelationId === "string"
          ? statusOrCorrelationId
          : correlationId,
    });
  }

  static fromResponse(
    status: number,
    correlationId: string | null,
    body: unknown
  ): ApiError {
    const kind = kindForStatus(status);
    const problem = isProblem(body) ? body : {};
    return new ApiError({
      kind,
      status,
      message: problem.detail ?? SAFE_MESSAGES[kind],
      errorCode: problem.errorCode,
      correlationId: problem.correlationId ?? correlationId ?? undefined,
      fieldErrors: problem.fieldErrors,
    });
  }
}

function isProblem(value: unknown): value is Problem {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

// Selected backend errorCodes with clearer, user-safe phrasing.
const CONFLICT_MESSAGES: Record<string, string> = {
  DUPLICATE_ORDER_ID: "An order with this ID already exists.",
  ORDER_NOT_ELIGIBLE: "Only orders in NEW status can be changed.",
  ORDER_HAS_EXECUTIONS: "This order has executions and cannot be deleted.",
  ORDER_ALREADY_FILLED: "This order is already fully filled.",
  EXECUTION_OVERFILL: "This fill exceeds the order's remaining quantity.",
  EXECUTION_TARGET_SUPERSEDED:
    "This execution has already been amended by a later record.",
  EXECUTION_TARGET_CANCELLED: "This execution chain has been cancelled.",
  EXECUTION_ALLOCATED:
    "This execution is allocated and must be released before amendment.",
  DUPLICATE_EXEC_ID: "An execution with this ExecID already exists.",
  INVALID_STATUS_TRANSITION: "That status change is not allowed.",
  ACCOUNT_NOT_ACTIVE: "The account is not active.",
  ACCOUNT_NOT_EMPTY: "The account still has non-zero balances.",
  INSUFFICIENT_FUNDS: "Insufficient funds for this transaction.",
  INSUFFICIENT_SECURITIES: "Insufficient position for this delivery.",
  DUPLICATE_ACCOUNT_ID: "An account with this ID already exists.",
};

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError;
}
